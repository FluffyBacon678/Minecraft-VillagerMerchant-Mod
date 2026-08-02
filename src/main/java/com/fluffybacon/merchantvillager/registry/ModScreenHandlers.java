package com.fluffybacon.merchantvillager.registry;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
    public static final ScreenHandlerType<MerchantPostScreenHandler> MERCHANT_POST = Registry.register(
        Registries.SCREEN_HANDLER,
        MerchantVillagerMod.id("merchant_post"),
        new ScreenHandlerType<>(MerchantPostScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
    );

    public static void initialize() {
    }

    private ModScreenHandlers() {
    }
}
