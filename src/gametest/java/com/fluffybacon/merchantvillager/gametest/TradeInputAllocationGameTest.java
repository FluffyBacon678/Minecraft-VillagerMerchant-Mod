package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantBatchPlanner;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

public final class TradeInputAllocationGameTest {
    @GameTest
    public void sharedInputsAreNotCountedTwice(TestContext context) {
        TradedItem first = new TradedItem(Items.PAPER, 3);
        TradedItem second = new TradedItem(Items.PAPER, 2);
        context.assertEquals(0, TradeInputMatcher.affordableExecutions(
            List.of(new ItemStack(Items.PAPER, 4)), first, 3, Optional.of(second), 2
        ), "Four paper cannot pay a three-plus-two paper trade");
        context.assertEquals(2, TradeInputMatcher.affordableExecutions(
            List.of(new ItemStack(Items.PAPER, 11)), first, 3, Optional.of(second), 2
        ), "Shared inputs must be divided by the combined cost");
        context.assertEquals(3, TradeInputMatcher.affordableExecutions(
            List.of(new ItemStack(Items.PAPER, 11)), first, 3, Optional.empty(), 0
        ), "One-input trades retain their normal affordability");
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(pos, MerchantPostBlockEntity.class);
        post.setStack(0, new ItemStack(Items.PAPER, 11));
        TradeOffer offer = new TradeOffer(first, Optional.of(second), new ItemStack(Items.EMERALD), 10, 1, 0.0F);
        MerchantWorkerState state = new MerchantWorkerState();
        var order = MerchantBatchPlanner.plan(post, state, UUID.randomUUID(),
            List.of(new MerchantBatchPlanner.Candidate("shared-test", 0, offer)), new SimpleInventory(27));
        context.assertEquals(2, order.orElseThrow().trades().getFirst().executions(), "Planner must respect the combined cost");
        context.assertTrue(MerchantBatchPlanner.reserve(post, state, order.orElseThrow()), "Shared batch must reserve exact payment");
        context.assertEquals(1, post.getStack(0).getCount(), "The unneeded paper must stay in storage");
        context.assertEquals(10, TradeInputMatcher.matchingCount(state.cargo(), first), "Cargo must contain both payments exactly once");
        context.complete();
    }

    @GameTest
    public void componentSpecificInputsSurvivePlanningReservationAndExecution(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(pos, MerchantPostBlockEntity.class);
        Text name = Text.literal("Reserved paper");
        ItemStack named = new ItemStack(Items.PAPER, 2);
        named.set(DataComponentTypes.CUSTOM_NAME, name);
        // The shared stack deliberately comes first: greedy extraction used to consume it.
        post.setStack(0, named);
        post.setStack(1, new ItemStack(Items.PAPER, 3));
        TradedItem first = new TradedItem(Items.PAPER, 3);
        TradedItem second = new TradedItem(Items.PAPER, 2)
            .withComponents(builder -> builder.add(DataComponentTypes.CUSTOM_NAME, name));
        MerchantWorkerState reorderedCargo = new MerchantWorkerState();
        reorderedCargo.add(named.copy(), false);
        reorderedCargo.add(new ItemStack(Items.PAPER, 3), false);
        context.assertTrue(reorderedCargo.executeCargoTrade(first, 3, Optional.of(second), 2, new ItemStack(Items.EMERALD)),
            "Shared cargo preceding exclusive cargo must not starve the component-specific input");
        TradeOffer offer = new TradeOffer(first, Optional.of(second), new ItemStack(Items.EMERALD), 10, 1, 0.0F);
        MerchantWorkerState state = new MerchantWorkerState();
        var order = MerchantBatchPlanner.plan(post, state, UUID.randomUUID(),
            List.of(new MerchantBatchPlanner.Candidate("allocation-test", 0, offer)), new SimpleInventory(27));
        context.assertTrue(order.isPresent(), "An affordable component-sensitive trade must produce a plan");
        context.assertEquals(1, order.orElseThrow().trades().getFirst().executions(), "Exactly one trade is affordable");
        context.assertTrue(MerchantBatchPlanner.reserve(post, state, order.orElseThrow()), "Reservation must preserve the narrow input");
        context.assertTrue(post.isEmpty(), "Both exact input stacks must leave storage");
        context.assertTrue(state.executeCargoTrade(first, 3, Optional.of(second), 2, new ItemStack(Items.EMERALD)),
            "Execution must preserve component-specific materials even if cargo order changes");
        context.assertEquals(1, TradeInputMatcher.matchingCount(state.cargo(), new TradedItem(Items.EMERALD)),
            "Exactly one emerald must replace five paper");
        context.assertEquals(0, TradeInputMatcher.matchingCount(state.cargo(), first), "No paper remains after payment");
        context.complete();
    }

    @GameTest
    public void insufficientSharedInputsLeaveCargoUntouched(TestContext context) {
        MerchantWorkerState state = new MerchantWorkerState();
        state.add(new ItemStack(Items.PAPER, 4), false);
        context.assertFalse(state.executeCargoTrade(new TradedItem(Items.PAPER, 3), 3,
            Optional.of(new TradedItem(Items.PAPER, 2)), 2, new ItemStack(Items.EMERALD)),
            "Shared-input underpayment must fail");
        context.assertEquals(4, TradeInputMatcher.matchingCount(state.cargo(), new TradedItem(Items.PAPER)),
            "A failed trade must not consume any input");
        context.assertEquals(0, TradeInputMatcher.matchingCount(state.cargo(), new TradedItem(Items.EMERALD)),
            "A failed trade must not mint rewards");
        context.complete();
    }
}
