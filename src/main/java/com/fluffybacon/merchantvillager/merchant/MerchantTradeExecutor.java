package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.trade.OfferIdentity;
import com.fluffybacon.merchantvillager.trade.TargetMerchantAvailability;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffer;

public final class MerchantTradeExecutor {
    public static boolean executeOne(
        ServerWorld world,
        VillagerEntity worker,
        MerchantEntity target,
        MerchantWorkerState state,
        MerchantPostBlockEntity post
    ) {
        if (!TargetMerchantAvailability.canTradeNow(target)
            || target.getEntityWorld() != world
            || worker.getEntityWorld() != world
            || worker.squaredDistanceTo(target) > MerchantVillagerConfig.INTERACTION_DISTANCE_SQUARED
            || !post.isEnabled(state.offerFingerprint())
            || state.offerIndex() < 0
            || state.offerIndex() >= target.getOffers().size()) {
            return false;
        }
        TradeOffer offer = target.getOffers().get(state.offerIndex());
        String currentFingerprint = OfferIdentity.create(
            world.getRegistryManager(),
            target.getUuid(),
            state.offerIndex(),
            offer.getFirstBuyItem(),
            offer.getSecondBuyItem(),
            offer.getSellItem(),
            offer.getMaxUses(),
            offer.getMerchantExperience(),
            offer.getPriceMultiplier()
        );
        if (!currentFingerprint.equals(state.offerFingerprint()) || offer.isDisabled()) {
            return false;
        }

        int firstCount = offer.getDisplayedFirstBuyItem().getCount();
        int secondCount = offer.getDisplayedSecondBuyItem().isEmpty()
            ? 0
            : offer.getDisplayedSecondBuyItem().getCount();
        if (!state.executeCargoTrade(
            offer.getFirstBuyItem(),
            firstCount,
            offer.getSecondBuyItem(),
            secondCount,
            offer.copySellItem()
        )) {
            return false;
        }

        // MerchantEntity#trade is the real vanilla hook: one use increment,
        // demand/experience handling, and no player advancement without a customer.
        target.trade(offer);
        state.completeExecution();
        return true;
    }

    private MerchantTradeExecutor() {
    }
}
