package com.fluffybacon.merchantvillager.merchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * A short-lived, server-only hold on the target side of a social trade.
 *
 * <p>The lock deliberately never toggles {@code NoAI}. It is derived from the
 * worker's live {@link MerchantState#TRADING_BUSY} state, expires if the worker
 * misses a tick, and stores only UUIDs. A crash, unload, or interrupted trade
 * therefore cannot leave another villager permanently frozen.</p>
 */
public final class SocialTradeTargetLock {
    private static final Map<MinecraftServer, Map<UUID, Lock>> LOCKS = new WeakHashMap<>();

    public static synchronized boolean acquireOrRefresh(
        ServerWorld world, VillagerEntity worker, MerchantEntity target
    ) {
        if (!worker.isAlive()
            || !target.isAlive()
            || worker.getEntityWorld() != world
            || target.getEntityWorld() != world
            || !(worker instanceof MerchantWorker merchantWorker)) {
            return false;
        }
        MerchantWorkerState state = merchantWorker.merchantVillager$getState();
        if (state.state() != MerchantState.TRADING_BUSY
            || !target.getUuid().equals(state.targetUuid())) {
            return false;
        }
        long now = world.getTime();
        Map<UUID, Lock> locks = LOCKS.computeIfAbsent(
            world.getServer(), ignored -> new HashMap<>()
        );
        locks.values().removeIf(lock -> lock.expiry() < now);
        Lock existing = locks.get(target.getUuid());
        if (existing != null && !existing.worker().equals(worker.getUuid())) {
            return false;
        }
        // One grace tick covers either entity-tick order. The worker refreshes
        // this every active social tick; every explicit exit releases it sooner.
        locks.put(target.getUuid(), new Lock(worker.getUuid(), now + 1L));
        hold(target, worker);
        return true;
    }

    /**
     * Called at the end of a target mob's AI tick, after its own brain/goals
     * had a chance to restart navigation. This is what makes the hold reliable
     * for both normal villagers and Wandering Traders.
     */
    public static synchronized void enforce(ServerWorld world, MerchantEntity target) {
        Map<UUID, Lock> locks = LOCKS.get(world.getServer());
        if (locks == null) {
            return;
        }
        Lock lock = locks.get(target.getUuid());
        if (lock == null) {
            return;
        }
        Entity owner = world.getEntity(lock.worker());
        if (lock.expiry() < world.getTime()
            || !(owner instanceof VillagerEntity worker)
            || !worker.isAlive()
            || !(worker instanceof MerchantWorker merchantWorker)) {
            locks.remove(target.getUuid());
            return;
        }
        MerchantWorkerState state = merchantWorker.merchantVillager$getState();
        if (state.state() != MerchantState.TRADING_BUSY
            || !target.getUuid().equals(state.targetUuid())) {
            locks.remove(target.getUuid());
            return;
        }
        hold(target, worker);
    }

    public static synchronized void releaseWorker(MinecraftServer server, UUID worker) {
        Map<UUID, Lock> locks = LOCKS.get(server);
        if (locks != null) {
            locks.values().removeIf(lock -> lock.worker().equals(worker));
        }
    }

    public static synchronized boolean isLockedBy(
        MinecraftServer server, UUID target, UUID worker
    ) {
        Map<UUID, Lock> locks = LOCKS.get(server);
        Lock lock = locks == null ? null : locks.get(target);
        return lock != null && lock.worker().equals(worker);
    }

    private static void hold(MerchantEntity target, VillagerEntity worker) {
        target.getNavigation().stop();
        target.getMoveControl().moveTo(
            target.getX(), target.getY(), target.getZ(), 0.0
        );
        target.setForwardSpeed(0.0F);
        target.setSidewaysSpeed(0.0F);
        target.setJumping(false);
        Vec3d velocity = target.getVelocity();
        target.setVelocity(0.0, velocity.y, 0.0);
        if (target instanceof VillagerEntity targetVillager) {
            targetVillager.getBrain().forget(MemoryModuleType.WALK_TARGET);
            targetVillager.getBrain().forget(MemoryModuleType.PATH);
        }
        target.lookAtEntity(worker, 30.0F, 30.0F);
        target.getLookControl().lookAt(worker, 30.0F, 30.0F);
    }

    private record Lock(UUID worker, long expiry) {
    }

    private SocialTradeTargetLock() {
    }
}
