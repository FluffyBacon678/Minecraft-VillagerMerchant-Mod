package com.fluffybacon.merchantvillager.mixin;

import com.fluffybacon.merchantvillager.inventory.ChestRoleMarker;
import java.util.List;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses loot only for role signs carrying this mod's persisted owner tag. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "getDroppedStacks", at = @At("HEAD"), cancellable = true)
    private void merchantVillager$suppressGeneratedMarkerLoot(
        LootWorldContext.Builder builder,
        CallbackInfoReturnable<List<ItemStack>> callback
    ) {
        if (builder.getOptional(LootContextParameters.BLOCK_ENTITY)
                instanceof SignBlockEntity sign
            && ChestRoleMarker.isGeneratedMarker(sign)) {
            callback.setReturnValue(List.of());
        }
    }
}
