package com.fluffybacon.merchantvillager.inventory;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Immediate rescan hint; the post's periodic validator also covers explosions and commands. */
public final class StorageMarkerEvents {
    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(OrphanedMarkerCleanupState::tick);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) ->
            OrphanedMarkerCleanupState.onChunkLoaded(world, chunk.getPos())
        );
        PlayerBlockBreakEvents.AFTER.register((world, player, brokenPos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld)
                || !(state.getBlock() instanceof AbstractSignBlock)
                    && !(state.getBlock() instanceof ChestBlock)) {
                return;
            }
            for (BlockPos candidate : BlockPos.iterate(
                brokenPos.add(-3, -3, -3), brokenPos.add(3, 3, 3)
            )) {
                if (serverWorld.getBlockEntity(candidate) instanceof MerchantPostBlockEntity post) {
                    post.onNearbyStorageBlockBroken(brokenPos);
                }
            }
        });
    }

    private StorageMarkerEvents() {
    }
}
