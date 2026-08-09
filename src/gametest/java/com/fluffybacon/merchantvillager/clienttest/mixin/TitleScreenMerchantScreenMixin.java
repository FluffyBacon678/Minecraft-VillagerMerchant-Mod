package com.fluffybacon.merchantvillager.clienttest.mixin;

import com.fluffybacon.merchantvillager.screen.client.MerchantPostScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client GameTest renders the production handled screen from the title
 * screen, where no player exists. Vanilla's final HandledScreen#tick method is
 * the only rendering-path method that unconditionally dereferences the player.
 */
@Mixin(HandledScreen.class)
abstract class TitleScreenMerchantScreenMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void merchantVillager$allowTitleScreenHarness(CallbackInfo ci) {
        if ((Object)this instanceof MerchantPostScreen
            && MinecraftClient.getInstance().player == null) {
            ci.cancel();
        }
    }
}
