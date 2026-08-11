package com.fluffybacon.merchantvillager.registry;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.blockentity.ChestRoleMarkerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<MerchantPostBlockEntity> MERCHANT_POST = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        MerchantVillagerMod.id("merchant_post"),
        FabricBlockEntityTypeBuilder.create(MerchantPostBlockEntity::new, ModBlocks.MERCHANT_POST).build()
    );

    public static final BlockEntityType<ChestRoleMarkerBlockEntity> CHEST_ROLE_MARKER = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        MerchantVillagerMod.id("chest_role_marker"),
        FabricBlockEntityTypeBuilder.create(
            ChestRoleMarkerBlockEntity::new,
            ModBlocks.CHEST_ROLE_MARKER
        ).build()
    );

    public static void initialize() {
    }

    private ModBlockEntities() {
    }
}
