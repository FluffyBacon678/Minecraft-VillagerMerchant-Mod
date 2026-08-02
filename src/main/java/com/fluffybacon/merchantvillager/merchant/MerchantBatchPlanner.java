package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

/**
 * Greedily builds one conservative, server-owned visit for a single target.
 * The planner reserves inputs for several enabled offers while ensuring that
 * all departure inputs plus all eventual rewards fit in the nine cargo slots.
 * That conservative bound guarantees every intermediate execution also fits.
 */
public final class MerchantBatchPlanner {
    public static Optional<MerchantWorkOrder> plan(
        MerchantPostBlockEntity post,
        MerchantWorkerState state,
        UUID targetUuid,
        List<Candidate> candidates,
        Inventory outputChest
    ) {
        if (state.hasCargo() || candidates.isEmpty() || outputChest == null) {
            return Optional.empty();
        }

        List<ItemStack> remainingInputs = post.copyInventory();
        List<ItemStack> reservedInputs = new ArrayList<>();
        List<ItemStack> expectedOutputs = new ArrayList<>();
        List<MerchantTradePlan> trades = new ArrayList<>();

        for (Candidate candidate : candidates) {
            TradeOffer offer = candidate.offer();
            if (offer.isDisabled()) {
                continue;
            }
            int possible = possibleExecutions(remainingInputs, offer);
            while (possible > 0) {
                List<ItemStack> trialRemaining = copyStacks(remainingInputs);
                List<ItemStack> trialReserved = copyStacks(reservedInputs);
                List<ItemStack> trialOutputs = copyStacks(expectedOutputs);
                if (reserveInto(
                        trialRemaining,
                        offer.getFirstBuyItem(),
                        offer.getDisplayedFirstBuyItem().getCount() * possible,
                        trialReserved
                    )
                    && (offer.getSecondBuyItem().isEmpty()
                        || reserveInto(
                            trialRemaining,
                            offer.getSecondBuyItem().get(),
                            offer.getDisplayedSecondBuyItem().getCount() * possible,
                            trialReserved
                        ))
                    && addRepeated(trialOutputs, offer.copySellItem(), possible)
                    && occupiedSlots(trialReserved) + occupiedSlots(trialOutputs)
                        <= MerchantVillagerConfig.CARGO_SIZE
                    && outputCanFit(outputChest, trialOutputs)) {
                    remainingInputs = trialRemaining;
                    reservedInputs = trialReserved;
                    expectedOutputs = trialOutputs;
                    trades.add(new MerchantTradePlan(
                        candidate.fingerprint(),
                        candidate.offerIndex(),
                        possible,
                        offer.getFirstBuyItem(),
                        offer.getDisplayedFirstBuyItem().getCount(),
                        offer.getSecondBuyItem(),
                        offer.getDisplayedSecondBuyItem().isEmpty()
                            ? 0
                            : offer.getDisplayedSecondBuyItem().getCount(),
                        offer.copySellItem()
                    ));
                    break;
                }
                possible--;
            }
        }

        return trades.isEmpty()
            ? Optional.empty()
            : Optional.of(new MerchantWorkOrder(targetUuid, trades));
    }

    private static int possibleExecutions(List<ItemStack> inventory, TradeOffer offer) {
        int possible = TradeInputMatcher.matchingCount(inventory, offer.getFirstBuyItem())
            / Math.max(1, offer.getDisplayedFirstBuyItem().getCount());
        if (offer.getSecondBuyItem().isPresent()) {
            possible = Math.min(
                possible,
                TradeInputMatcher.matchingCount(inventory, offer.getSecondBuyItem().get())
                    / Math.max(1, offer.getDisplayedSecondBuyItem().getCount())
            );
        }
        return Math.max(0, Math.min(possible, offer.getMaxUses() - offer.getUses()));
    }

    public static boolean reserve(
        MerchantPostBlockEntity post, MerchantWorkerState state, MerchantWorkOrder order
    ) {
        List<ItemStack> simulated = post.copyInventory();
        List<ItemStack> plannedCargo = new ArrayList<>();
        for (MerchantTradePlan trade : order.trades()) {
            if (!reserveInto(
                    simulated,
                    trade.firstInput(),
                    trade.firstCount() * trade.executions(),
                    plannedCargo
                )
                || (trade.secondInput().isPresent()
                    && !reserveInto(
                        simulated,
                        trade.secondInput().get(),
                        trade.secondCount() * trade.executions(),
                        plannedCargo
                    ))) {
                return false;
            }
        }
        if (!state.canLoadInputs(plannedCargo)) {
            return false;
        }

        List<ItemStack> extracted = new ArrayList<>();
        for (MerchantTradePlan trade : order.trades()) {
            extract(
                post,
                trade.firstInput(),
                trade.firstCount() * trade.executions(),
                extracted
            );
            if (trade.secondInput().isPresent()) {
                extract(
                    post,
                    trade.secondInput().get(),
                    trade.secondCount() * trade.executions(),
                    extracted
                );
            }
        }
        if (!state.loadInputs(extracted)) {
            extracted.forEach(post::insertReturnedInput);
            return false;
        }
        post.markDirty();
        return true;
    }

    private static boolean reserveInto(
        List<ItemStack> inventory,
        TradedItem input,
        int count,
        List<ItemStack> extracted
    ) {
        int remaining = count;
        for (ItemStack stack : inventory) {
            if (input.matches(stack)) {
                int moved = Math.min(remaining, stack.getCount());
                merge(extracted, stack.copyWithCount(moved));
                stack.decrement(moved);
                remaining -= moved;
                if (remaining == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void extract(
        MerchantPostBlockEntity post, TradedItem input, int count, List<ItemStack> extracted
    ) {
        int remaining = count;
        for (int slot = 0; slot < post.size() && remaining > 0; slot++) {
            ItemStack present = post.getStack(slot);
            if (input.matches(present)) {
                int moved = Math.min(remaining, present.getCount());
                merge(extracted, post.removeStack(slot, moved));
                remaining -= moved;
            }
        }
    }

    private static boolean addRepeated(List<ItemStack> stacks, ItemStack output, int executions) {
        long total = (long)output.getCount() * executions;
        while (total > 0L) {
            int moved = (int)Math.min(total, output.getMaxCount());
            merge(stacks, output.copyWithCount(moved));
            total -= moved;
            if (occupiedSlots(stacks) > MerchantVillagerConfig.CARGO_SIZE) {
                return false;
            }
        }
        return true;
    }

    private static void merge(List<ItemStack> stacks, ItemStack incoming) {
        for (ItemStack present : stacks) {
            if (!incoming.isEmpty() && ItemStack.areItemsAndComponentsEqual(present, incoming)) {
                int moved = Math.min(incoming.getCount(), present.getMaxCount() - present.getCount());
                present.increment(moved);
                incoming.decrement(moved);
            }
        }
        while (!incoming.isEmpty()) {
            int moved = Math.min(incoming.getCount(), incoming.getMaxCount());
            stacks.add(incoming.copyWithCount(moved));
            incoming.decrement(moved);
        }
    }

    private static int occupiedSlots(List<ItemStack> stacks) {
        return (int)stacks.stream().filter(stack -> !stack.isEmpty()).count();
    }

    private static boolean outputCanFit(Inventory chest, List<ItemStack> outputs) {
        List<ItemStack> inventory = new ArrayList<>(chest.size());
        for (int slot = 0; slot < chest.size(); slot++) {
            inventory.add(chest.getStack(slot).copy());
        }
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
                ItemStack present = inventory.get(slot);
                if (!present.isEmpty() && ItemStack.areItemsAndComponentsEqual(present, remaining)) {
                    int limit = Math.min(chest.getMaxCount(present), present.getMaxCount());
                    int moved = Math.min(remaining.getCount(), Math.max(0, limit - present.getCount()));
                    present.increment(moved);
                    remaining.decrement(moved);
                }
            }
            for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
                if (inventory.get(slot).isEmpty()) {
                    int moved = Math.min(
                        remaining.getCount(),
                        Math.min(chest.getMaxCount(remaining), remaining.getMaxCount())
                    );
                    inventory.set(slot, remaining.copyWithCount(moved));
                    remaining.decrement(moved);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public record Candidate(String fingerprint, int offerIndex, TradeOffer offer) {
    }

    private MerchantBatchPlanner() {
    }
}
