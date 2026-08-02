package com.fluffybacon.merchantvillager.mixin;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantController;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.merchant.ReservationManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin implements MerchantWorker {
    @Unique
    private final MerchantWorkerState merchantVillager$state = new MerchantWorkerState();

    @Override
    public MerchantWorkerState merchantVillager$getState() {
        return merchantVillager$state;
    }

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void merchantVillager$write(WriteView view, CallbackInfo ci) {
        merchantVillager$state.write(view);
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void merchantVillager$read(ReadView view, CallbackInfo ci) {
        merchantVillager$state.read(view);
    }

    /**
     * The single Brain integration point. It runs after vanilla villager Brain
     * processing and does not replace schedules, survival tasks, or activities.
     */
    @Inject(method = "mobTick", at = @At("TAIL"))
    private void merchantVillager$tick(ServerWorld world, CallbackInfo ci) {
        MerchantController.tick(world, (VillagerEntity)(Object)this, merchantVillager$state);
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void merchantVillager$dropCargo(DamageSource source, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity)(Object)this;
        if (villager.getEntityWorld() instanceof ServerWorld world) {
            if (merchantVillager$state.isPostIn(world)
                && merchantVillager$state.postPos() != null
                && world.getBlockEntity(merchantVillager$state.postPos()) instanceof MerchantPostBlockEntity post) {
                post.clearMerchant(villager.getUuid());
            }
            merchantVillager$state.dropCargoOnce(villager);
            ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
        }
    }
}
