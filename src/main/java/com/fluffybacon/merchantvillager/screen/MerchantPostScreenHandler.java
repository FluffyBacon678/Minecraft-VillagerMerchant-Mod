package com.fluffybacon.merchantvillager.screen;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public final class MerchantPostScreenHandler extends ScreenHandler {
    private static final int POST_SLOT_COUNT = 27;
    public static final int STORAGE_X = 153;
    public static final int STORAGE_Y = 34;
    public static final int PLAYER_INVENTORY_Y = 158;
    public static final int HOTBAR_Y = 216;
    private final Inventory postInventory;
    private final BlockPos postPos;

    public MerchantPostScreenHandler(
        int syncId, PlayerInventory playerInventory, BlockPos postPos
    ) {
        this(syncId, playerInventory, new SimpleInventory(POST_SLOT_COUNT), postPos);
    }

    public MerchantPostScreenHandler(int syncId, PlayerInventory playerInventory, MerchantPostBlockEntity post) {
        this(syncId, playerInventory, post, post.getPos());
    }

    private MerchantPostScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos postPos) {
        super(ModScreenHandlers.MERCHANT_POST, syncId);
        checkSize(inventory, POST_SLOT_COUNT);
        this.postInventory = inventory;
        this.postPos = postPos.toImmutable();
        inventory.onOpen(playerInventory.player);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                    inventory,
                    column + row * 9,
                    STORAGE_X + column * 18,
                    STORAGE_Y + row * 18
                ));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                    playerInventory,
                    column + row * 9 + 9,
                    STORAGE_X + column * 18,
                    PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, STORAGE_X + column * 18, HOTBAR_Y));
        }
    }

    public BlockPos getPostPos() {
        return postPos;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return postInventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (slotIndex < POST_SLOT_COUNT) {
            if (!insertItem(stack, POST_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!postInventory.isValid(0, stack) || !insertItem(stack, 0, POST_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (postInventory instanceof MerchantPostBlockEntity post) {
            post.notifyMaterialOrPermissionChange();
        }
        return original;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        postInventory.onClose(player);
    }
}
