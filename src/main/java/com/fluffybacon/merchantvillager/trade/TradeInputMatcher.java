package com.fluffybacon.merchantvillager.trade;

import java.util.Collection;
import net.minecraft.item.ItemStack;

public final class TradeInputMatcher {
    public static boolean isAccepted(ItemStack stack, Collection<OfferSnapshot> offers) {
        if (stack.isEmpty()) {
            return false;
        }
        return offers.stream().anyMatch(offer -> offer.accepts(stack));
    }

    public static int matchingCount(Iterable<ItemStack> stacks, net.minecraft.village.TradedItem input) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (input.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private TradeInputMatcher() {
    }
}
