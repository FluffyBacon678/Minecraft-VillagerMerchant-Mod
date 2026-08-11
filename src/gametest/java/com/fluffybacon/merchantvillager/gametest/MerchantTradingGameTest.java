package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.inventory.OutputChestFinder;
import com.fluffybacon.merchantvillager.merchant.MerchantController;
import com.fluffybacon.merchantvillager.merchant.MerchantTradeExecutor;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.merchant.MerchantState;
import com.fluffybacon.merchantvillager.merchant.SocialTradeTargetLock;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.registry.ModPointOfInterests;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.inventory.Inventory;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.test.TestContext;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

public final class MerchantTradingGameTest {
    @GameTest
    public void socialTargetLockSelfReleasesWhenWorkerDisappears(TestContext context) {
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 1));
        VillagerEntity target = spawnVillager(context, new BlockPos(2, 1, 1));
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        state.target(target.getUuid(), 0, "");
        state.enter(MerchantState.TRADING_BUSY, "Testing orphaned social lock");

        context.assertTrue(
            SocialTradeTargetLock.acquireOrRefresh(context.getWorld(), worker, target),
            "Regression setup must acquire the target-side social lock"
        );
        worker.discard();
        SocialTradeTargetLock.enforce(context.getWorld(), target);

        context.assertFalse(
            SocialTradeTargetLock.isLockedBy(
                context.getWorld().getServer(), target.getUuid(), worker.getUuid()
            ),
            "A disappearing worker must not leave a stale target lock"
        );
        context.assertFalse(target.isAiDisabled(), "An orphaned social lock must never persist NoAI");
        context.complete();
    }

    @GameTest(maxTicks = 100)
    public void tradingBusyWaitsBeforeCompletingTheInventorySwap(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(postPos.east(), Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(2, 1, 2));
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        var snapshot = post.getOffers().stream()
            .filter(candidate -> candidate.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow();
        post.setOfferEnabledInternal(snapshot.fingerprint(), true);
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        context.assertTrue(state.add(new ItemStack(Items.PAPER), false), "Input cargo must load");
        state.target(target.getUuid(), snapshot.offerIndex(), snapshot.fingerprint());
        state.plannedExecutions(1);
        state.beginTradingInteraction(MerchantVillagerConfig.MIN_TRADE_INTERACTION_TICKS);
        state.enter(MerchantState.TRADING_BUSY, "Testing social trade phase");
        Vec3d targetStart = target.getEntityPos();
        target.getNavigation().startMovingTo(
            target.getX() + 3.0, target.getY(), target.getZ(), 1.0
        );
        target.setVelocity(0.35, 0.0, 0.0);

        context.runAtTick(20, () -> {
            context.assertEquals(0, offer.getUses(), "No item swap may occur during the greeting");
            context.assertEquals(MerchantState.TRADING_BUSY, state.state(), "Greeting must remain active");
            context.assertTrue(worker.getNavigation().isIdle(), "Merchant must stop and face the target");
            context.assertTrue(target.getNavigation().isIdle(), "Normal target AI must remain held");
            context.assertFalse(target.isAiDisabled(), "Social holding must never toggle persistent NoAI");
            context.assertTrue(
                SocialTradeTargetLock.isLockedBy(
                    context.getWorld().getServer(), target.getUuid(), worker.getUuid()
                ),
                "The target must carry the worker's short-lived social lock"
            );
            context.assertTrue(
                target.getEntityPos().squaredDistanceTo(targetStart) < 0.5,
                "A target whose normal AI tried to walk away must remain at the conversation"
            );
        });

        context.runAtTick(70, () -> {
            context.assertEquals(1, offer.getUses(), "The real offer must execute after the greeting");
            context.assertFalse(target.isAiDisabled(), "Completing a trade must leave target AI enabled");
            context.assertFalse(
                SocialTradeTargetLock.isLockedBy(
                    context.getWorld().getServer(), target.getUuid(), worker.getUuid()
                ),
                "Completing a trade must release the target-side social lock"
            );
            context.complete();
        });
    }

    @GameTest
    public void savedRemoteChestIsRejectedBeforeDeposit(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos remoteChestPos = new BlockPos(3, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(remoteChestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        context.assertTrue(state.add(new ItemStack(Items.EMERALD), true), "Reward cargo must load");
        state.outputChest(context.getAbsolutePos(remoteChestPos));
        state.enter(MerchantState.DEPOSITING_REWARDS, "Testing saved output chest validation");

        MerchantController.tick(context.getWorld(), worker, state);

        context.assertEquals(
            0,
            context.getBlockEntity(remoteChestPos, ChestBlockEntity.class).count(Items.EMERALD),
            "An output chest saved by an older build must not receive cargo when it no longer touches"
        );
        context.assertTrue(state.hasRewards(), "Rejected remote output must preserve reward cargo");
        context.assertEquals(
            MerchantState.FINDING_OUTPUT_CHEST,
            state.state(),
            "Merchant must search again under the current touching-chest rule"
        );
        context.complete();
    }

    @GameTest(maxTicks = 300, skyAccess = true)
    public void nonTouchingChestIsIgnoredAndTradeDoesNotStart(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos nearbyChestPos = new BlockPos(3, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(nearbyChestPos, Blocks.CHEST);
        context.assertFalse(
            OutputChestFinder.isTouchingPost(postPos, nearbyChestPos),
            "Regression chest must be nearby without touching the post"
        );

        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            // Keep this signature unique across the shared GameTest world.
            // Global approval is intentionally keyed by exact trade rather
            // than fixture UUID, so a duplicate from another nearby test
            // could otherwise make that fixture consume these materials.
            new TradedItem(Items.NAUTILUS_SHELL, 1),
            Optional.empty(),
            new ItemStack(Items.DRAGON_BREATH),
            7,
            3,
            0.07F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);

        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.PAPER, 3));

        context.runAtTick(250, () -> {
            context.assertEquals(0, offer.getUses(), "A nearby player chest must never authorize a trade");
            context.assertEquals(3, post.count(Items.PAPER), "Inputs must remain safely in the post");
            context.assertEquals(
                0,
                context.getBlockEntity(nearbyChestPos, ChestBlockEntity.class).count(Items.EMERALD),
                "A non-touching chest must receive nothing"
            );
            context.assertFalse(
                ((MerchantWorker)worker).merchantVillager$getState().hasCargo(),
                "No items may be reserved without a touching output chest"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 600, skyAccess = true)
    public void touchingHalfOfDoubleChestUsesBothHalves(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos touchingHalf = new BlockPos(2, 1, 1);
        BlockPos connectedHalf = new BlockPos(3, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(
            touchingHalf,
            Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.LEFT)
        );
        context.setBlockState(
            connectedHalf,
            Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.RIGHT)
        );
        ChestBlockEntity touchingInventory = context.getBlockEntity(touchingHalf, ChestBlockEntity.class);
        for (int slot = 0; slot < touchingInventory.size(); slot++) {
            touchingInventory.setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
        }

        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.POISONOUS_POTATO, 17),
            Optional.empty(),
            new ItemStack(Items.DRAGON_BREATH),
            2,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.POISONOUS_POTATO, 17));

        context.runAtTick(500, () -> {
            Inventory combined = OutputChestFinder.chestInventory(
                context.getWorld(),
                context.getAbsolutePos(touchingHalf)
            );
            context.assertEquals(54, combined.size(), "Touching double chest must expose all 54 slots");
            context.assertEquals(1, offer.getUses(), "Trade must execute through the touching chest half");
            context.assertEquals(1, combined.count(Items.DRAGON_BREATH), "Reward must use the connected empty half");
            context.assertEquals(
                0,
                touchingInventory.count(Items.DRAGON_BREATH),
                "The deliberately full touching half cannot accept the reward"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 1100, skyAccess = true)
    public void physicalPaperTradesDeliverEmeraldsToTouchingChest(TestContext context) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos postRelative = new BlockPos(1, 1, 1);
        BlockPos chestRelative = new BlockPos(2, 1, 1);
        BlockPos workerRelative = new BlockPos(1, 1, 2);
        BlockPos targetRelative = new BlockPos(5, 1, 2);
        context.setBlockState(postRelative, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestRelative, Blocks.CHEST);
        context.assertEquals(
            1,
            postRelative.getManhattanDistance(chestRelative),
            "The output chest must be directly touching the Merchant's Post"
        );

        VillagerEntity worker = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(workerRelative),
            SpawnReason.COMMAND
        );
        VillagerEntity target = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(targetRelative),
            SpawnReason.COMMAND
        );
        target.setAiDisabled(true);
        target.getBrain().doExclusively(Activity.IDLE);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);

        prepareWorker(context, worker, postRelative);

        MerchantPostBlockEntity post = context.getBlockEntity(postRelative, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        var targetOffers = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .toList();
        context.assertEquals(1, targetOffers.size(), "The real target offer must be discovered");
        String fingerprint = targetOffers.getFirst().fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.PAPER, 3));
        boolean[] sawVisibleRewardReview = {false};
        boolean[] sawDeliveryReceipt = {false};
        context.runAtEveryTick(() -> {
            MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
            if (state.state() == MerchantState.REVIEWING_REWARDS
                && state.cargo().stream().anyMatch(stack -> stack.isOf(Items.EMERALD))) {
                sawVisibleRewardReview[0] = true;
            }
            if (state.status().startsWith("Delivered 3 Emerald")) {
                sawDeliveryReceipt[0] = true;
            }
        });

        context.runAtTick(1000, () -> {
            ChestBlockEntity chest = context.getBlockEntity(chestRelative, ChestBlockEntity.class);
            var state = ((MerchantWorker)worker).merchantVillager$getState();
            String diagnostics = "state=" + state.state()
                + ", profession=" + worker.getVillagerData().profession().getIdAsString()
                + ", jobSite=" + worker.getBrain()
                    .getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE)
                    .map(Object::toString)
                    .orElse("<none>")
                + ", status=" + state.status()
                + ", failure=" + state.lastFailure()
                + ", planned=" + state.plannedExecutions()
                + ", completed=" + state.completedExecutions()
                + ", uses=" + offer.getUses()
                + ", paper=" + post.count(Items.PAPER)
                + ", hasCargo=" + state.hasCargo();
            context.assertEquals(
                3,
                chest.count(Items.EMERALD),
                "Three real rewards must reach the output chest (" + diagnostics + ")"
            );
            context.assertEquals(3, offer.getUses(), "The target offer use count must increment exactly once per trade");
            context.assertTrue(post.getStack(0).isEmpty(), "Reserved input must be consumed exactly once");
            context.assertFalse(
                state.hasCargo(),
                "Worker cargo must be empty after delivery"
            );
            context.assertTrue(
                sawVisibleRewardReview[0],
                "Completed emeralds must remain visibly in Merchant Cargo before delivery"
            );
            context.assertTrue(
                sawDeliveryReceipt[0],
                "Idle telemetry must show a visible Export delivery receipt before resuming work"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 600, skyAccess = true)
    public void multipleTradesDeliverRewardsToTouchingChestInOneTrip(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        context.assertEquals(
            1,
            postPos.getManhattanDistance(chestPos),
            "The output chest must be directly touching the Merchant's Post"
        );

        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer paper = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        TradeOffer wheat = new TradeOffer(
            new TradedItem(Items.WHEAT, 1),
            Optional.empty(),
            new ItemStack(Items.BREAD, 2),
            8,
            1,
            0.05F
        );
        target.getOffers().add(paper);
        target.getOffers().add(wheat);
        prepareWorker(context, worker, postPos);

        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        var targetOffers = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .toList();
        context.assertEquals(2, targetOffers.size(), "Both real offers must be discovered");
        targetOffers.forEach(offer -> post.setOfferEnabledInternal(offer.fingerprint(), true));
        post.setStack(0, new ItemStack(Items.PAPER, 2));
        post.setStack(1, new ItemStack(Items.WHEAT, 2));

        context.runAtTick(500, () -> {
            ChestBlockEntity chest = context.getBlockEntity(chestPos, ChestBlockEntity.class);
            context.assertEquals(2, chest.count(Items.EMERALD), "Paper rewards must be delivered");
            context.assertEquals(4, chest.count(Items.BREAD), "Wheat rewards must be delivered");
            context.assertEquals(2, paper.getUses(), "First offer must execute twice");
            context.assertEquals(2, wheat.getUses(), "Second offer must execute twice in the same target batch");
            context.assertFalse(
                ((MerchantWorker)worker).merchantVillager$getState().hasCargo(),
                "Multi-offer batch cargo must be fully delivered"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 600, skyAccess = true)
    public void physicalTwoInputTradeConsumesBothInputs(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);

        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.EMERALD, 2),
            Optional.of(new TradedItem(Items.BOOK, 1)),
            new ItemStack(Items.BOOKSHELF),
            4,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);

        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.EMERALD, 4));
        post.setStack(1, new ItemStack(Items.BOOK, 2));

        context.runAtTick(500, () -> {
            ChestBlockEntity chest = context.getBlockEntity(chestPos, ChestBlockEntity.class);
            context.assertEquals(2, chest.count(Items.BOOKSHELF), "Two-input rewards must be delivered");
            context.assertEquals(2, offer.getUses(), "Two-input offer uses must increment exactly twice");
            context.assertEquals(0, post.count(Items.EMERALD), "Exact first inputs must be consumed");
            context.assertEquals(0, post.count(Items.BOOK), "Exact second inputs must be consumed");
            context.complete();
        });
    }

    @GameTest(maxTicks = 300, skyAccess = true)
    public void disabledOfferNeverConsumesMaterials(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);

        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.BLAZE_POWDER, 11),
            Optional.empty(),
            new ItemStack(Items.GHAST_TEAR),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);

        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        context.assertFalse(
            post.isEnabled(fingerprint),
            "Newly discovered offers must default to X"
        );
        post.setStack(0, new ItemStack(Items.BLAZE_POWDER, 33));

        context.runAtTick(250, () -> {
            ChestBlockEntity chest = context.getBlockEntity(chestPos, ChestBlockEntity.class);
            context.assertEquals(0, chest.count(Items.GHAST_TEAR), "Disabled trade must create no reward");
            context.assertEquals(0, offer.getUses(), "Disabled trade must not increment uses");
            context.assertEquals(33, post.count(Items.BLAZE_POWDER), "Disabled trade must not consume materials");
            context.complete();
        });
    }

    @GameTest(maxTicks = 300, skyAccess = true)
    public void outOfStockOfferNeverExecutes(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.FERMENTED_SPIDER_EYE, 13),
            Optional.empty(),
            new ItemStack(Items.PHANTOM_MEMBRANE),
            1,
            1,
            0.05F
        );
        offer.use();
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.FERMENTED_SPIDER_EYE, 39));

        context.runAtTick(250, () -> {
            context.assertEquals(1, offer.getUses(), "Out-of-stock offer use count must not change");
            context.assertEquals(39, post.count(Items.FERMENTED_SPIDER_EYE), "Out-of-stock offer must consume no material");
            context.assertEquals(
                0,
                context.getBlockEntity(chestPos, ChestBlockEntity.class).count(Items.PHANTOM_MEMBRANE),
                "Out-of-stock offer must create no reward"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 500, skyAccess = true)
    public void lastRemainingUseExecutesExactlyOnce(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PRISMARINE_CRYSTALS, 1),
            Optional.empty(),
            new ItemStack(Items.SHULKER_SHELL),
            2,
            1,
            0.05F
        );
        offer.use();
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.PRISMARINE_CRYSTALS, 2));

        context.runAtTick(450, () -> {
            context.assertEquals(2, offer.getUses(), "Only the final available use must execute");
            context.assertEquals(1, post.count(Items.PRISMARINE_CRYSTALS), "Unused reserved input must return to the post");
            context.assertEquals(
                1,
                context.getBlockEntity(chestPos, ChestBlockEntity.class).count(Items.SHULKER_SHELL),
                "The last available use must produce exactly one reward"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 600, skyAccess = true)
    public void wanderingTraderOfferUsesThePhysicalLoop(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(2, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        WanderingTraderEntity target = context.spawnEntity(
            EntityType.WANDERING_TRADER,
            Vec3d.ofBottomCenter(new BlockPos(5, 1, 2)),
            SpawnReason.COMMAND
        );
        target.setAiDisabled(true);
        target.setDespawnDelay(1200);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.EMERALD, 1),
            Optional.empty(),
            new ItemStack(Items.OAK_SAPLING),
            4,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        var snapshot = post.getOffers().stream()
            .filter(candidate -> candidate.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow();
        context.assertTrue(snapshot.wanderingTrader(), "Catalogue must mark the temporary target distinctly");
        post.setOfferEnabledInternal(snapshot.fingerprint(), true);
        post.setStack(0, new ItemStack(Items.EMERALD, 2));

        context.runAtTick(500, () -> {
            context.assertEquals(2, offer.getUses(), "Wandering Trader offer must execute twice");
            context.assertEquals(
                2,
                context.getBlockEntity(chestPos, ChestBlockEntity.class).count(Items.OAK_SAPLING),
                "Wandering Trader rewards must reach the output chest"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 300, skyAccess = true)
    public void fullOutputChestPreventsDeparture(TestContext context) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, 29, z), Blocks.STONE);
            }
        }
        BlockPos postPos = new BlockPos(1, 30, 1);
        BlockPos chestPos = new BlockPos(2, 30, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(chestPos, ChestBlockEntity.class);
        for (int slot = 0; slot < chest.size(); slot++) {
            chest.setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 30, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 30, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.PAPER, 3));

        context.runAtTick(250, () -> {
            context.assertEquals(0, offer.getUses(), "No trade may begin without output capacity");
            context.assertEquals(3, post.count(Items.PAPER), "Inputs must remain in the post");
            context.assertFalse(
                ((MerchantWorker)worker).merchantVillager$getState().hasCargo(),
                "Worker must not reserve cargo without a valid output chest"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 600, skyAccess = true)
    public void blockedPreferredOutputDoesNotStarveAnotherFittingTrade(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = postPos.east();
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(5, 1, 2));
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer paper = new TradeOffer(
            new TradedItem(Items.MAGMA_CREAM),
            Optional.empty(),
            new ItemStack(Items.ENDER_PEARL),
            8,
            1,
            0.05F
        );
        TradeOffer wheat = new TradeOffer(
            new TradedItem(Items.RABBIT_FOOT),
            Optional.empty(),
            new ItemStack(Items.GLOWSTONE_DUST, 2),
            8,
            1,
            0.05F
        );
        target.getOffers().add(paper);
        target.getOffers().add(wheat);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        var snapshots = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .sorted(java.util.Comparator.comparing(snapshot -> snapshot.fingerprint()))
            .toList();
        context.assertEquals(2, snapshots.size(), "Both candidate trades must be discovered");
        snapshots.forEach(snapshot -> post.setOfferEnabledInternal(snapshot.fingerprint(), true));

        var blocked = snapshots.get(0);
        var fitting = snapshots.get(1);
        TradeOffer blockedOffer = target.getOffers().get(blocked.offerIndex());
        TradeOffer fittingOffer = target.getOffers().get(fitting.offerIndex());
        ChestBlockEntity chest = context.getBlockEntity(chestPos, ChestBlockEntity.class);
        for (int slot = 0; slot < chest.size(); slot++) {
            chest.setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        ItemStack fittingOutput = fitting.output();
        chest.setStack(
            0,
            fittingOutput.copyWithCount(fittingOutput.getMaxCount() - fittingOutput.getCount())
        );
        post.setStack(0, new ItemStack(Items.MAGMA_CREAM));
        post.setStack(1, new ItemStack(Items.RABBIT_FOOT));

        context.runAtTick(500, () -> {
            context.assertEquals(
                0,
                blockedOffer.getUses(),
                "The output with no compatible space must remain unexecuted"
            );
            context.assertEquals(
                1,
                fittingOffer.getUses(),
                "A later approved offer that fits Export must still execute"
            );
            context.assertFalse(
                ((MerchantWorker)worker).merchantVillager$getState().hasCargo(),
                "The fitting reward must finish delivery"
            );
            context.complete();
        });
    }

    @GameTest
    public void tradeExecutorRejectsRemoteExecution(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(7, 1, 2));
        worker.setAiDisabled(true);
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.refreshCatalogue(true);
        var snapshot = post.getOffers().stream()
            .filter(candidate -> candidate.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow();
        post.setOfferEnabledInternal(snapshot.fingerprint(), true);
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        state.target(target.getUuid(), snapshot.offerIndex(), snapshot.fingerprint());
        state.plannedExecutions(1);
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.PAPER))),
            "Test setup must load one real input"
        );

        boolean executed = MerchantTradeExecutor.executeOne(
            context.getWorld(),
            worker,
            target,
            state,
            post
        );
        context.assertFalse(executed, "A real offer must not execute outside interaction distance");
        context.assertEquals(0, offer.getUses(), "Rejected remote execution must not increment uses");
        context.assertTrue(state.hasInputs(), "Rejected remote execution must preserve input cargo");
        context.assertFalse(state.hasRewards(), "Rejected remote execution must create no output");
        context.complete();
    }

    @GameTest
    public void tradeExecutorRejectsExecutionThroughSolidWall(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 2, 2), Blocks.STONE);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(3, 1, 2));
        worker.setAiDisabled(true);
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.refreshCatalogue(true);
        var snapshot = post.getOffers().stream()
            .filter(candidate -> candidate.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow();
        post.setOfferEnabledInternal(snapshot.fingerprint(), true);
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        state.target(target.getUuid(), snapshot.offerIndex(), snapshot.fingerprint());
        state.plannedExecutions(1);
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.PAPER))),
            "Wall regression must start with one exact input"
        );
        context.assertFalse(
            MerchantTradeExecutor.hasInteractionLine(worker, target),
            "The solid wall must block every valid entity interaction ray"
        );

        boolean executed = MerchantTradeExecutor.executeOne(
            context.getWorld(),
            worker,
            target,
            state,
            post
        );
        context.assertFalse(executed, "Interaction distance alone must not permit trading through a wall");
        context.assertEquals(0, offer.getUses(), "Wall-blocked execution must not increment offer uses");
        context.assertTrue(state.hasInputs(), "Wall-blocked execution must preserve input cargo");
        context.assertFalse(state.hasRewards(), "Wall-blocked execution must create no reward");
        context.complete();
    }

    @GameTest(maxTicks = 1000, skyAccess = true)
    public void stationaryExtendedTargetIsRejectedWithoutReservingInputs(TestContext context) {
        int y = 90;
        createElevatedFloor(context, y - 1, 66);
        BlockPos postPos = new BlockPos(1, y, 1);
        BlockPos chestPos = new BlockPos(2, y, 1);
        BlockPos targetPos = new BlockPos(61, y, 2);
        ChunkPos targetChunk = new ChunkPos(context.getAbsolutePos(targetPos));
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, y, 2));
        worker.setAiDisabled(true);
        VillagerEntity target = spawnVillager(context, targetPos);
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        // GameTests advance logical time much faster than a normal server.
        // Start loading only after setup is complete, then gate worker AI
        // until the full route is loaded and entity-ticking.
        var routeChunksReady = context.getWorld().getChunkManager().addChunkLoadingTicket(
            ChunkTicketType.DRAGON,
            targetChunk,
            5
        );
        boolean[] configured = {false};
        long[] routeReadyTick = {-1L};
        boolean[] completed = {false};
        boolean[] ticketActive = {true};
        Runnable removeRouteTicket = () -> {
            if (ticketActive[0]) {
                context.getWorld().getChunkManager().removeTicket(
                    ChunkTicketType.DRAGON,
                    targetChunk,
                    5
                );
                ticketActive[0] = false;
            }
        };
        context.runAtEveryTick(() -> {
            if (completed[0]) {
                return;
            }
            try {
                if (routeReadyTick[0] < 0L) {
                    if (context.getTick() >= 500L) {
                        completed[0] = true;
                        removeRouteTicket.run();
                        context.throwGameTestException(
                            "Timed out waiting for extended route chunks to become entity-ticking"
                        );
                    }
                    if (!routeChunksReady.isDone()
                        || !routeChunksAreEntityTicking(context, postPos, targetPos)) {
                        return;
                    }
                    routeChunksReady.join();
                    worker.setAiDisabled(false);
                    routeReadyTick[0] = context.getTick();
                }
                if (!configured[0]) {
                    post.refreshCatalogue(true);
                    post.getOffers().stream()
                        .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
                        .findFirst()
                        .ifPresent(snapshot -> {
                            post.setOfferEnabledInternal(snapshot.fingerprint(), true);
                            post.setStack(0, new ItemStack(Items.NAUTILUS_SHELL, 2));
                            configured[0] = true;
                        });
                }
                if (context.getTick() - routeReadyTick[0] < 180L) {
                    return;
                }
                completed[0] = true;
                removeRouteTicket.run();
                context.assertTrue(
                    configured[0],
                    extendedDiscoveryFailure(context, postPos, target, post)
                );
                context.assertEquals(0, offer.getUses(), "Stationary 55-to-66-block target must not be pursued");
                context.assertEquals(
                    2,
                    post.count(Items.NAUTILUS_SHELL),
                    "Rejected extended target must reserve no inputs"
                );
                context.assertFalse(
                    ((MerchantWorker)worker).merchantVillager$getState().hasCargo(),
                    "Rejected convergence must leave worker cargo empty"
                );
                context.complete();
            } catch (RuntimeException | Error failure) {
                completed[0] = true;
                removeRouteTicket.run();
                throw failure;
            }
        });
    }

    @GameTest(maxTicks = 3000, skyAccess = true)
    public void approachingExtendedTargetCanConvergeAndTradeWithinTether(TestContext context) {
        int y = 60;
        createElevatedFloor(context, y - 1, 66);
        BlockPos postPos = new BlockPos(1, y, 1);
        BlockPos chestPos = new BlockPos(2, y, 1);
        BlockPos targetPos = new BlockPos(61, y, 2);
        ChunkPos targetChunk = new ChunkPos(context.getAbsolutePos(targetPos));
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, y, 2));
        worker.setAiDisabled(true);
        VillagerEntity target = spawnVillager(context, targetPos);
        double initialTargetDistance = Math.sqrt(
            target.squaredDistanceTo(context.getAbsolutePos(postPos).toCenterPos())
        );
        context.assertTrue(
            initialTargetDistance > MerchantVillagerConfig.NORMAL_RADIUS
                && initialTargetDistance <= MerchantVillagerConfig.EXTENDED_RADIUS,
            "Regression target must start in the 55-to-66-block meet-halfway zone"
        );
        target.setAiDisabled(true);
        target.getOffers().clear();
        // This movement regression runs beside dozens of other GameTests.
        // Use an exact signature unique to this fixture: global pre-approval
        // intentionally enables every matching live villager, so the common
        // paper-for-emerald offer can make this worker visit a neighboring
        // fixture before its extended target.
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.AMETHYST_SHARD, 1),
            Optional.empty(),
            new ItemStack(Items.HEART_OF_THE_SEA),
            2,
            7,
            0.03125F
        );
        target.getOffers().add(offer);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        // Load every chunk covered by the simulation ticket asynchronously.
        // Worker AI is enabled only after setup and entity-ticking readiness,
        // preventing the accelerated test clock from outrunning activation.
        var routeChunksReady = context.getWorld().getChunkManager().addChunkLoadingTicket(
            ChunkTicketType.DRAGON,
            targetChunk,
            5
        );
        boolean[] configured = {false};
        long[] movementStart = {-1L};
        double[] maximumWorkerDistance = {0.0};
        boolean[] completed = {false};
        boolean[] routeActivated = {false};
        boolean[] ticketActive = {true};
        Runnable removeRouteTicket = () -> {
            if (ticketActive[0]) {
                context.getWorld().getChunkManager().removeTicket(
                    ChunkTicketType.DRAGON,
                    targetChunk,
                    5
                );
                ticketActive[0] = false;
            }
        };
        context.runAtEveryTick(() -> {
            if (completed[0]) {
                return;
            }
            try {
                if (context.getTick() >= 2800L) {
                    completed[0] = true;
                    removeRouteTicket.run();
                    context.throwGameTestException(
                        "Approaching extended-target fixture did not finish before its cleanup deadline"
                    );
                }
                if (!routeActivated[0]) {
                    if (context.getTick() >= 1200L) {
                        completed[0] = true;
                        removeRouteTicket.run();
                        context.throwGameTestException(
                            "Timed out waiting for approaching route chunks to become entity-ticking"
                        );
                    }
                    if (!routeChunksReady.isDone()
                        || !routeChunksAreEntityTicking(context, postPos, targetPos)) {
                        return;
                    }
                    routeChunksReady.join();
                    worker.setAiDisabled(false);
                    routeActivated[0] = true;
                }
                if (!configured[0]) {
                    post.refreshCatalogue(true);
                    post.getOffers().stream()
                        .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
                        .findFirst()
                        .ifPresent(snapshot -> {
                            post.setOfferEnabledInternal(snapshot.fingerprint(), true);
                            post.setStack(0, new ItemStack(Items.AMETHYST_SHARD));
                            configured[0] = true;
                            movementStart[0] = context.getTick();
                        });
                }
                maximumWorkerDistance[0] = Math.max(
                    maximumWorkerDistance[0],
                    Math.sqrt(worker.squaredDistanceTo(context.getAbsolutePos(postPos).toCenterPos()))
                );
                if (configured[0] && context.getTick() - movementStart[0] < 55L) {
                    target.setVelocity(-0.12, 0.0, 0.0);
                    target.setPosition(target.getX() - 0.12, target.getY(), target.getZ());
                } else {
                    target.setVelocity(Vec3d.ZERO);
                }
                if (movementStart[0] < 0L || context.getTick() - movementStart[0] < 1300L) {
                    return;
                }
                completed[0] = true;
                removeRouteTicket.run();
                MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
                String diagnostics = " (state=" + state.state()
                + ", status=" + state.status()
                + ", failure=" + state.lastFailure()
                + ", stateTicks=" + state.stateTicks()
                + ", worker=" + worker.getEntityPos()
                + ", target=" + target.getEntityPos()
                + ", targetDistance=" + Math.sqrt(worker.squaredDistanceTo(target))
                + ", targetVelocity=" + target.getVelocity()
                + ", workerVelocity=" + worker.getVelocity()
                + ", workerOnGround=" + worker.isOnGround()
                + ", workerWater=" + worker.isTouchingWater()
                + ", interactionLine=" + MerchantTradeExecutor.hasInteractionLine(worker, target)
                + ", directApproach=" + state.directApproachActive()
                + ", navigationIdle=" + worker.getNavigation().isIdle()
                + ", navigationTarget=" + worker.getNavigation().getTargetPos()
                + ", moveControlMoving=" + worker.getMoveControl().isMoving()
                + ", moveControlSpeed=" + worker.getMoveControl().getSpeed()
                + ", walkTarget=" + worker.getBrain()
                    .getOptionalRegisteredMemory(MemoryModuleType.WALK_TARGET)
                + ", workerChunk=" + new ChunkPos(worker.getBlockPos())
                + ", targetChunk=" + new ChunkPos(target.getBlockPos())
                + ", workerChunkLoaded=" + context.getWorld().isChunkLoaded(worker.getBlockPos())
                + ", targetChunkLoaded=" + context.getWorld().isChunkLoaded(target.getBlockPos())
                + ", workerSupport=" + context.getWorld().getBlockState(worker.getBlockPos().down())
                + ", postDistance=" + Math.sqrt(
                    target.squaredDistanceTo(context.getAbsolutePos(postPos).toCenterPos())
                )
                + ", maxWorkerDistance=" + maximumWorkerDistance[0]
                + ", postInput=" + post.count(Items.AMETHYST_SHARD)
                + ", cargo=" + state.cargo()
                + ", available="
                + com.fluffybacon.merchantvillager.trade.TargetMerchantAvailability.canTradeNow(target)
                + ", targetActivity=" + target.getBrain().getFirstPossibleNonCoreActivity()
                + ", targetWater=" + target.isTouchingWater()
                + ", targetSleeping=" + target.isSleeping()
                + ", targetCustomer=" + target.hasCustomer()
                + ", workerProfession=" + worker.getVillagerData().profession().getIdAsString()
                + ", workerActivity=" + worker.getBrain().getFirstPossibleNonCoreActivity()
                + ")";
                context.assertTrue(
                    configured[0],
                    extendedDiscoveryFailure(context, postPos, target, post)
                );
                context.assertEquals(
                    1,
                    offer.getUses(),
                    "Approaching extended target must complete one real use" + diagnostics
                );
                context.assertEquals(
                    1,
                    context.getBlockEntity(chestPos, ChestBlockEntity.class).count(Items.HEART_OF_THE_SEA),
                    "Extended target reward must return to the output chest" + diagnostics
                );
                context.assertTrue(
                    Math.sqrt(target.squaredDistanceTo(context.getAbsolutePos(postPos).toCenterPos()))
                        <= MerchantVillagerConfig.NORMAL_RADIUS,
                    "Extended target must approach into the normal 55-block radius before being pursued"
                );
                context.assertTrue(
                    maximumWorkerDistance[0] <= MerchantVillagerConfig.EXTENDED_RADIUS + 0.25,
                    "Merchant must remain inside the absolute 66-block tether"
                );
                context.complete();
            } catch (RuntimeException | Error failure) {
                completed[0] = true;
                removeRouteTicket.run();
                throw failure;
            }
        });
    }

    private static boolean routeChunksAreEntityTicking(
        TestContext context, BlockPos relativeStart, BlockPos relativeEnd
    ) {
        BlockPos start = context.getAbsolutePos(relativeStart);
        BlockPos end = context.getAbsolutePos(relativeEnd);
        ChunkPos startChunk = new ChunkPos(start);
        ChunkPos endChunk = new ChunkPos(end);
        int minX = Math.min(startChunk.x, endChunk.x);
        int maxX = Math.max(startChunk.x, endChunk.x);
        int minZ = Math.min(startChunk.z, endChunk.z);
        int maxZ = Math.max(startChunk.z, endChunk.z);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                BlockPos probe = new BlockPos(
                    (chunkX << 4) + 8,
                    start.getY(),
                    (chunkZ << 4) + 8
                );
                if (!context.getWorld().shouldTickEntityAt(probe)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String extendedDiscoveryFailure(
        TestContext context,
        BlockPos postPos,
        VillagerEntity target,
        MerchantPostBlockEntity post
    ) {
        return "Extended target must be discovered"
            + " (post=" + context.getAbsolutePos(postPos)
            + ", target=" + target.getEntityPos()
            + ", distance=" + Math.sqrt(target.squaredDistanceTo(context.getAbsolutePos(postPos).toCenterPos()))
            + ", loaded=" + context.getWorld().isChunkLoaded(target.getBlockPos())
            + ", alive=" + target.isAlive()
            + ", removed=" + target.isRemoved()
            + ", customer=" + target.hasCustomer()
            + ", water=" + target.isTouchingWater()
            + ", sleeping=" + target.isSleeping()
            + ", available=" + com.fluffybacon.merchantvillager.trade.TargetMerchantAvailability.canTradeNow(target)
            + ", offers=" + post.getOffers().size() + ") on tick " + context.getTick();
    }

    @GameTest
    public void priceChangePreservesCargoAndDoesNotUseOffer(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(4, 1, 2));
        worker.setAiDisabled(true);
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        );
        target.getOffers().add(offer);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.refreshCatalogue(true);
        var snapshot = post.getOffers().stream()
            .filter(candidate -> candidate.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow();
        post.setOfferEnabledInternal(snapshot.fingerprint(), true);
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        state.target(target.getUuid(), snapshot.offerIndex(), snapshot.fingerprint());
        state.plannedExecutions(1);
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.PAPER))),
            "Test setup must load the old one-paper price"
        );
        offer.setSpecialPrice(1);

        boolean executed = MerchantTradeExecutor.executeOne(
            context.getWorld(),
            worker,
            target,
            state,
            post
        );
        context.assertFalse(executed, "Price increase beyond cargo must cancel the individual execution");
        context.assertEquals(0, offer.getUses(), "Failed repriced trade must not use the offer");
        context.assertEquals(1, state.cargo().getFirst().getCount(), "Exact input cargo must remain");
        context.assertFalse(state.hasRewards(), "Failed repriced trade must create no reward");
        context.complete();
    }

    @GameTest
    public void savedInputAndRewardCargoReconcileToRecovery(TestContext context) {
        MerchantWorkerState inputState = new MerchantWorkerState();
        inputState.bindPost(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 1, 1)));
        inputState.target(java.util.UUID.randomUUID(), 0, "a".repeat(64));
        inputState.plannedExecutions(3);
        inputState.reservationExpiry(context.getWorld().getTime() + 1200L);
        inputState.enter(MerchantState.TRAVELLING_TO_TARGET, "Travelling");
        context.assertTrue(
            inputState.loadInputs(java.util.List.of(new ItemStack(Items.PAPER, 3))),
            "Test setup must load input cargo"
        );
        MerchantWorkerState restoredInputs = roundTripState(context, inputState);
        context.assertEquals(
            MerchantState.RECOVERING,
            restoredInputs.state(),
            "Reloaded in-flight inputs must enter conservative recovery"
        );
        context.assertTrue(restoredInputs.hasInputs(), "Reloaded input stacks must remain present");
        context.assertEquals(0L, restoredInputs.reservationExpiry(), "Stale reservation lease must not resume");

        MerchantWorkerState rewardState = new MerchantWorkerState();
        rewardState.bindPost(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 1, 1)));
        context.assertTrue(
            rewardState.add(new ItemStack(Items.EMERALD, 2), true),
            "Test setup must load reward cargo"
        );
        rewardState.enter(MerchantState.DEPOSITING_REWARDS, "Depositing");
        MerchantWorkerState restoredRewards = roundTripState(context, rewardState);
        context.assertEquals(
            MerchantState.RECOVERING,
            restoredRewards.state(),
            "Reloaded rewards must enter conservative recovery"
        );
        context.assertTrue(restoredRewards.hasRewards(), "Reloaded reward stacks must remain rewards");
        context.assertFalse(restoredRewards.hasInputs(), "Reward flags must persist across reload");
        context.complete();
    }

    private static MerchantWorkerState roundTripState(
        TestContext context, MerchantWorkerState original
    ) {
        NbtWriteView write = NbtWriteView.create(
            ErrorReporter.EMPTY,
            context.getWorld().getRegistryManager()
        );
        original.write(write);
        MerchantWorkerState restored = new MerchantWorkerState();
        restored.read(NbtReadView.create(
            ErrorReporter.EMPTY,
            context.getWorld().getRegistryManager(),
            write.getNbt()
        ));
        return restored;
    }

    private static void createFloor(TestContext context) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void createElevatedFloor(TestContext context, int y, int maxX) {
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= 4; z++) {
                context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static VillagerEntity spawnVillager(TestContext context, BlockPos pos) {
        VillagerEntity villager = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(pos),
            SpawnReason.COMMAND
        );
        villager.getBrain().doExclusively(Activity.IDLE);
        return villager;
    }

    private static void prepareWorker(TestContext context, VillagerEntity worker, BlockPos relativePost) {
        BlockPos absolutePost = context.getAbsolutePos(relativePost);
        Optional<BlockPos> reservedPost = context.getWorld().getPointOfInterestStorage().getPosition(
            entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
            (entry, candidate) -> candidate.equals(absolutePost),
            absolutePost,
            1
        );
        context.assertTrue(
            reservedPost.filter(absolutePost::equals).isPresent(),
            "Prepared Merchant must reserve its exact Merchant's Post POI"
        );
        worker.setVillagerData(worker.getVillagerData().withProfession(
            context.getWorld().getRegistryManager(),
            ModVillagerProfessions.MERCHANT_KEY
        ));
        worker.reinitializeBrain(context.getWorld());
        worker.getBrain().doExclusively(Activity.IDLE);
        worker.getBrain().remember(
            MemoryModuleType.JOB_SITE,
            GlobalPos.create(
                context.getWorld().getRegistryKey(),
                absolutePost
            )
        );
        ((MerchantWorker)worker).merchantVillager$getState().bindPost(
            context.getWorld(),
            absolutePost
        );
        context.getWorld().setTimeOfDay(3000L);
    }
}
