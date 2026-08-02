package com.fluffybacon.merchantvillager.inventory;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class OutputChestFinder {
    public static Optional<BlockPos> find(
        ServerWorld world,
        BlockPos postPos,
        Iterable<ItemStack> rewards,
        VillagerEntity merchant
    ) {
        return Stream.of(Direction.values())
            .map(postPos::offset)
            .filter(candidate -> isTouchingPost(postPos, candidate))
            .filter(world::isChunkLoaded)
            .filter(candidate -> chestInventory(world, candidate) != null)
            .filter(candidate -> hasSpace(chestInventory(world, candidate), rewards))
            .filter(candidate -> {
                var path = merchant.getNavigation().findPathTo(candidate, 1);
                return path != null && path.reachesTarget();
            })
            .map(BlockPos::toImmutable)
            .min(Comparator
                .comparingDouble((BlockPos candidate) -> candidate.getSquaredDistance(postPos))
                .thenComparingLong(BlockPos::asLong));
    }

    public static boolean isTouchingPost(BlockPos postPos, BlockPos chestPos) {
        return postPos.getManhattanDistance(chestPos) == 1;
    }

    public static Inventory touchingChestInventory(
        ServerWorld world, BlockPos postPos, BlockPos chestPos
    ) {
        return chestPos != null && isTouchingPost(postPos, chestPos)
            ? chestInventory(world, chestPos)
            : null;
    }

    /**
     * Returns how many copies of {@code stack} the current chest inventory can
     * still accept, accounting for both partial and empty slots.
     */
    public static int availableSpace(Inventory inventory, ItemStack stack) {
        if (inventory == null || stack.isEmpty()) {
            return 0;
        }
        long available = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack present = inventory.getStack(slot);
            if (present.isEmpty()) {
                available += Math.min(inventory.getMaxCount(stack), stack.getMaxCount());
            } else if (ItemStack.areItemsAndComponentsEqual(present, stack)) {
                available += Math.max(0, inventory.getMaxCount(present) - present.getCount());
            }
        }
        return (int)Math.min(Integer.MAX_VALUE, available);
    }

    public static Inventory chestInventory(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest)) {
            return null;
        }
        return ChestBlock.getInventory(chest, state, world, pos, true);
    }

    public static boolean hasSpace(Inventory inventory, Iterable<ItemStack> stacks) {
        if (inventory == null) {
            return false;
        }
        for (ItemStack incoming : stacks) {
            if (incoming.isEmpty()) {
                continue;
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack present = inventory.getStack(slot);
                if (present.isEmpty()
                    || (ItemStack.areItemsAndComponentsEqual(present, incoming)
                        && present.getCount() < inventory.getMaxCount(present))) {
                    return true;
                }
            }
        }
        return false;
    }

    private OutputChestFinder() {
    }
}
