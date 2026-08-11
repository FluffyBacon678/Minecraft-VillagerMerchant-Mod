package com.fluffybacon.merchantvillager.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Remap-safe marker for audited vanilla factories whose output depends on RNG. */
@Mixin(targets = {
    "net.minecraft.village.TradeOffers$EnchantBookFactory",
    "net.minecraft.village.TradeOffers$SellDyedArmorFactory",
    "net.minecraft.village.TradeOffers$SellEnchantedToolFactory",
    "net.minecraft.village.TradeOffers$SellPotionHoldingItemFactory"
})
public interface RandomizedVanillaTradeFactory {
}
