package com.fluffybacon.merchantvillager.registry;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;

public final class ModScreenHandlers {
    public static final ExtendedScreenHandlerType<MerchantPostScreenHandler, BlockPos> MERCHANT_POST =
        Registry.register(
            Registries.SCREEN_HANDLER,
            MerchantVillagerMod.id("merchant_post"),
            new ExtendedScreenHandlerType<>(MerchantPostScreenHandler::new, BlockPos.PACKET_CODEC)
        );

    public static void initialize() {
    }

    private ModScreenHandlers() {
    }
}
