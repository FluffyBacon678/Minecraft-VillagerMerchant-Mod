package com.fluffybacon.merchantvillager.mixin;

import com.fluffybacon.merchantvillager.merchant.AutomatedTradeExperience;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderEntityMixin {
    @WrapOperation(
        method = "afterUsing",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
        )
    )
    private boolean merchantVillager$storeAutomatedTradeExperience(
        World world, Entity entity, Operation<Boolean> original
    ) {
        WanderingTraderEntity trader = (WanderingTraderEntity)(Object)this;
        return AutomatedTradeExperience.storeCapturedExperience(trader, entity)
            || original.call(world, entity);
    }
}
