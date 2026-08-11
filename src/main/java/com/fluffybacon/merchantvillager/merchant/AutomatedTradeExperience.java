package com.fluffybacon.merchantvillager.merchant;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Captures only the player XP orb produced synchronously by a vanilla
 * MerchantEntity#trade call made on behalf of a Merchant worker.
 *
 * <p>The target identity check is intentional: an unrelated trade or XP spawn
 * on the server thread must retain vanilla behavior.</p>
 */
public final class AutomatedTradeExperience {
    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    public static void captureDuringTrade(
        MerchantEntity target, MerchantWorkerState workerState, Runnable trade
    ) {
        Capture previous = ACTIVE.get();
        ACTIVE.set(new Capture(target, workerState));
        try {
            trade.run();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    /**
     * @return {@code true} when this exact orb belongs to the active automated
     * trade and was stored instead of being spawned
     */
    public static boolean storeCapturedExperience(MerchantEntity source, Entity entity) {
        Capture capture = ACTIVE.get();
        if (capture == null
            || capture.target() != source
            || !(entity instanceof ExperienceOrbEntity orb)) {
            return false;
        }
        capture.workerState().addStoredExperience(orb.getValue());
        return true;
    }

    /** Releases accumulated XP as normal orbs and drains it exactly once. */
    public static int releaseStoredExperience(
        ServerWorld world, VillagerEntity merchant, MerchantWorkerState state
    ) {
        int amount = state.drainStoredExperience();
        if (amount > 0) {
            ExperienceOrbEntity.spawn(
                world,
                merchant.getEntityPos().add(0.0, merchant.getHeight() * 0.5, 0.0),
                amount
            );
        }
        return amount;
    }

    private record Capture(MerchantEntity target, MerchantWorkerState workerState) {
    }

    private AutomatedTradeExperience() {
    }
}
