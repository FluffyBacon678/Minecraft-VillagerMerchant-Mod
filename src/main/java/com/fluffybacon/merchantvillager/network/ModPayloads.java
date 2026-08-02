package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.inventory.OutputChestFinder;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffer;

public final class ModPayloads {
    public static void initializeServer() {
        PayloadTypeRegistry.playS2C().register(CataloguePayload.ID, CataloguePayload.CODEC);
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
            if (payload.fingerprint().length() != 64
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
                && player.getEntityWorld() instanceof ServerWorld world
                && world.getBlockEntity(payload.postPos()) instanceof MerchantPostBlockEntity post
                && player.currentScreenHandler instanceof com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler handler
                && handler.getPostPos().equals(payload.postPos())) {
                post.refreshCatalogue(true);
                post.sendCatalogue(player);
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
            if (payload.fingerprint().length() == 64
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

        List<CataloguePayload.Entry> entries = new ArrayList<>(post.getOffers().size());
        for (OfferSnapshot offer : post.getOffers()) {
            TradeOffer liveOffer = resolveLiveOffer(player, offer);
            int firstPrice = liveOffer == null
                ? offer.firstInput().count()
                : liveOffer.getDisplayedFirstBuyItem().getCount();
            int secondPrice = liveOffer == null || liveOffer.getDisplayedSecondBuyItem().isEmpty()
                ? offer.secondInput().map(input -> input.count()).orElse(0)
                : liveOffer.getDisplayedSecondBuyItem().getCount();
            int firstCount = TradeInputMatcher.matchingCount(
                inventory,
                offer.firstInput()
            );
            int fundable = firstCount / Math.max(1, firstPrice);
            if (offer.secondInput().isPresent()) {
                int secondCount = TradeInputMatcher.matchingCount(
                    inventory,
                    offer.secondInput().get()
                );
                fundable = Math.min(fundable, secondCount / Math.max(1, secondPrice));
            }
            fundable = Math.min(fundable, offer.remainingUses());
            entries.add(new CataloguePayload.Entry(
                offer,
                post.isEnabled(offer.fingerprint()),
                post.isCoolingDown(offer.fingerprint()),
                Math.max(0, fundable),
                workerState != null && offer.fingerprint().equals(workerState.offerFingerprint()),
                firstCount,
                offer.secondInput().isPresent()
                    ? TradeInputMatcher.matchingCount(inventory, offer.secondInput().get())
                    : 0,
                firstPrice,
                secondPrice
            ));
        }

        int targetCount = (int)post.getOffers().stream()
            .map(OfferSnapshot::targetUuid)
            .distinct()
            .count();
        int enabledCount = (int)entries.stream().filter(CataloguePayload.Entry::enabled).count();
        int executableCount = (int)entries.stream()
            .filter(CataloguePayload.Entry::enabled)
            .filter(entry -> !entry.coolingDown())
            .filter(entry -> entry.fundableExecutions() > 0)
            .filter(entry -> !entry.offer().isOutOfStock() && entry.offer().targetAvailable())
            .count();
        Optional<CataloguePayload.WorkerStats> stats = Optional.empty();
        if (workerState != null && workerEntity != null && player.getEntityWorld() instanceof ServerWorld world) {
            List<ItemStack> cargo = workerState.copyCargo().stream().map(ItemStack::copy).toList();
            Optional<net.minecraft.util.math.BlockPos> chest = Optional.ofNullable(workerState.outputChest());
            String chestStatus = outputChestStatus(world, workerState);
            stats = Optional.of(new CataloguePayload.WorkerStats(
                workerEntity.getDisplayName().getString(),
                workerEntity.getHealth(),
                workerEntity.getMaxHealth(),
                workerEntity.squaredDistanceTo(post.getPos().toCenterPos()),
                cargo,
                Optional.ofNullable(workerState.targetUuid()),
                workerState.plannedExecutions(),
                workerState.completedExecutions(),
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
            List.copyOf(entries)
        );
        ServerPlayNetworking.send(player, payload);
    }

    private static String outputChestStatus(ServerWorld world, MerchantWorkerState state) {
        if (state.outputChest() == null) {
            return "Not selected";
        }
        Inventory inventory = OutputChestFinder.chestInventory(world, state.outputChest());
        if (inventory == null) {
            return "Unavailable";
        }
        List<ItemStack> rewards = new ArrayList<>();
        for (int slot = 0; slot < state.cargo().size(); slot++) {
            if (state.isRewardSlot(slot) && !state.cargo().get(slot).isEmpty()) {
                rewards.add(state.cargo().get(slot));
            }
        }
        return rewards.isEmpty() || OutputChestFinder.hasSpace(inventory, rewards)
            ? "Space available"
            : "Full";
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

    private ModPayloads() {
    }
}
