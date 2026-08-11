package com.fluffybacon.merchantvillager.inventory;

import java.util.Optional;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class OutputChestFinder {
    public static Optional<BlockPos> find(
        ServerWorld world,
        BlockPos postPos,
        Iterable<ItemStack> rewards
    ) {
        return AdjacentChestManager.scan(world, postPos).stream()
            .filter(candidate -> AdjacentChestManager.resolveInventory(world, postPos, candidate)
                .filter(inventory -> hasSpace(inventory, rewards))
                .isPresent())
            .map(AdjacentChestManager.Candidate::accessPos)
            .findFirst();
    }

    /**
     * Compatibility overload for the existing controller. Chest validity is
     * structural; the merchant's ability to path to a solid chest block no
     * longer decides whether directly touching storage is safe to use.
     */
    public static Optional<BlockPos> find(
        ServerWorld world,
        BlockPos postPos,
        Iterable<ItemStack> rewards,
        VillagerEntity merchant
    ) {
        return find(world, postPos, rewards);
    }

    public static boolean isTouchingPost(BlockPos postPos, BlockPos chestPos) {
        return AdjacentChestManager.isDirectFaceNeighbor(postPos, chestPos);
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
        return AdjacentChestManager.chestInventory(world, pos);
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
