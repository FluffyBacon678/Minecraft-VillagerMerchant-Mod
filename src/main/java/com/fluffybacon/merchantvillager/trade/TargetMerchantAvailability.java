package com.fluffybacon.merchantvillager.trade;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;

public final class TargetMerchantAvailability {
    public static boolean canTradeNow(MerchantEntity target) {
        if (!target.isAlive()
            || target.isRemoved()
            || target.hasCustomer()
            || target.isTouchingWater()) {
            return false;
        }
        if (target instanceof VillagerEntity villager) {
            return !villager.isSleeping()
                && !villager.getBrain().hasActivity(Activity.PANIC)
                && !villager.getBrain().hasActivity(Activity.PRE_RAID)
                && !villager.getBrain().hasActivity(Activity.RAID)
                && !villager.getBrain().hasActivity(Activity.HIDE);
        }
        return true;
    }

    private TargetMerchantAvailability() {
    }
}
