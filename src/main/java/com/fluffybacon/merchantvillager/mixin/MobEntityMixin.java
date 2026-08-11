package com.fluffybacon.merchantvillager.mixin;

import com.fluffybacon.merchantvillager.merchant.SocialTradeTargetLock;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "tickNewAi", at = @At("TAIL"))
    private void merchantVillager$holdSocialTradeTarget(CallbackInfo ci) {
        MobEntity mob = (MobEntity)(Object)this;
        if (mob instanceof MerchantEntity target
            && mob.getEntityWorld() instanceof ServerWorld world) {
            SocialTradeTargetLock.enforce(world, target);
        }
    }
}
