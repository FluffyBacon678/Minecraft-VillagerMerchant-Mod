package com.fluffybacon.merchantvillager.mixin.client;

import com.fluffybacon.merchantvillager.client.MerchantClothingRenderState;
import com.fluffybacon.merchantvillager.merchant.MerchantClothing;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies synchronized entity clothing data into the frame-local render state. */
@Mixin(VillagerEntityRenderer.class)
public abstract class VillagerEntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void merchantVillager$copyClothingColor(
        VillagerEntity villager,
        VillagerEntityRenderState renderState,
        float tickProgress,
        CallbackInfo ci
    ) {
        DyeColor color = MerchantClothing.get(villager);
        renderState.setData(MerchantClothingRenderState.COLOR, color);
    }
}
