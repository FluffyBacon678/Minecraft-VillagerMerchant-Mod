package com.fluffybacon.merchantvillager.block;

import com.fluffybacon.merchantvillager.blockentity.ChestRoleMarkerBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/** A non-obtainable standing sign whose support is the assigned chest itself. */
public final class ChestRoleMarkerBlock extends SignBlock {
    public ChestRoleMarkerBlock(AbstractBlock.Settings settings) {
        super(WoodType.OAK, settings);
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock() instanceof ChestBlock;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChestRoleMarkerBlockEntity(pos, state);
    }
}
