package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.inventory.OutputChestFinder;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.trade.GlobalTradeCatalogue;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import com.fluffybacon.merchantvillager.trade.TradeProvider;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;

public final class ModPayloads {
    private static final UUID GLOBAL_ROW_UUID = new UUID(0L, 0L);
    private static final int MAX_ENCODED_ROWS_BYTES_PER_CHUNK = 96 * 1024;
    private static final int MAX_SAFE_PLAY_PAYLOAD_BYTES = 128 * 1024;
    private static final Map<ServerPlayerEntity, Long> LAST_TOGGLE_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Long> LAST_REFRESH_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Long> LAST_DISABLE_ALL_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, ViewerCatalogueState> VIEWER_CATALOGUES =
        new WeakHashMap<>();

    public static void initializeServer() {
        PayloadTypeRegistry.playS2C().register(CataloguePayload.ID, CataloguePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
            CatalogueDeltaPayload.ID, CatalogueDeltaPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(ToggleOfferPayload.ID, ToggleOfferPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RefreshCataloguePayload.ID, RefreshCataloguePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
            DisableAllOffersPayload.ID, DisableAllOffersPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            DepositTradeMaterialPayload.ID, DepositTradeMaterialPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(ToggleOfferPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (payload.fingerprint().length() < 64
                || payload.fingerprint().length() > 80
                || !allowAtInterval(player, LAST_TOGGLE_TICK, 4)
                || player.squaredDistanceTo(payload.postPos().toCenterPos()) > 64.0
                || !(player.getEntityWorld() instanceof ServerWorld world)
                || !(world.getBlockEntity(payload.postPos()) instanceof MerchantPostBlockEntity post)) {
                return;
            }
            post.setOfferEnabled(player, payload.fingerprint(), payload.enabled());
        });

        ServerPlayNetworking.registerGlobalReceiver(RefreshCataloguePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player.squaredDistanceTo(payload.postPos().toCenterPos()) <= 64.0
                && allowAtInterval(player, LAST_DISABLE_ALL_TICK, 20)
                && player.getEntityWorld() instanceof ServerWorld world
                && world.getBlockEntity(payload.postPos()) instanceof MerchantPostBlockEntity post
                && player.currentScreenHandler instanceof com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler handler
                && handler.getPostPos().equals(payload.postPos())
                && allowAtInterval(player, LAST_REFRESH_TICK, 20)) {
                post.refreshCatalogue(true);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(DisableAllOffersPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player.squaredDistanceTo(payload.postPos().toCenterPos()) <= 64.0
                && player.getEntityWorld() instanceof ServerWorld world
                && world.getBlockEntity(payload.postPos()) instanceof MerchantPostBlockEntity post) {
                post.disableAllOffers(player);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(DepositTradeMaterialPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (payload.fingerprint().length() >= 64
                && payload.fingerprint().length() <= 80
                && payload.inputIndex() >= 0
                && payload.inputIndex() <= 1
                && payload.mode() >= DepositTradeMaterialPayload.CURSOR_ALL
                && payload.mode() <= DepositTradeMaterialPayload.PLAYER_ALL
                && player.squaredDistanceTo(payload.postPos().toCenterPos()) <= 64.0
                && player.getEntityWorld() instanceof ServerWorld world
                && world.getBlockEntity(payload.postPos()) instanceof MerchantPostBlockEntity post) {
                post.depositForOffer(
                    player,
                    payload.fingerprint(),
                    payload.inputIndex(),
                    payload.mode()
                );
            }
        });
    }

    public static void sendCatalogue(ServerPlayerEntity player, MerchantPostBlockEntity post) {
        CataloguePayload snapshot = buildSnapshot(player, post, true);
        sendPreparedCatalogue(player, prepareCatalogue(player, snapshot));
    }

    public static void sendTelemetry(ServerPlayerEntity player, MerchantPostBlockEntity post) {
        sendBoundedTelemetry(player, buildSnapshot(player, post, false));
    }

    /** Sends only changed rows unless the viewer's static key set has changed. */
    public static void sendCatalogueDelta(
        ServerPlayerEntity player, MerchantPostBlockEntity post
    ) {
        CataloguePayload current = buildSnapshot(player, post, true);
        ViewerCatalogueState previous = VIEWER_CATALOGUES.get(player);
        Map<String, CataloguePayload.Entry> currentRows = entryRows(current.entries());
        if (previous != null && previous.postPos().equals(current.postPos())) {
            previous.omittedKeys().forEach(currentRows::remove);
        }
        if (previous == null
            || !previous.postPos().equals(current.postPos())
            || current.revision() < previous.revision()) {
            sendPreparedCatalogue(player, prepareCatalogue(player, current));
            return;
        }
        CatalogueDeltaPlanner.Plan plan = CatalogueDeltaPlanner.plan(
            previous.rows(), currentRows
        );
        if (plan.fullCatalogueRequired()) {
            sendPreparedCatalogue(player, prepareCatalogue(player, current));
            return;
        }
        if (!sendCatalogueDeltas(
            player,
            current.postPos(),
            previous.revision(),
            current.revision(),
            plan.deltas()
        )) {
            sendPreparedCatalogue(player, prepareCatalogue(player, current));
            return;
        }
        VIEWER_CATALOGUES.put(player, new ViewerCatalogueState(
            current.postPos().toImmutable(), current.revision(), Map.copyOf(currentRows),
            previous.omittedKeys()
        ));
        sendBoundedTelemetry(player, current);
    }

    private static CataloguePayload buildSnapshot(
        ServerPlayerEntity player, MerchantPostBlockEntity post, boolean includeCatalogue
    ) {
        List<ItemStack> inventory = post.copyInventory();
        MerchantWorkerState workerState = null;
        VillagerEntity workerEntity = null;
        if (post.getAssignedMerchant().isPresent() && player.getEntityWorld() instanceof ServerWorld world) {
            Entity entity = world.getEntity(post.getAssignedMerchant().get());
            if (entity instanceof VillagerEntity villager && entity instanceof MerchantWorker worker) {
                workerEntity = villager;
                workerState = worker.merchantVillager$getState();
            }
        }
        String activeOfferFingerprint = workerState == null ? "" : workerState.offerFingerprint();

        List<CataloguePayload.Entry> entries = includeCatalogue
            ? new ArrayList<>(post.getCatalogueEntries().size())
            : List.of();
        if (includeCatalogue) for (GlobalTradeCatalogue.Entry catalogueEntry : post.getCatalogueEntries()) {
            String tradeKey = catalogueEntry.key().serialized();
            List<OfferSnapshot> matchingLive = post.getOffers().stream()
                .filter(offer -> tradeKey.equals(post.tradeKeyFor(offer)))
                .sorted(Comparator
                    .comparing(OfferSnapshot::targetAvailable).reversed()
                    .thenComparing(OfferSnapshot::isOutOfStock)
                    .thenComparing(Comparator.comparingInt(
                        (OfferSnapshot offer) -> liveFundable(player, inventory, offer)
                    ).reversed())
                    .thenComparingDouble(OfferSnapshot::distanceSquared)
                    .thenComparing(OfferSnapshot::targetUuid))
                .toList();
            OfferSnapshot representative = matchingLive.isEmpty() ? null : matchingLive.getFirst();
            TradeOffer liveOffer = representative == null
                ? null
                : resolveLiveOffer(player, representative);
            var template = catalogueEntry.preview();
            int firstPrice = liveOffer == null
                ? template.firstInput().count()
                : liveOffer.getDisplayedFirstBuyItem().getCount();
            int secondPrice = liveOffer == null || liveOffer.getDisplayedSecondBuyItem().isEmpty()
                ? template.secondInput().map(input -> input.count()).orElse(0)
                : liveOffer.getDisplayedSecondBuyItem().getCount();
            int firstCount = TradeInputMatcher.matchingCount(
                inventory,
                template.firstInput()
            );
            int fundable = firstCount / Math.max(1, firstPrice);
            if (template.secondInput().isPresent()) {
                int secondCount = TradeInputMatcher.matchingCount(
                    inventory,
                    template.secondInput().get()
                );
                fundable = Math.min(fundable, secondCount / Math.max(1, secondPrice));
            }
            int remainingUses = representative == null
                ? template.maxUses()
                : representative.remainingUses();
            fundable = Math.min(fundable, remainingUses);
            if (!matchingLive.isEmpty()) {
                fundable = matchingLive.stream()
                    .mapToInt(offer -> liveFundable(player, inventory, offer))
                    .max()
                    .orElse(0);
            }
            boolean available = matchingLive.stream().anyMatch(OfferSnapshot::targetAvailable);
            boolean wandering = catalogueEntry.providers().stream()
                .anyMatch(provider -> provider.kind() == TradeProvider.Kind.WANDERING_TRADER);
            String profession = representative == null
                ? representativeProfession(catalogueEntry)
                : representative.profession();
            int level = representative == null
                ? representativeLevel(catalogueEntry)
                : representative.villagerLevel();
            var displayFirst = liveOffer == null
                ? template.firstInput()
                : liveOffer.getFirstBuyItem();
            var displaySecond = liveOffer == null
                ? template.secondInput()
                : liveOffer.getSecondBuyItem();
            ItemStack displayOutput = liveOffer == null
                ? template.output()
                : liveOffer.copySellItem();
            OfferSnapshot display = new OfferSnapshot(
                representative == null ? GLOBAL_ROW_UUID : representative.targetUuid(),
                providerLabel(catalogueEntry),
                profession,
                level,
                representative == null ? -1 : representative.offerIndex(),
                displayFirst,
                displaySecond,
                displayOutput,
                representative == null ? 0 : representative.uses(),
                representative == null ? template.maxUses() : representative.maxUses(),
                representative == null ? Double.POSITIVE_INFINITY : representative.distanceSquared(),
                wandering,
                available,
                representative == null ? -1 : representative.despawnDelay(),
                tradeKey
            );
            entries.add(new CataloguePayload.Entry(
                display,
                post.isEnabled(tradeKey),
                !matchingLive.isEmpty()
                    && matchingLive.stream().allMatch(offer -> post.isCoolingDown(offer.fingerprint())),
                Math.max(0, fundable),
                matchingLive.stream().anyMatch(offer ->
                    offer.fingerprint().equals(activeOfferFingerprint)
                ),
                firstCount,
                template.secondInput().isPresent()
                    ? TradeInputMatcher.matchingCount(inventory, template.secondInput().get())
                    : 0,
                firstPrice,
                secondPrice
            ));
        }

        int targetCount = (int)post.getOffers().stream()
            .map(OfferSnapshot::targetUuid)
            .distinct()
            .count();
        int enabledCount = includeCatalogue
            ? (int)entries.stream().filter(CataloguePayload.Entry::enabled).count()
            : post.countEnabledOffers();
        int executableCount = includeCatalogue
            ? (int)entries.stream()
                .filter(CataloguePayload.Entry::enabled)
                .filter(entry -> !entry.coolingDown())
                .filter(entry -> entry.fundableExecutions() > 0)
                .filter(entry -> !entry.offer().isOutOfStock() && entry.offer().targetAvailable())
                .count()
            : liveExecutableCount(player, post, inventory);
        Optional<CataloguePayload.WorkerStats> stats = Optional.empty();
        if (workerState != null && workerEntity != null && player.getEntityWorld() instanceof ServerWorld world) {
            WorkerTelemetryBounds.CargoView cargo = WorkerTelemetryBounds.summarizeCargo(
                player.getRegistryManager(), workerState.copyCargo()
            );
            int rewardSlotMask = 0;
            for (int slot = 0; slot < cargo.cargo().size(); slot++) {
                if (workerState.isRewardSlot(slot)) {
                    rewardSlotMask |= 1 << slot;
                }
            }
            Optional<net.minecraft.util.math.BlockPos> chest = post.getExportChestPos();
            String chestStatus = outputChestStatus(world, post.getPos(), workerState);
            stats = Optional.of(new CataloguePayload.WorkerStats(
                workerEntity.getDisplayName().getString(),
                workerEntity.getHealth(),
                workerEntity.getMaxHealth(),
                workerState.storedExperience(),
                workerEntity.squaredDistanceTo(post.getPos().toCenterPos()),
                cargo.cargo(),
                cargo.summarized(),
                rewardSlotMask,
                Optional.ofNullable(workerState.targetUuid()),
                workerState.plannedExecutions(),
                workerState.completedExecutions(),
                post.getImportChestPos(),
                chest,
                chestStatus
            ));
        }

        CataloguePayload payload = new CataloguePayload(
            post.getPos(),
            post.getCatalogueRevision(),
            post.getAssignedMerchant(),
            workerState == null ? "UNASSIGNED" : workerState.state().name(),
            workerState == null ? "No Merchant assigned" : workerState.status(),
            workerState == null ? post.getLastFailure() : workerState.lastFailure(),
            targetCount,
            enabledCount,
            executableCount,
            stats,
            includeCatalogue,
            0,
            includeCatalogue ? 1 : 0,
            includeCatalogue ? List.copyOf(entries) : List.of()
        );
        return payload;
    }

    private static String providerLabel(GlobalTradeCatalogue.Entry entry) {
        List<String> labels = entry.providers().stream()
            .map(ModPayloads::providerLabel)
            .distinct()
            .sorted()
            .toList();
        String joined = String.join(" · ", labels);
        if (entry.key().kind() == com.fluffybacon.merchantvillager.trade.TradeCatalogueKey.Kind.CONTEXTUAL) {
            joined = "Destination varies · " + joined;
        } else if (entry.variable()) {
            joined = "Exact sampled variant · " + joined;
        }
        return joined.length() <= 1024 ? joined : joined.substring(0, 1023) + "…";
    }

    private static String providerLabel(TradeProvider provider) {
        if (provider.kind() == TradeProvider.Kind.WANDERING_TRADER) {
            return "Wandering Trader";
        }
        String id = provider.professionId();
        int separator = id.indexOf(':');
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        String name = java.util.Arrays.stream(path.split("_"))
            .filter(word -> !word.isEmpty())
            .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
            .collect(java.util.stream.Collectors.joining(" "));
        return name + " L" + provider.level();
    }

    private static String representativeProfession(GlobalTradeCatalogue.Entry entry) {
        return entry.providers().stream()
            .filter(provider -> provider.kind() == TradeProvider.Kind.VILLAGER)
            .map(TradeProvider::professionId)
            .sorted()
            .findFirst()
            .orElse("minecraft:wandering_trader");
    }

    private static int representativeLevel(GlobalTradeCatalogue.Entry entry) {
        return entry.providers().stream()
            .filter(provider -> provider.kind() == TradeProvider.Kind.VILLAGER)
            .mapToInt(TradeProvider::level)
            .min()
            .orElse(0);
    }

    private static String outputChestStatus(
        ServerWorld world, BlockPos postPos, MerchantWorkerState state
    ) {
        if (!(world.getBlockEntity(postPos) instanceof MerchantPostBlockEntity post)) {
            return "Unavailable";
        }
        Inventory inventory = post.getExportInventory(world);
        if (inventory == null) {
            return post.getChestRoleStatus();
        }
        List<ItemStack> rewards = new ArrayList<>();
        for (int slot = 0; slot < state.cargo().size(); slot++) {
            if (state.isRewardSlot(slot) && !state.cargo().get(slot).isEmpty()) {
                rewards.add(state.cargo().get(slot));
            }
        }
        String capacity = rewards.isEmpty() || OutputChestFinder.hasSpace(inventory, rewards)
            ? "ready"
            : "full";
        return post.getChestRoleStatus() + "; Export " + capacity;
    }

    private static TradeOffer resolveLiveOffer(ServerPlayerEntity player, OfferSnapshot snapshot) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }
        Entity entity = world.getEntity(snapshot.targetUuid());
        if (!(entity instanceof MerchantEntity merchant)
            || snapshot.offerIndex() < 0
            || snapshot.offerIndex() >= merchant.getOffers().size()) {
            return null;
        }
        return merchant.getOffers().get(snapshot.offerIndex());
    }

    private static int liveExecutableCount(
        ServerPlayerEntity player, MerchantPostBlockEntity post, List<ItemStack> inventory
    ) {
        Set<String> executable = new HashSet<>();
        for (OfferSnapshot snapshot : post.getOffers()) {
            if (!post.isEnabled(snapshot.fingerprint())
                || post.isCoolingDown(snapshot.fingerprint())
                || snapshot.isOutOfStock()
                || !snapshot.targetAvailable()) {
                continue;
            }
            TradeOffer live = resolveLiveOffer(player, snapshot);
            if (live == null || live.isDisabled()) {
                continue;
            }
            int first = TradeInputMatcher.matchingCount(inventory, live.getFirstBuyItem())
                / Math.max(1, live.getDisplayedFirstBuyItem().getCount());
            if (live.getSecondBuyItem().isPresent()) {
                int second = TradeInputMatcher.matchingCount(inventory, live.getSecondBuyItem().get())
                    / Math.max(1, live.getDisplayedSecondBuyItem().getCount());
                first = Math.min(first, second);
            }
            if (first > 0) {
                executable.add(post.tradeKeyFor(snapshot));
            }
        }
        return executable.size();
    }

    private static int liveFundable(
        ServerPlayerEntity player, List<ItemStack> inventory, OfferSnapshot snapshot
    ) {
        TradeOffer live = resolveLiveOffer(player, snapshot);
        if (live == null || live.isDisabled()) {
            return 0;
        }
        int result = TradeInputMatcher.matchingCount(inventory, live.getFirstBuyItem())
            / Math.max(1, live.getDisplayedFirstBuyItem().getCount());
        if (live.getSecondBuyItem().isPresent()) {
            int second = TradeInputMatcher.matchingCount(inventory, live.getSecondBuyItem().get())
                / Math.max(1, live.getDisplayedSecondBuyItem().getCount());
            result = Math.min(result, second);
        }
        return Math.min(result, Math.max(0, live.getMaxUses() - live.getUses()));
    }

    private static PreparedCatalogue prepareCatalogue(
        ServerPlayerEntity player, CataloguePayload base
    ) {
        List<List<CataloguePayload.Entry>> chunks = new ArrayList<>();
        List<CataloguePayload.Entry> current = new ArrayList<>();
        int currentBytes = 0;
        for (CataloguePayload.Entry entry : base.entries()) {
            int entryBytes = encodedEntrySize(player, entry);
            if (entryBytes > MAX_ENCODED_ROWS_BYTES_PER_CHUNK) {
                MerchantVillagerMod.LOGGER.warn(
                    "Omitting oversized Merchant catalogue row {} ({} encoded bytes)",
                    entry.offer().fingerprint(), entryBytes
                );
                continue;
            }
            if (!current.isEmpty() && (current.size() >= CataloguePayload.MAX_ENTRIES_PER_CHUNK
                || currentBytes + entryBytes > MAX_ENCODED_ROWS_BYTES_PER_CHUNK)) {
                chunks.add(List.copyOf(current));
                current.clear();
                currentBytes = 0;
            }
            if (chunks.size() >= CataloguePayload.MAX_CHUNKS) {
                break;
            }
            current.add(entry);
            currentBytes += entryBytes;
        }
        if (!current.isEmpty() && chunks.size() < CataloguePayload.MAX_CHUNKS) {
            chunks.add(List.copyOf(current));
        }
        if (chunks.isEmpty()) {
            chunks.add(List.of());
        }
        int delivered = chunks.stream().mapToInt(List::size).sum();
        if (delivered < base.entries().size()) {
            MerchantVillagerMod.LOGGER.warn(
                "Merchant catalogue transport delivered {} of {} bounded rows",
                delivered, base.entries().size()
            );
        }
        List<CataloguePayload.Entry> deliveredEntries = chunks.stream()
            .flatMap(List::stream)
            .toList();
        Set<String> deliveredKeys = deliveredEntries.stream()
            .map(entry -> entry.offer().fingerprint())
            .collect(java.util.stream.Collectors.toSet());
        Set<String> omittedKeys = base.entries().stream()
            .map(entry -> entry.offer().fingerprint())
            .filter(key -> !deliveredKeys.contains(key))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        CataloguePayload normalized = withEntries(base, deliveredEntries);
        return new PreparedCatalogue(normalized, List.copyOf(chunks), omittedKeys);
    }

    private static void sendPreparedCatalogue(
        ServerPlayerEntity player, PreparedCatalogue prepared
    ) {
        CataloguePayload base = prepared.payload();
        List<List<CataloguePayload.Entry>> chunks = prepared.chunks();
        int encodedBytes = 0;
        for (int index = 0; index < chunks.size(); index++) {
            CataloguePayload chunk = new CataloguePayload(
                base.postPos(),
                base.revision(),
                base.workerUuid(),
                base.workerState(),
                base.status(),
                base.lastFailure(),
                base.targetCount(),
                base.enabledCount(),
                base.executableCount(),
                Optional.empty(),
                true,
                index,
                chunks.size(),
                chunks.get(index)
            );
            encodedBytes += encodedPayloadSize(player, chunk);
            ServerPlayNetworking.send(player, chunk);
        }
        // Worker cargo can contain arbitrary modded components. Send its
        // bounded summary once after the static row baseline rather than
        // repeating it in every catalogue chunk.
        sendBoundedTelemetry(player, base);
        Map<String, CataloguePayload.Entry> rows = entryRows(base.entries());
        VIEWER_CATALOGUES.put(player, new ViewerCatalogueState(
            base.postPos().toImmutable(), base.revision(), Map.copyOf(rows),
            prepared.omittedKeys()
        ));
        MerchantVillagerMod.LOGGER.debug(
            "Sent Merchant catalogue snapshot: {} rows, {} chunks, {} encoded bytes",
            base.entries().size(), chunks.size(), encodedBytes
        );
    }

    private static boolean sendCatalogueDeltas(
        ServerPlayerEntity player,
        BlockPos postPos,
        int baseRevision,
        int revision,
        List<CatalogueDeltaPayload.RowDelta> deltas
    ) {
        List<List<CatalogueDeltaPayload.RowDelta>> chunks = new ArrayList<>();
        List<CatalogueDeltaPayload.RowDelta> current = new ArrayList<>();
        int currentBytes = 0;
        for (CatalogueDeltaPayload.RowDelta delta : deltas) {
            int deltaBytes = encodedDeltaSize(player, delta);
            if (deltaBytes > MAX_ENCODED_ROWS_BYTES_PER_CHUNK) {
                MerchantVillagerMod.LOGGER.warn(
                    "Omitting oversized Merchant catalogue delta {} ({} encoded bytes)",
                    delta.tradeKey(), deltaBytes
                );
                continue;
            }
            if (!current.isEmpty()
                && (current.size() >= CatalogueDeltaPayload.MAX_DELTAS_PER_CHUNK
                    || currentBytes + deltaBytes > MAX_ENCODED_ROWS_BYTES_PER_CHUNK)) {
                chunks.add(List.copyOf(current));
                current.clear();
                currentBytes = 0;
            }
            if (chunks.size() >= CatalogueDeltaPayload.MAX_CHUNKS) {
                break;
            }
            current.add(delta);
            currentBytes += deltaBytes;
        }
        if (!current.isEmpty() && chunks.size() < CatalogueDeltaPayload.MAX_CHUNKS) {
            chunks.add(List.copyOf(current));
        }
        if (chunks.isEmpty()) {
            chunks.add(List.of());
        }
        int delivered = chunks.stream().mapToInt(List::size).sum();
        if (delivered != deltas.size()) {
            // A partial delta must never advance the baseline: the next viewer
            // update falls back to a complete bounded snapshot.
            VIEWER_CATALOGUES.remove(player);
            MerchantVillagerMod.LOGGER.warn(
                "Merchant catalogue delta delivered {} of {} rows; requesting full baseline next sync",
                delivered, deltas.size()
            );
            return false;
        }
        for (int index = 0; index < chunks.size(); index++) {
            ServerPlayNetworking.send(player, new CatalogueDeltaPayload(
                postPos,
                baseRevision,
                revision,
                index,
                chunks.size(),
                chunks.get(index)
            ));
        }
        return true;
    }

    private static CataloguePayload telemetryOnly(CataloguePayload source) {
        return new CataloguePayload(
            source.postPos(),
            source.revision(),
            source.workerUuid(),
            source.workerState(),
            source.status(),
            source.lastFailure(),
            source.targetCount(),
            source.enabledCount(),
            source.executableCount(),
            source.workerStats(),
            false,
            0,
            0,
            List.of()
        );
    }

    private static void sendBoundedTelemetry(
        ServerPlayerEntity player, CataloguePayload source
    ) {
        CataloguePayload telemetry = telemetryOnly(source);
        int encodedBytes = safelyEncodedPayloadSize(player, telemetry);
        if (encodedBytes < 0 || encodedBytes > MAX_SAFE_PLAY_PAYLOAD_BYTES) {
            MerchantVillagerMod.LOGGER.warn(
                "Omitting Merchant cargo telemetry that exceeded the {}-byte play-payload budget",
                MAX_SAFE_PLAY_PAYLOAD_BYTES
            );
            telemetry = new CataloguePayload(
                telemetry.postPos(),
                telemetry.revision(),
                telemetry.workerUuid(),
                telemetry.workerState(),
                telemetry.status(),
                telemetry.lastFailure(),
                telemetry.targetCount(),
                telemetry.enabledCount(),
                telemetry.executableCount(),
                Optional.empty(),
                false,
                0,
                0,
                List.of()
            );
        }
        ServerPlayNetworking.send(player, telemetry);
    }

    private static CataloguePayload withEntries(
        CataloguePayload source, List<CataloguePayload.Entry> entries
    ) {
        return new CataloguePayload(
            source.postPos(),
            source.revision(),
            source.workerUuid(),
            source.workerState(),
            source.status(),
            source.lastFailure(),
            source.targetCount(),
            source.enabledCount(),
            source.executableCount(),
            source.workerStats(),
            true,
            0,
            1,
            List.copyOf(entries)
        );
    }

    private static Map<String, CataloguePayload.Entry> entryRows(
        List<CataloguePayload.Entry> entries
    ) {
        Map<String, CataloguePayload.Entry> rows = new LinkedHashMap<>();
        for (CataloguePayload.Entry entry : entries) {
            rows.put(entry.offer().fingerprint(), entry);
        }
        return rows;
    }

    private static int encodedEntrySize(
        ServerPlayerEntity player, CataloguePayload.Entry entry
    ) {
        RegistryByteBuf scratch = new RegistryByteBuf(
            Unpooled.buffer(), player.getRegistryManager()
        );
        try {
            entry.write(scratch);
            return scratch.readableBytes();
        } finally {
            scratch.release();
        }
    }

    private static int encodedDeltaSize(
        ServerPlayerEntity player, CatalogueDeltaPayload.RowDelta delta
    ) {
        RegistryByteBuf scratch = new RegistryByteBuf(
            Unpooled.buffer(), player.getRegistryManager()
        );
        try {
            delta.write(scratch);
            return scratch.readableBytes();
        } finally {
            scratch.release();
        }
    }

    private static int encodedPayloadSize(
        ServerPlayerEntity player, CataloguePayload payload
    ) {
        RegistryByteBuf scratch = new RegistryByteBuf(
            Unpooled.buffer(), player.getRegistryManager()
        );
        try {
            CataloguePayload.CODEC.encode(scratch, payload);
            return scratch.readableBytes();
        } finally {
            scratch.release();
        }
    }

    private static int safelyEncodedPayloadSize(
        ServerPlayerEntity player, CataloguePayload payload
    ) {
        try {
            return encodedPayloadSize(player, payload);
        } catch (RuntimeException invalidOrOversized) {
            MerchantVillagerMod.LOGGER.warn(
                "Could not encode bounded Merchant telemetry; omitting worker cargo",
                invalidOrOversized
            );
            return -1;
        }
    }

    private static boolean allowAtInterval(
        ServerPlayerEntity player,
        Map<ServerPlayerEntity, Long> lastTicks,
        long minimumTicks
    ) {
        long now = player.getEntityWorld().getTime();
        Long previous = lastTicks.get(player);
        if (previous != null && now >= previous && now - previous < minimumTicks) {
            return false;
        }
        lastTicks.put(player, now);
        return true;
    }

    private ModPayloads() {
    }

    private record PreparedCatalogue(
        CataloguePayload payload,
        List<List<CataloguePayload.Entry>> chunks,
        Set<String> omittedKeys
    ) {
    }

    private record ViewerCatalogueState(
        BlockPos postPos,
        int revision,
        Map<String, CataloguePayload.Entry> rows,
        Set<String> omittedKeys
    ) {
    }
}
