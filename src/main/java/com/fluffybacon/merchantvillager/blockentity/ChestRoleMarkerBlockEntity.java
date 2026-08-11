package com.fluffybacon.merchantvillager.blockentity;

import com.fluffybacon.merchantvillager.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;

/** Uses vanilla sign text/storage with the marker's dedicated block-entity type. */
public final class ChestRoleMarkerBlockEntity extends SignBlockEntity {
    public ChestRoleMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHEST_ROLE_MARKER, pos, state);
    }
}
