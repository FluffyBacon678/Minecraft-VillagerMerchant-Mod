package com.fluffybacon.merchantvillager.merchant;

import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradedItem;

public record MerchantTradePlan(
    String fingerprint,
    int offerIndex,
    int executions,
    TradedItem firstInput,
    int firstCount,
    Optional<TradedItem> secondInput,
    int secondCount,
    ItemStack output
) {
}
