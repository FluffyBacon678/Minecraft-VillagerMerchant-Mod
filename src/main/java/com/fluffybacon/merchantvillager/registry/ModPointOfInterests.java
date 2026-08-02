package com.fluffybacon.merchantvillager.registry;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.poi.PointOfInterestType;

public final class ModPointOfInterests {
    public static final RegistryKey<PointOfInterestType> MERCHANT_POST_KEY =
        RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, MerchantVillagerMod.id("merchant_post"));

    public static final PointOfInterestType MERCHANT_POST = PointOfInterestHelper.register(
        MerchantVillagerMod.id("merchant_post"),
        1,
        1,
        ModBlocks.MERCHANT_POST
    );

    public static void initialize() {
    }

    private ModPointOfInterests() {
    }
}
