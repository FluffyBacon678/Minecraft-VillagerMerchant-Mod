package com.fluffybacon.merchantvillager.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Remap-safe marker for audited vanilla factories that never consume RNG. */
@Mixin(targets = {
    "net.minecraft.village.TradeOffers$BuyItemFactory",
    "net.minecraft.village.TradeOffers$EmptyFactory",
    "net.minecraft.village.TradeOffers$SellSuspiciousStewFactory",
    "net.minecraft.village.TradeOffers$TypeAwareBuyForOneEmeraldFactory"
})
public interface DeterministicVanillaTradeFactory {
}
