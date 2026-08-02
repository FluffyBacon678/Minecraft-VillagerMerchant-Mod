package com.fluffybacon.merchantvillager.mixin;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.merchant.ReservationManager;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla villager infection converts and discards the original entity without
 * invoking its death hook. Drop Merchant cargo only after a successful
 * conversion so it cannot vanish with the discarded entity.
 */
@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {
    @Inject(method = "infectVillager", at = @At("RETURN"))
    private void merchantVillager$recoverConvertedCargo(
        ServerWorld world,
        VillagerEntity villager,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() || !(villager instanceof MerchantWorker worker)) {
            return;
        }
        MerchantWorkerState state = worker.merchantVillager$getState();
        if (state.isPostIn(world)
            && state.postPos() != null
            && world.getBlockEntity(state.postPos()) instanceof MerchantPostBlockEntity post) {
            post.clearMerchant(villager.getUuid());
        }
        state.dropCargoOnce(villager);
        ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
    }
}
