package com.fluffybacon.merchantvillager.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.util.DyeColor;

public final class MerchantClothingRenderState {
    public static final RenderStateDataKey<DyeColor> COLOR = RenderStateDataKey.create(
        () -> "Merchant Villager clothing color"
    );

    private MerchantClothingRenderState() {
    }
}
