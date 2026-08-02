package com.fluffybacon.merchantvillager.trade;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradedItem;

public record OfferSnapshot(
    UUID targetUuid,
    String targetName,
    String profession,
    int villagerLevel,
    int offerIndex,
    TradedItem firstInput,
    Optional<TradedItem> secondInput,
    ItemStack output,
    int uses,
    int maxUses,
    double distanceSquared,
    boolean wanderingTrader,
    boolean targetAvailable,
    int despawnDelay,
    String fingerprint
) {
    public int remainingUses() {
        return Math.max(0, maxUses - uses);
    }

    public boolean isOutOfStock() {
        return uses >= maxUses;
    }

    public boolean accepts(ItemStack stack) {
        return firstInput.matches(stack) || secondInput.map(input -> input.matches(stack)).orElse(false);
    }
}
