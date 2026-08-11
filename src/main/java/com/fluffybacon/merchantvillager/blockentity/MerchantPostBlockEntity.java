package com.fluffybacon.merchantvillager.blockentity;

import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.inventory.AdjacentChestManager;
import com.fluffybacon.merchantvillager.inventory.ChestRoleMarker;
import com.fluffybacon.merchantvillager.inventory.OrphanedMarkerCleanupState;
import com.fluffybacon.merchantvillager.inventory.PendingMarkerRemovals;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.network.ModPayloads;
import com.fluffybacon.merchantvillager.registry.ModBlockEntities;
import com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler;
import com.fluffybacon.merchantvillager.trade.GlobalTradeCatalogue;
import com.fluffybacon.merchantvillager.trade.GlobalTradeCatalogueCache;
import com.fluffybacon.merchantvillager.trade.MerchantScanner;
import com.fluffybacon.merchantvillager.trade.OfferPermission;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import com.fluffybacon.merchantvillager.trade.TradeProvider;
import com.fluffybacon.merchantvillager.trade.TradeSignatures;
import com.fluffybacon.merchantvillager.trade.TradeTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradedItem;
import net.minecraft.village.TradeOffer;
import org.jspecify.annotations.Nullable;

public final class MerchantPostBlockEntity extends LockableContainerBlockEntity
    implements SidedInventory, ExtendedScreenHandlerFactory<BlockPos> {
    public static final int INVENTORY_SIZE = 27;
    private static final int[] SLOTS = createSlots();
    private static final long PERMISSION_EXPIRY_TICKS = 20L * 60L * 60L * 24L * 7L;
    private static final UUID GLOBAL_PERMISSION_TARGET = new UUID(0L, 0L);
    private static final int CATALOGUE_DELTA_SYNC_INTERVAL = 4;

    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final Map<String, OfferPermission> permissions = new HashMap<>();
    private final Map<String, Long> unreachableUntil = new HashMap<>();
    private List<OfferSnapshot> offers = List.of();
    private List<GlobalTradeCatalogue.Entry> catalogueEntries = List.of();
    private final Map<String, String> liveTradeKeys = new HashMap<>();
    @Nullable private UUID assignedMerchant;
    private int scanCooldown;
    private int viewerSyncCooldown;
    private int viewerCatalogueSyncCooldown;
    private boolean viewerCatalogueSyncPending;
    private int catalogueRevision;
    private int selectionCursor;
    private boolean paused;
    private String lastFailure = "";
    @Nullable private BlockPos importChest;
    @Nullable private BlockPos exportChest;
    @Nullable private BlockPos importMarker;
    @Nullable private BlockPos exportMarker;
    private final PendingMarkerRemovals pendingMarkerRemovals;
    private int chestReconcileCooldown;
    private boolean chestRescanRequested = true;
    private String chestRoleStatus = "Scanning adjacent chests";

    public MerchantPostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MERCHANT_POST, pos, state);
        pendingMarkerRemovals = new PendingMarkerRemovals(pos);
    }

    public static void serverTick(ServerWorld world, BlockPos pos, BlockState state, MerchantPostBlockEntity post) {
        if (post.scanCooldown-- <= 0) {
            post.refreshCatalogue(false);
        }
        if (post.viewerSyncCooldown-- <= 0) {
            post.viewerSyncCooldown = 20;
            post.syncViewerTelemetry();
        }
        if (post.viewerCatalogueSyncCooldown > 0) {
            post.viewerCatalogueSyncCooldown--;
        }
        if (post.viewerCatalogueSyncPending
            && post.viewerCatalogueSyncCooldown <= 0) {
            post.syncViewerCatalogueDeltas();
        }
        if (post.chestRescanRequested || post.chestReconcileCooldown-- <= 0) {
            post.reconcileChestRoles(world);
            post.pumpApprovedImports(world);
        }
        post.validateAssignment();
    }

    private void reconcileChestRoles(ServerWorld serverWorld) {
        chestRescanRequested = false;
        chestReconcileCooldown = 20;
        drainPendingMarkerRemovals(serverWorld);
        BlockPos oldImport = importChest;
        BlockPos oldExport = exportChest;
        BlockPos oldImportMarker = importMarker;
        BlockPos oldExportMarker = exportMarker;
        boolean oldDual = oldImport != null && oldImport.equals(oldExport);

        AdjacentChestManager.Assignment assignment = AdjacentChestManager.assign(
            AdjacentChestManager.scan(serverWorld, pos),
            Optional.ofNullable(importChest),
            Optional.ofNullable(exportChest)
        );
        BlockPos nextImport = assignment.importPos().orElse(null);
        BlockPos nextExport = assignment.exportPos().orElse(null);
        boolean rolesChanged = !java.util.Objects.equals(oldImport, nextImport)
            || !java.util.Objects.equals(oldExport, nextExport);
        if (rolesChanged) {
            if (oldDual) {
                removeOrQueueMarker(
                    serverWorld, oldImportMarker, oldImport, ChestRoleMarker.Role.DUAL
                );
            } else {
                if (oldImport != null) {
                    removeOrQueueMarker(
                        serverWorld, oldImportMarker, oldImport, ChestRoleMarker.Role.IMPORT
                    );
                }
                if (oldExport != null) {
                    removeOrQueueMarker(
                        serverWorld, oldExportMarker, oldExport, ChestRoleMarker.Role.EXPORT
                    );
                }
            }
            importMarker = null;
            exportMarker = null;
        }
        importChest = nextImport;
        exportChest = nextExport;

        if (assignment.isEmpty()) {
            chestRoleStatus = "No adjacent chest";
            importMarker = null;
            exportMarker = null;
        } else if (assignment.isDualPurpose()) {
            importMarker = ChestRoleMarker.reconcile(
                serverWorld,
                pos,
                importChest,
                importMarker,
                ChestRoleMarker.Role.DUAL,
                Set.of()
            ).orElse(null);
            exportMarker = null;
            chestRoleStatus = importMarker == null
                ? "Dual chest assigned; marker obstructed"
                : "Dual Import / Export chest";
        } else {
            HashSet<BlockPos> reserved = new HashSet<>();
            importMarker = ChestRoleMarker.reconcile(
                serverWorld,
                pos,
                importChest,
                importMarker,
                ChestRoleMarker.Role.IMPORT,
                reserved
            ).orElse(null);
            if (importMarker != null) {
                reserved.add(importMarker);
            }
            exportMarker = ChestRoleMarker.reconcile(
                serverWorld,
                pos,
                exportChest,
                exportMarker,
                ChestRoleMarker.Role.EXPORT,
                reserved
            ).orElse(null);
            chestRoleStatus = importMarker == null || exportMarker == null
                ? "Import / Export assigned; marker obstructed"
                : "Import and Export chests assigned";
        }

        if (rolesChanged
            || !java.util.Objects.equals(oldImportMarker, importMarker)
            || !java.util.Objects.equals(oldExportMarker, exportMarker)) {
            catalogueRevision++;
            markDirty();
            scheduleViewerCatalogueSync();
        }
    }

    private void drainPendingMarkerRemovals(ServerWorld serverWorld) {
        if (pendingMarkerRemovals.drain(entry -> ChestRoleMarker.removeOwned(
            serverWorld,
            entry.markerPos(),
            pos,
            entry.chestPos(),
            entry.role()
        ))) {
            markDirty();
        }
    }

    private void removeOrQueueMarker(
        ServerWorld serverWorld,
        @Nullable BlockPos markerPos,
        @Nullable BlockPos chestPos,
        ChestRoleMarker.Role role
    ) {
        if (markerPos == null || chestPos == null) {
            return;
        }
        if (!ChestRoleMarker.removeOwned(serverWorld, markerPos, pos, chestPos, role)) {
            pendingMarkerRemovals.enqueue(markerPos, chestPos, role);
        }
    }

    private void pumpApprovedImports(ServerWorld serverWorld) {
        if (workerCargoMayNeedRecovery(serverWorld)) {
            return;
        }
        Inventory source = getImportInventory(serverWorld);
        if (source == null) {
            return;
        }
        boolean movedAny = false;
        for (int slot = 0; slot < source.size(); slot++) {
            ItemStack stack = source.getStack(slot);
            int desiredReserve = approvedInputReserve(stack);
            int currentReserve = countExactBackpackStack(stack);
            int requested = Math.min(stack.getCount(), Math.max(0, desiredReserve - currentReserve));
            if (stack.isEmpty() || requested <= 0) {
                continue;
            }
            ItemStack attempted = stack.copyWithCount(requested);
            ItemStack remainder = HopperBlockEntity.transfer(source, this, attempted, null);
            int moved = requested - remainder.getCount();
            if (moved > 0) {
                stack.decrement(moved);
                if (stack.isEmpty()) {
                    source.setStack(slot, ItemStack.EMPTY);
                }
                movedAny = true;
            }
        }
        if (movedAny) {
            source.markDirty();
            notifyMaterialOrPermissionChange();
        }
    }

    /**
     * Never refill slots that an in-flight or recovering worker may need in
     * order to return its exact reserved inputs. An unloaded assigned worker
     * is treated conservatively because its persisted cargo is not observable.
     */
    private boolean workerCargoMayNeedRecovery(ServerWorld serverWorld) {
        if (assignedMerchant == null) {
            return false;
        }
        Entity entity = serverWorld.getEntity(assignedMerchant);
        if (entity == null) {
            return true;
        }
        return entity instanceof MerchantWorker worker
            && worker.merchantVillager$getState().hasCargo();
    }

    /**
     * Keep at most one stack's worth of each approved material, sized for one
     * execution of every approved catalogue row that accepts this exact stack.
     * This gives a Dual chest a useful input buffer without continuously
     * pulling exported rewards back out of it.
     */
    private int approvedInputReserve(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        long desired = 0L;
        for (GlobalTradeCatalogue.Entry entry : catalogueEntries) {
            if (!isEnabled(entry.key().serialized())) {
                continue;
            }
            if (entry.preview().firstInput().matches(stack)) {
                desired += entry.preview().firstInput().count();
            }
            if (entry.preview().secondInput().filter(input -> input.matches(stack)).isPresent()) {
                desired += entry.preview().secondInput().orElseThrow().count();
            }
        }
        return (int)Math.min(stack.getMaxCount(), desired);
    }

    private int countExactBackpackStack(ItemStack expected) {
        int count = 0;
        for (ItemStack present : inventory) {
            if (ItemStack.areItemsAndComponentsEqual(present, expected)) {
                count += present.getCount();
            }
        }
        return count;
    }

    private boolean isApprovedTradeInput(ItemStack stack) {
        return catalogueEntries.stream()
            .filter(entry -> isEnabled(entry.key().serialized()))
            .anyMatch(entry -> entry.preview().firstInput().matches(stack)
                || entry.preview().secondInput().map(input -> input.matches(stack)).orElse(false));
    }

    public void refreshCatalogue(boolean forceSync) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        scanCooldown = MerchantVillagerConfig.CATALOGUE_SCAN_INTERVAL;
        List<OfferSnapshot> scanned = MerchantScanner.scan(serverWorld, pos);
        GlobalTradeCatalogue global = GlobalTradeCatalogueCache.get(serverWorld);
        LinkedHashMap<String, GlobalTradeCatalogue.Entry> indexed = new LinkedHashMap<>();
        global.entries().forEach(entry -> indexed.put(entry.key().serialized(), entry));
        Map<String, String> nextLiveTradeKeys = new HashMap<>();
        long now = serverWorld.getTime();
        for (OfferSnapshot snapshot : scanned) {
            TradeOffer live = resolveLiveOffer(serverWorld, snapshot);
            if (live == null) {
                continue;
            }
            Optional<GlobalTradeCatalogue.Resolution> resolution = global.resolve(
                serverWorld.getRegistryManager(), live
            );
            GlobalTradeCatalogue.Entry entry;
            if (resolution.isPresent()) {
                entry = resolution.get().entry();
            } else {
                var key = TradeSignatures.exact(serverWorld.getRegistryManager(), live);
                TradeProvider observedProvider = providerFor(serverWorld, snapshot);
                GlobalTradeCatalogue.Entry observedEntry = new GlobalTradeCatalogue.Entry(
                    key,
                    TradeTemplate.from(live),
                    false,
                    List.of(observedProvider),
                    1,
                    1,
                    0
                );
                entry = indexed.compute(
                    key.serialized(),
                    (ignored, existing) -> existing == null
                        ? observedEntry
                        : existing.withProvider(observedProvider)
                );
            }
            String tradeKey = entry.key().serialized();
            nextLiveTradeKeys.put(snapshot.fingerprint(), tradeKey);

            // One-way migration from the first RC's approval hashes. Those
            // hashes included hidden max-use/XP fields, so visibly identical
            // recipes were split. Preserve the player's choice while moving
            // it to the component-and-count-sensitive shared recipe key.
            for (String legacyTradeKey : List.of(
                TradeSignatures.legacyExact(serverWorld.getRegistryManager(), live).serialized(),
                TradeSignatures.legacyContextualMap(
                    serverWorld.getRegistryManager(), live
                ).serialized()
            )) {
                OfferPermission legacyGlobal = permissions.get(legacyTradeKey);
                if (legacyGlobal != null && legacyGlobal.enabled()) {
                    permissions.put(tradeKey, new OfferPermission(
                        tradeKey, GLOBAL_PERMISSION_TARGET, true, now
                    ));
                    permissions.remove(legacyTradeKey);
                }
            }

            // Best-effort one-way migration from the old entity-specific
            // permission to the new global approval.
            OfferPermission legacy = permissions.get(snapshot.fingerprint());
            if (legacy != null && legacy.enabled()) {
                permissions.put(tradeKey, new OfferPermission(
                    tradeKey, GLOBAL_PERMISSION_TARGET, true, now
                ));
                permissions.remove(snapshot.fingerprint());
            }
        }
        List<GlobalTradeCatalogue.Entry> nextEntries = indexed.values().stream()
            .sorted(java.util.Comparator.comparing(entry -> entry.key().serialized()))
            .toList();
        boolean changed = !sameCatalogue(offers, scanned)
            || !catalogueEntries.stream().map(entry -> entry.key().serialized()).toList()
                .equals(nextEntries.stream().map(entry -> entry.key().serialized()).toList());
        offers = scanned;
        catalogueEntries = nextEntries;
        liveTradeKeys.clear();
        liveTradeKeys.putAll(nextLiveTradeKeys);
        permissions.values().removeIf(permission ->
            !isGlobalTradeKey(permission.fingerprint())
                && now - permission.lastSeen() > PERMISSION_EXPIRY_TICKS
                && scanned.stream().noneMatch(offer -> offer.fingerprint().equals(permission.fingerprint()))
        );
        unreachableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (changed || forceSync) {
            catalogueRevision++;
            markDirty();
            if (forceSync) {
                syncViewersFull();
            } else {
                scheduleViewerCatalogueSync();
            }
        }
    }

    @Nullable
    private static TradeOffer resolveLiveOffer(ServerWorld serverWorld, OfferSnapshot snapshot) {
        Entity entity = serverWorld.getEntity(snapshot.targetUuid());
        if (!(entity instanceof MerchantEntity merchant)
            || snapshot.offerIndex() < 0
            || snapshot.offerIndex() >= merchant.getOffers().size()) {
            return null;
        }
        return merchant.getOffers().get(snapshot.offerIndex());
    }

    private static TradeProvider providerFor(
        ServerWorld serverWorld, OfferSnapshot snapshot
    ) {
        Entity entity = serverWorld.getEntity(snapshot.targetUuid());
        if (entity instanceof WanderingTraderEntity) {
            return TradeProvider.wanderingTrader(0, 0, Math.max(0, snapshot.offerIndex()));
        }
        String type = entity instanceof VillagerEntity villager
            ? villager.getVillagerData().type().getKey()
                .map(key -> key.getValue().toString())
                .orElse("minecraft:plains")
            : "minecraft:plains";
        return TradeProvider.villager(
            snapshot.profession(),
            Math.max(0, snapshot.villagerLevel()),
            type,
            Math.max(0, snapshot.offerIndex())
        );
    }

    private static boolean isGlobalTradeKey(String key) {
        return key.startsWith("exact:")
            || key.startsWith("context:");
    }

    private static boolean sameCatalogue(List<OfferSnapshot> left, List<OfferSnapshot> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            OfferSnapshot a = left.get(i);
            OfferSnapshot b = right.get(i);
            if (!a.fingerprint().equals(b.fingerprint())
                || a.uses() != b.uses()
                || a.targetAvailable() != b.targetAvailable()) {
                return false;
            }
        }
        return true;
    }

    private void syncViewersFull() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (player.currentScreenHandler instanceof MerchantPostScreenHandler handler
                && handler.getPostPos().equals(pos)) {
                sendCatalogue(player);
            }
        }
        viewerCatalogueSyncPending = false;
        viewerCatalogueSyncCooldown = CATALOGUE_DELTA_SYNC_INTERVAL;
    }

    private void syncViewerCatalogueDeltas() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (player.currentScreenHandler instanceof MerchantPostScreenHandler handler
                && handler.getPostPos().equals(pos)) {
                ModPayloads.sendCatalogueDelta(player, this);
            }
        }
        viewerCatalogueSyncPending = false;
        viewerCatalogueSyncCooldown = CATALOGUE_DELTA_SYNC_INTERVAL;
    }

    private void scheduleViewerCatalogueSync() {
        viewerCatalogueSyncPending = true;
    }

    private void syncViewerTelemetry() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (player.currentScreenHandler instanceof MerchantPostScreenHandler handler
                && handler.getPostPos().equals(pos)) {
                ModPayloads.sendTelemetry(player, this);
            }
        }
    }

    public void sendCatalogue(ServerPlayerEntity player) {
        ModPayloads.sendCatalogue(player, this);
    }

    public boolean setOfferEnabled(ServerPlayerEntity player, String tradeKey, boolean enabled) {
        if (!isValidController(player)) {
            return false;
        }
        if (catalogueEntries.stream()
            .noneMatch(entry -> entry.key().serialized().equals(tradeKey))) {
            return false;
        }
        OfferPermission old = permissions.get(tradeKey);
        if (old != null && old.enabled() == enabled) {
            return false;
        }
        permissions.put(tradeKey, new OfferPermission(
            tradeKey,
            GLOBAL_PERMISSION_TARGET,
            enabled,
            world == null ? 0L : world.getTime()
        ));
        catalogueRevision++;
        requestChestRescan();
        markDirty();
        scheduleViewerCatalogueSync();
        return true;
    }

    public int disableAllOffers(ServerPlayerEntity player) {
        if (!isValidController(player)) {
            return 0;
        }
        int changed = 0;
        for (Map.Entry<String, OfferPermission> entry : permissions.entrySet()) {
            OfferPermission permission = entry.getValue();
            if (permission.enabled()) {
                entry.setValue(new OfferPermission(
                    permission.fingerprint(),
                    permission.targetUuid(),
                    false,
                    permission.lastSeen()
                ));
                changed++;
            }
        }
        if (changed > 0) {
            notifyMaterialOrPermissionChange();
        }
        return changed;
    }

    /**
     * Moves server-owned stacks through a catalogue ghost input. The packet
     * supplies only identity and interaction mode; all stack matching, counts,
     * inventory capacity, and mutations are derived and enforced here.
     */
    public int depositForOffer(
        ServerPlayerEntity player, String fingerprint, int inputIndex, int mode
    ) {
        if (!isValidController(player)
            || inputIndex < 0
            || inputIndex > 1
            || mode < 0
            || mode > 2) {
            return 0;
        }
        String normalizedKey = liveTradeKeys.getOrDefault(fingerprint, fingerprint);
        Optional<GlobalTradeCatalogue.Entry> catalogueEntry = catalogueEntries.stream()
            .filter(entry -> entry.key().serialized().equals(normalizedKey))
            .findFirst();
        if (catalogueEntry.isEmpty()) {
            return 0;
        }
        TradedItem required;
        if (inputIndex == 0) {
            required = catalogueEntry.get().preview().firstInput();
        } else if (catalogueEntry.get().preview().secondInput().isPresent()) {
            required = catalogueEntry.get().preview().secondInput().get();
        } else {
            return 0;
        }

        int moved = 0;
        if (mode == 2) {
            DefaultedList<ItemStack> main = player.getInventory().getMainStacks();
            for (ItemStack stack : main) {
                moved += moveMatchingIntoPost(stack, stack.getCount(), required);
            }
            player.getInventory().markDirty();
        } else {
            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            moved = moveMatchingIntoPost(cursor, mode == 1 ? 1 : cursor.getCount(), required);
            if (cursor.isEmpty()) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            }
        }
        if (moved > 0) {
            notifyMaterialOrPermissionChange();
            player.currentScreenHandler.syncState();
        }
        return moved;
    }

    private int moveMatchingIntoPost(ItemStack source, int requested, TradedItem required) {
        if (source.isEmpty() || requested <= 0 || !required.matches(source)) {
            return 0;
        }
        int remaining = Math.min(requested, source.getCount());
        int initial = remaining;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack present = inventory.get(slot);
            if (!present.isEmpty() && ItemStack.areItemsAndComponentsEqual(present, source)) {
                int limit = Math.min(getMaxCount(present), present.getMaxCount());
                int amount = Math.min(remaining, Math.max(0, limit - present.getCount()));
                present.increment(amount);
                source.decrement(amount);
                remaining -= amount;
            }
        }
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            if (inventory.get(slot).isEmpty()) {
                int amount = Math.min(
                    remaining,
                    Math.min(getMaxCount(source), source.getMaxCount())
                );
                inventory.set(slot, source.copyWithCount(amount));
                source.decrement(amount);
                remaining -= amount;
            }
        }
        return initial - remaining;
    }

    private boolean isValidController(ServerPlayerEntity player) {
        return player.currentScreenHandler instanceof MerchantPostScreenHandler handler
            && handler.getPostPos().equals(pos)
            && canPlayerUse(player);
    }

    public void notifyMaterialOrPermissionChange() {
        catalogueRevision++;
        markDirty();
        scheduleViewerCatalogueSync();
    }

    public boolean isEnabled(String fingerprint) {
        String tradeKey = liveTradeKeys.getOrDefault(fingerprint, fingerprint);
        OfferPermission permission = permissions.get(tradeKey);
        return permission != null && permission.enabled();
    }

    public boolean isOfferEnabled(OfferSnapshot offer) {
        return isEnabled(offer.fingerprint());
    }

    public String tradeKeyFor(OfferSnapshot offer) {
        return liveTradeKeys.getOrDefault(offer.fingerprint(), "");
    }

    public boolean isCoolingDown(String fingerprint) {
        long now = world == null ? 0L : world.getTime();
        Long until = unreachableUntil.get(fingerprint);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            unreachableUntil.remove(fingerprint);
            return false;
        }
        return true;
    }

    public void markUnreachable(String fingerprint, long until) {
        if (!fingerprint.isEmpty()) {
            unreachableUntil.put(fingerprint, until);
            markDirty();
        }
    }

    /**
     * Server-owned approval hook used by deterministic GameTests and recovery
     * tooling. Network handlers never call this without their own validation.
     */
    public void setOfferEnabledInternal(String fingerprint, boolean enabled) {
        offers.stream().filter(offer -> offer.fingerprint().equals(fingerprint)).findFirst()
            .ifPresent(offer -> {
                String tradeKey = liveTradeKeys.getOrDefault(fingerprint, fingerprint);
                permissions.put(tradeKey, new OfferPermission(
                    tradeKey,
                    isGlobalTradeKey(tradeKey) ? GLOBAL_PERMISSION_TARGET : offer.targetUuid(),
                    enabled,
                    world == null ? 0L : world.getTime()
                ));
                catalogueRevision++;
                requestChestRescan();
                markDirty();
                scheduleViewerCatalogueSync();
            });
    }

    public List<OfferSnapshot> getOffers() {
        return offers;
    }

    public List<GlobalTradeCatalogue.Entry> getCatalogueEntries() {
        return catalogueEntries;
    }

    public int getCatalogueRevision() {
        return catalogueRevision;
    }

    public int getScanCooldown() {
        return Math.max(0, scanCooldown);
    }

    public int getSelectionCursor() {
        return selectionCursor;
    }

    public void advanceSelectionCursor(int selectedIndex, int targetCount) {
        selectionCursor = targetCount <= 0 ? 0 : Math.floorMod(selectedIndex + 1, targetCount);
        markDirty();
    }

    public Optional<UUID> getAssignedMerchant() {
        return Optional.ofNullable(assignedMerchant);
    }

    public void onMerchantCargoChanged() {
        markDirty();
        if (world instanceof ServerWorld) {
            syncViewerTelemetry();
        }
    }

    public void assignMerchant(UUID merchantUuid) {
        if (assignedMerchant == null || assignedMerchant.equals(merchantUuid)) {
            assignedMerchant = merchantUuid;
            markDirty();
        }
    }

    public void clearMerchant(UUID merchantUuid) {
        if (merchantUuid.equals(assignedMerchant)) {
            assignedMerchant = null;
            markDirty();
        }
    }

    private void validateAssignment() {
        if (assignedMerchant == null || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Entity entity = serverWorld.getEntity(assignedMerchant);
        if (entity == null) {
            for (ServerWorld candidateWorld : serverWorld.getServer().getWorlds()) {
                if (candidateWorld != serverWorld && candidateWorld.getEntity(assignedMerchant) != null) {
                    assignedMerchant = null;
                    markDirty();
                    return;
                }
            }
        }
        if (entity != null && (!(entity instanceof MerchantWorker worker)
            || worker.merchantVillager$getState().postPos() == null
            || !pos.equals(worker.merchantVillager$getState().postPos()))) {
            assignedMerchant = null;
            markDirty();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        markDirty();
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(String failure) {
        lastFailure = failure == null ? "" : failure;
        markDirty();
    }

    public Optional<BlockPos> getImportChestPos() {
        return Optional.ofNullable(importChest);
    }

    public Optional<BlockPos> getExportChestPos() {
        return Optional.ofNullable(exportChest);
    }

    public Optional<BlockPos> getImportMarkerPos() {
        return Optional.ofNullable(importMarker);
    }

    public Optional<BlockPos> getExportMarkerPos() {
        return Optional.ofNullable(exportMarker);
    }

    public int getPendingMarkerRemovalCount() {
        return pendingMarkerRemovals.size();
    }

    @Nullable
    public Inventory getImportInventory(ServerWorld serverWorld) {
        return importChest == null
            ? null
            : com.fluffybacon.merchantvillager.inventory.OutputChestFinder
                .touchingChestInventory(serverWorld, pos, importChest);
    }

    @Nullable
    public Inventory getExportInventory(ServerWorld serverWorld) {
        return exportChest == null
            ? null
            : com.fluffybacon.merchantvillager.inventory.OutputChestFinder
                .touchingChestInventory(serverWorld, pos, exportChest);
    }

    public String getChestRoleStatus() {
        return chestRoleStatus;
    }

    public void requestChestRescan() {
        chestRescanRequested = true;
        chestReconcileCooldown = 0;
    }

    public void onNearbyStorageBlockBroken(BlockPos brokenPos) {
        if (brokenPos.getManhattanDistance(pos) <= 3) {
            requestChestRescan();
        }
    }

    public int countEnabledOffers() {
        return (int)catalogueEntries.stream()
            .filter(entry -> isEnabled(entry.key().serialized()))
            .count();
    }

    public void onPostDestroyed() {
        paused = true;
        if (world instanceof ServerWorld serverWorld) {
            // Once this block entity is removed its local queue can never tick
            // again. Transfer every unresolved exact-ownership cleanup first.
            for (PendingMarkerRemovals.Entry entry : pendingMarkerRemovals.entries()) {
                removeOrHandoffDestroyedMarker(serverWorld, entry);
            }
            pendingMarkerRemovals.clear();
            boolean dual = importChest != null && importChest.equals(exportChest);
            if (dual) {
                removeOrHandoffDestroyedMarker(
                    serverWorld,
                    importMarker,
                    importChest,
                    ChestRoleMarker.Role.DUAL
                );
            } else {
                if (importChest != null) {
                    removeOrHandoffDestroyedMarker(
                        serverWorld,
                        importMarker,
                        importChest,
                        ChestRoleMarker.Role.IMPORT
                    );
                }
                if (exportChest != null) {
                    removeOrHandoffDestroyedMarker(
                        serverWorld,
                        exportMarker,
                        exportChest,
                        ChestRoleMarker.Role.EXPORT
                    );
                }
            }
            if (assignedMerchant != null) {
                Entity entity = serverWorld.getEntity(assignedMerchant);
                if (entity instanceof MerchantWorker worker) {
                    worker.merchantVillager$getState().onPostDestroyed(pos);
                }
            }
        }
        assignedMerchant = null;
        markDirty();
    }

    private void removeOrHandoffDestroyedMarker(
        ServerWorld serverWorld,
        @Nullable BlockPos markerPos,
        @Nullable BlockPos chestPos,
        ChestRoleMarker.Role role
    ) {
        if (markerPos == null || chestPos == null) {
            return;
        }
        removeOrHandoffDestroyedMarker(
            serverWorld,
            new PendingMarkerRemovals.Entry(markerPos, chestPos, role)
        );
    }

    private void removeOrHandoffDestroyedMarker(
        ServerWorld serverWorld,
        PendingMarkerRemovals.Entry entry
    ) {
        if (!OrphanedMarkerCleanupState.isValid(pos, entry)) {
            return;
        }
        if (!ChestRoleMarker.removeOwned(
            serverWorld,
            entry.markerPos(),
            pos,
            entry.chestPos(),
            entry.role()
        )) {
            OrphanedMarkerCleanupState.enqueue(serverWorld, pos, entry);
        }
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("container.merchant_villager.merchant_post");
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return inventory;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
        this.inventory = inventory;
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new MerchantPostScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return pos.toImmutable();
    }

    @Override
    public int size() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return isApprovedTradeInput(stack) || TradeInputMatcher.isAccepted(stack, offers);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return dir != Direction.DOWN && isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return dir != Direction.UP;
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        Inventories.readData(view, inventory);
        permissions.clear();
        for (OfferPermission permission : view.getTypedListView("OfferPermissions", OfferPermission.CODEC)) {
            permissions.put(permission.fingerprint(), permission);
        }
        unreachableUntil.clear();
        for (OfferCooldown cooldown : view.getTypedListView("UnreachableOffers", OfferCooldown.CODEC)) {
            unreachableUntil.put(cooldown.fingerprint(), cooldown.until());
        }
        liveTradeKeys.clear();
        for (TradeKeyMapping mapping : view.getTypedListView("LiveTradeKeys", TradeKeyMapping.CODEC)) {
            liveTradeKeys.put(mapping.liveFingerprint(), mapping.tradeKey());
        }
        assignedMerchant = view.read("AssignedMerchant", net.minecraft.util.Uuids.CODEC).orElse(null);
        paused = view.getBoolean("Paused", false);
        lastFailure = view.getString("LastFailure", "");
        catalogueRevision = view.getInt("CatalogueRevision", 0);
        selectionCursor = Math.max(0, view.getInt("SelectionCursor", 0));
        importChest = view.read("ImportChest", BlockPos.CODEC).orElse(null);
        exportChest = view.read("ExportChest", BlockPos.CODEC).orElse(null);
        importMarker = view.read("ImportMarker", BlockPos.CODEC).orElse(null);
        exportMarker = view.read("ExportMarker", BlockPos.CODEC).orElse(null);
        pendingMarkerRemovals.clear();
        for (PendingMarkerRemovals.Entry entry
            : view.getTypedListView("PendingMarkerRemovals", PendingMarkerRemovals.Entry.CODEC)) {
            pendingMarkerRemovals.restore(entry);
        }
        chestRoleStatus = view.getString("ChestRoleStatus", "Scanning adjacent chests");
        chestRescanRequested = true;
        chestReconcileCooldown = 0;
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        Inventories.writeData(view, inventory);
        WriteView.ListAppender<OfferPermission> list = view.getListAppender("OfferPermissions", OfferPermission.CODEC);
        permissions.values().stream()
            .sorted(java.util.Comparator.comparing(OfferPermission::fingerprint))
            .forEach(list::add);
        WriteView.ListAppender<OfferCooldown> cooldowns =
            view.getListAppender("UnreachableOffers", OfferCooldown.CODEC);
        unreachableUntil.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new OfferCooldown(entry.getKey(), entry.getValue()))
            .forEach(cooldowns::add);
        WriteView.ListAppender<TradeKeyMapping> tradeKeys =
            view.getListAppender("LiveTradeKeys", TradeKeyMapping.CODEC);
        liveTradeKeys.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new TradeKeyMapping(entry.getKey(), entry.getValue()))
            .forEach(tradeKeys::add);
        view.putNullable("AssignedMerchant", net.minecraft.util.Uuids.CODEC, assignedMerchant);
        view.putBoolean("Paused", paused);
        view.putString("LastFailure", lastFailure);
        view.putInt("CatalogueRevision", catalogueRevision);
        view.putInt("SelectionCursor", selectionCursor);
        view.putNullable("ImportChest", BlockPos.CODEC, importChest);
        view.putNullable("ExportChest", BlockPos.CODEC, exportChest);
        view.putNullable("ImportMarker", BlockPos.CODEC, importMarker);
        view.putNullable("ExportMarker", BlockPos.CODEC, exportMarker);
        WriteView.ListAppender<PendingMarkerRemovals.Entry> pendingMarkers =
            view.getListAppender("PendingMarkerRemovals", PendingMarkerRemovals.Entry.CODEC);
        pendingMarkerRemovals.entries().forEach(pendingMarkers::add);
        view.putString("ChestRoleStatus", chestRoleStatus);
    }

    private static int[] createSlots() {
        int[] slots = new int[INVENTORY_SIZE];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    public List<ItemStack> copyInventory() {
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        inventory.forEach(stack -> copy.add(stack.copy()));
        return copy;
    }

    /**
     * Internal recovery insertion deliberately bypasses the current catalogue
     * filter. Inputs already removed for a work order must remain recoverable
     * even when the target unloads or its offer changes.
     */
    public ItemStack insertReturnedInput(ItemStack incoming) {
        ItemStack remainder = incoming.copy();
        for (int slot = 0; slot < inventory.size() && !remainder.isEmpty(); slot++) {
            ItemStack present = inventory.get(slot);
            if (!present.isEmpty() && ItemStack.areItemsAndComponentsEqual(present, remainder)) {
                int moved = Math.min(remainder.getCount(), present.getMaxCount() - present.getCount());
                present.increment(moved);
                remainder.decrement(moved);
            }
        }
        for (int slot = 0; slot < inventory.size() && !remainder.isEmpty(); slot++) {
            if (inventory.get(slot).isEmpty()) {
                int moved = Math.min(remainder.getCount(), remainder.getMaxCount());
                inventory.set(slot, remainder.copyWithCount(moved));
                remainder.decrement(moved);
            }
        }
        if (remainder.getCount() != incoming.getCount()) {
            markDirty();
        }
        return remainder;
    }

    private record OfferCooldown(String fingerprint, long until) {
        private static final Codec<OfferCooldown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("fingerprint").forGetter(OfferCooldown::fingerprint),
            Codec.LONG.fieldOf("until").forGetter(OfferCooldown::until)
        ).apply(instance, OfferCooldown::new));
    }

    private record TradeKeyMapping(String liveFingerprint, String tradeKey) {
        private static final Codec<TradeKeyMapping> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("live").forGetter(TradeKeyMapping::liveFingerprint),
            Codec.STRING.fieldOf("trade").forGetter(TradeKeyMapping::tradeKey)
        ).apply(instance, TradeKeyMapping::new));
    }
}
