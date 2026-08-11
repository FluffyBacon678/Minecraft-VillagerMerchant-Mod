package com.fluffybacon.merchantvillager.screen;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

/** A live, take-only view of the assigned Merchant villager's physical cargo. */
public final class MerchantCargoInventory implements Inventory {
    private final MerchantPostBlockEntity post;

    public MerchantCargoInventory(MerchantPostBlockEntity post) {
        this.post = post;
    }

    @Override
    public int size() {
        return MerchantPostScreenHandler.CARGO_SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        MerchantWorkerState state = state();
        return state == null || !state.hasCargo();
    }

    @Override
    public ItemStack getStack(int slot) {
        MerchantWorkerState state = state();
        return valid(slot) && state != null ? state.cargo().get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        MerchantWorkerState state = state();
        if (!valid(slot) || amount <= 0 || state == null) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = state.takeCargo(slot, amount);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        MerchantWorkerState state = state();
        if (!valid(slot) || state == null) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = state.cargo().get(slot);
        if (!removed.isEmpty()) {
            state.clearSlot(slot);
            markDirty();
        }
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        MerchantWorkerState state = state();
        if (!valid(slot) || state == null) {
            return;
        }
        if (stack.isEmpty()) {
            state.clearSlot(slot);
        } else {
            // Cargo slots are take-only. This branch is used only by vanilla
            // slot bookkeeping when it writes back a partially removed stack.
            state.cargo().set(slot, stack);
        }
        markDirty();
    }

    @Override
    public void markDirty() {
        post.onMerchantCargoChanged();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return post.canPlayerUse(player);
    }

    @Override
    public void clear() {
        MerchantWorkerState state = state();
        if (state == null) {
            return;
        }
        for (int slot = 0; slot < size(); slot++) {
            state.clearSlot(slot);
        }
        markDirty();
    }

    private MerchantWorkerState state() {
        if (!(post.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        Entity entity = post.getAssignedMerchant().map(world::getEntity).orElse(null);
        return entity instanceof MerchantWorker worker ? worker.merchantVillager$getState() : null;
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < size();
    }
}
