package com.fluffybacon.merchantvillager.trade;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradedItem;

public final class TradeInputMatcher {
    public static boolean isAccepted(ItemStack stack, Collection<OfferSnapshot> offers) {
        if (stack.isEmpty()) {
            return false;
        }
        return offers.stream().anyMatch(offer -> offer.accepts(stack));
    }

    public static int matchingCount(Iterable<ItemStack> stacks, TradedItem input) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (input.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Counts affordable executions without counting shared inputs twice. */
    public static int affordableExecutions(
        Iterable<ItemStack> stacks,
        TradedItem first,
        int firstCost,
        Optional<TradedItem> second,
        int secondCost
    ) {
        if (firstCost <= 0 || (second.isPresent() && secondCost <= 0)) {
            return 0;
        }
        long firstAvailable = 0;
        long secondAvailable = 0;
        long combinedAvailable = 0;
        for (ItemStack stack : stacks) {
            boolean matchesFirst = first.matches(stack);
            boolean matchesSecond = second.isPresent() && second.get().matches(stack);
            if (matchesFirst) {
                firstAvailable += stack.getCount();
            }
            if (matchesSecond) {
                secondAvailable += stack.getCount();
            }
            if (matchesFirst || matchesSecond) {
                combinedAvailable += stack.getCount();
            }
        }
        long executions = firstAvailable / firstCost;
        if (second.isPresent()) {
            executions = Math.min(executions, secondAvailable / secondCost);
            executions = Math.min(executions, combinedAvailable / ((long)firstCost + secondCost));
        }
        return (int)Math.min(Integer.MAX_VALUE, executions);
    }

    private TradeInputMatcher() {
    }
}
