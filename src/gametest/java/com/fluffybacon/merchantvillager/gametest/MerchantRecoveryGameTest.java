package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantState;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.merchant.ReservationManager;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.registry.ModPointOfInterests;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

public final class MerchantRecoveryGameTest {
    @GameTest(maxTicks = 20)
    public void cargoDropGuardResetsForARealLaterCargoLoad(TestContext context) {
        VillagerEntity worker = spawnVillager(context, new BlockPos(2, 2, 2));
        worker.setAiDisabled(true);
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.PAPER, 3))),
            "First cargo must load"
        );
        state.dropCargoOnce(worker);
        state.dropCargoOnce(worker);
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.WHEAT, 4))),
            "A later independent cargo load must arm recovery again"
        );
        state.dropCargoOnce(worker);
        state.dropCargoOnce(worker);

        context.runAtTick(2, () -> {
            context.assertEquals(3, droppedCount(context, worker.getBlockPos(), Items.PAPER), "First cargo drops once");
            context.assertEquals(4, droppedCount(context, worker.getBlockPos(), Items.WHEAT), "Later cargo drops once");
            context.complete();
        });
    }

    @GameTest(maxTicks = 20)
    public void merchantDeathDropsRewardCargoAndClearsAssignment(TestContext context) {
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos.down(), Blocks.STONE);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(2, 1, 1));
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        state.bindPost(context.getWorld(), context.getAbsolutePos(postPos));
        context.assertTrue(state.add(new ItemStack(Items.EMERALD, 2), true), "Reward cargo must load");
        state.addStoredExperience(3);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        BlockPos deathPos = worker.getBlockPos();
        worker.kill(context.getWorld());

        context.runAtTick(2, () -> {
            context.assertEquals(2, droppedCount(context, deathPos, Items.EMERALD), "Death drops exact rewards once");
            context.assertTrue(post.getAssignedMerchant().isEmpty(), "Death clears the post assignment");
            context.assertFalse(state.hasCargo(), "Dropped death cargo must be cleared from persisted state");
            context.assertEquals(0, state.storedExperience(), "Death must drain stored XP");
            context.assertEquals(3, droppedExperience(context, deathPos), "Death must release stored XP once");
            context.complete();
        });
    }

    @GameTest(maxTicks = 20)
    public void lightningConversionDropsCargoAndReleasesPostExactlyOnce(TestContext context) {
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos.down(), Blocks.STONE);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(2, 1, 1));
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        state.bindPost(context.getWorld(), context.getAbsolutePos(postPos));
        state.addStoredExperience(3);
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.PAPER, 3))),
            "Lightning regression must start with exact input cargo"
        );
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        java.util.UUID target = java.util.UUID.randomUUID();
        String fingerprint = "a".repeat(64);
        context.assertTrue(
            ReservationManager.reserve(
                context.getWorld().getServer(),
                worker.getUuid(),
                target,
                fingerprint,
                1,
                context.getWorld().getTime()
            ),
            "Lightning regression must own one live reservation"
        );
        BlockPos conversionPos = worker.getBlockPos();
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, context.getWorld());
        lightning.setPosition(worker.getEntityPos());
        Difficulty previousDifficulty = context.getWorld().getDifficulty();
        context.getWorld().getServer().setDifficulty(Difficulty.NORMAL, true);
        worker.onStruckByLightning(context.getWorld(), lightning);
        context.getWorld().getServer().setDifficulty(previousDifficulty, true);

        context.runAtTick(2, () -> {
            context.assertTrue(worker.isRemoved(), "Successful lightning conversion must discard the villager");
            context.assertFalse(state.hasCargo(), "Dropped conversion cargo must be cleared");
            context.assertEquals(0, state.storedExperience(), "Conversion must drain stored XP");
            context.assertTrue(post.getAssignedMerchant().isEmpty(), "Conversion must clear the post assignment");
            context.assertEquals(
                0,
                ReservationManager.countWorker(
                    context.getWorld().getServer(), worker.getUuid(), context.getWorld().getTime()
                ),
                "Conversion must release every worker reservation"
            );
            context.assertEquals(
                3,
                droppedCount(context, conversionPos, Items.PAPER),
                "Lightning conversion must drop exact cargo once"
            );
            context.assertEquals(
                3,
                droppedExperience(context, conversionPos),
                "Lightning conversion must release stored XP once"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 260)
    public void unreachableDestroyedPostDropsCargoAfterRecoveryTimeout(TestContext context) {
        BlockPos workerPos = new BlockPos(2, 1, 2);
        context.setBlockState(workerPos.down(), Blocks.STONE);
        for (int offset = -1; offset <= 1; offset++) {
            if (offset != 0) {
                context.setBlockState(workerPos.add(offset, 0, 0), Blocks.BEDROCK);
                context.setBlockState(workerPos.add(offset, 1, 0), Blocks.BEDROCK);
                context.setBlockState(workerPos.add(0, 0, offset), Blocks.BEDROCK);
                context.setBlockState(workerPos.add(0, 1, offset), Blocks.BEDROCK);
            }
        }
        VillagerEntity worker = spawnVillager(context, workerPos);
        worker.setInvulnerable(true);
        MerchantWorkerState state = ((MerchantWorker)worker).merchantVillager$getState();
        BlockPos unreachableFormerPost = context.getAbsolutePos(new BlockPos(20, 1, 2));
        state.bindPost(context.getWorld(), unreachableFormerPost);
        context.assertTrue(
            state.loadInputs(java.util.List.of(new ItemStack(Items.PAPER, 5))),
            "Unreachable-post regression must begin with exact cargo"
        );
        state.onPostDestroyed(unreachableFormerPost);

        context.runAtTick(230, () -> {
            context.assertFalse(
                state.hasCargo(),
                "Recovery must not carry cargo forever when the former post is unreachable"
            );
            context.assertEquals(
                5,
                droppedCount(context, worker.getBlockPos(), Items.PAPER),
                "Timed-out recovery must drop the exact cargo once at the worker"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 600, skyAccess = true)
    public void postDestroyedAfterReservationReturnsAndDropsInputCargoOnce(TestContext context) {
        int y = 120;
        createFloor(context, y - 1, 14);
        BlockPos postPos = new BlockPos(1, y, 1);
        BlockPos chestPos = new BlockPos(2, y, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, y, 2));
        VillagerEntity target = targetWithOffer(
            context, new BlockPos(12, y, 2), 8, Items.SPIDER_EYE, Items.STRING
        );
        worker.setCanPickUpLoot(false);
        target.setCanPickUpLoot(false);
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        TradeOffer offer = target.getOffers().getFirst();
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.SPIDER_EYE, 3));
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
        boolean[] destroyed = {false};
        context.runAtEveryTick(() -> {
            if (!destroyed[0]
                && workerState.state() == MerchantState.TRAVELLING_TO_TARGET
                && workerState.hasInputs()) {
                destroyed[0] = true;
                context.setBlockState(postPos, Blocks.AIR);
            }
            if (destroyed[0] && !workerState.hasCargo()) {
                context.assertEquals(0, offer.getUses(), "Post destruction before arrival must execute no trade");
                context.assertEquals(
                    3,
                    droppedCount(context, context.getAbsolutePos(postPos), Items.SPIDER_EYE),
                    "Reserved inputs must drop once near the former post"
                );
                context.complete();
            }
        });
    }

    @GameTest(maxTicks = 700, skyAccess = true)
    public void chestRemovedAfterTradeKeepsRewardsUntilReplacementExists(TestContext context) {
        int y = 150;
        createFloor(context, y - 1, 10);
        BlockPos postPos = new BlockPos(1, y, 1);
        BlockPos originalChest = new BlockPos(2, y, 1);
        BlockPos replacementChest = new BlockPos(1, y, 0);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(originalChest, Blocks.CHEST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, y, 2));
        VillagerEntity target = targetWithOffer(
            context, new BlockPos(7, y, 2), 8, Items.CLAY_BALL, Items.BRICK
        );
        prepareWorker(context, worker, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(worker.getUuid());
        post.refreshCatalogue(true);
        TradeOffer offer = target.getOffers().getFirst();
        String fingerprint = post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.CLAY_BALL, 3));
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
        boolean[] removed = {false};
        boolean[] observedWaiting = {false};
        context.runAtEveryTick(() -> {
            if (!removed[0] && workerState.hasRewards()) {
                removed[0] = true;
                context.setBlockState(originalChest, Blocks.AIR);
            }
            if (removed[0] && workerState.hasRewards()
                && (workerState.state() == MerchantState.WAITING_FOR_OUTPUT_SPACE
                    || workerState.state() == MerchantState.FINDING_OUTPUT_CHEST)) {
                observedWaiting[0] = true;
            }
        });
        context.runAtTick(300, () -> context.setBlockState(replacementChest, Blocks.CHEST));

        context.runAtTick(600, () -> {
            context.assertTrue(removed[0], "Original chest must be removed after real rewards exist");
            context.assertTrue(observedWaiting[0], "Worker must retain rewards while no chest exists");
            context.assertEquals(3, offer.getUses(), "Completed trades must not repeat during chest recovery");
            context.assertEquals(
                3,
                context.getBlockEntity(replacementChest, ChestBlockEntity.class).count(Items.BRICK),
                "Replacement chest must receive all retained rewards"
            );
            context.assertFalse(workerState.hasCargo(), "Cargo must clear only after successful deposit");
            context.complete();
        });
    }

    @GameTest(maxTicks = 1300, skyAccess = true)
    public void twoPostsSharingOneOfferNeverDoubleExecute(TestContext context) {
        int y = 180;
        createFloor(context, y - 1, 24);
        BlockPos firstPostPos = new BlockPos(1, y, 1);
        BlockPos secondPostPos = new BlockPos(20, y, 1);
        BlockPos firstChestPos = new BlockPos(2, y, 1);
        BlockPos secondChestPos = new BlockPos(19, y, 1);
        ChunkPos simulationChunk = new ChunkPos(
            context.getAbsolutePos(new BlockPos(10, y, 2))
        );
        context.getWorld().getChunkManager().addTicket(
            ChunkTicketType.DRAGON,
            simulationChunk,
            3
        );
        context.setBlockState(firstPostPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(secondPostPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(firstChestPos, Blocks.CHEST);
        context.setBlockState(secondChestPos, Blocks.CHEST);
        VillagerEntity firstWorker = spawnVillager(context, new BlockPos(1, y, 2));
        VillagerEntity secondWorker = spawnVillager(context, new BlockPos(20, y, 2));
        VillagerEntity target = targetWithOffer(
            context, new BlockPos(10, y, 2), 4, Items.FLINT, Items.IRON_NUGGET
        );
        prepareWorker(context, firstWorker, firstPostPos);
        prepareWorker(context, secondWorker, secondPostPos);

        MerchantPostBlockEntity firstPost =
            context.getBlockEntity(firstPostPos, MerchantPostBlockEntity.class);
        MerchantPostBlockEntity secondPost =
            context.getBlockEntity(secondPostPos, MerchantPostBlockEntity.class);
        firstPost.assignMerchant(firstWorker.getUuid());
        secondPost.assignMerchant(secondWorker.getUuid());
        firstPost.refreshCatalogue(true);
        secondPost.refreshCatalogue(true);
        String firstFingerprint = firstPost.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        String secondFingerprint = secondPost.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        firstPost.setOfferEnabledInternal(firstFingerprint, true);
        secondPost.setOfferEnabledInternal(secondFingerprint, true);
        firstPost.setStack(0, new ItemStack(Items.FLINT, 3));
        secondPost.setStack(0, new ItemStack(Items.FLINT, 3));
        TradeOffer offer = target.getOffers().getFirst();

        context.runAtTick(1200, () -> {
            context.getWorld().getChunkManager().removeTicket(
                ChunkTicketType.DRAGON,
                simulationChunk,
                3
            );
            MerchantWorkerState firstState =
                ((MerchantWorker)firstWorker).merchantVillager$getState();
            MerchantWorkerState secondState =
                ((MerchantWorker)secondWorker).merchantVillager$getState();
            int rewards = context.getBlockEntity(firstChestPos, ChestBlockEntity.class).count(Items.IRON_NUGGET)
                + context.getBlockEntity(secondChestPos, ChestBlockEntity.class).count(Items.IRON_NUGGET);
            int unusedInputs = firstPost.count(Items.FLINT) + secondPost.count(Items.FLINT);
            String diagnostics = " (first=" + firstState.state() + "/" + firstState.status()
                + ", firstFailure=" + firstState.lastFailure()
                + ", firstTicks=" + firstState.stateTicks()
                + ", firstProfession=" + firstWorker.getVillagerData().profession().getIdAsString()
                + ", firstActivity=" + firstWorker.getBrain().getFirstPossibleNonCoreActivity()
                + ", firstPostFlint=" + firstPost.count(Items.FLINT)
                + ", firstCargo=" + firstState.cargo()
                + ", second=" + secondState.state() + "/" + secondState.status()
                + ", secondFailure=" + secondState.lastFailure()
                + ", secondTicks=" + secondState.stateTicks()
                + ", secondProfession=" + secondWorker.getVillagerData().profession().getIdAsString()
                + ", secondActivity=" + secondWorker.getBrain().getFirstPossibleNonCoreActivity()
                + ", secondPostFlint=" + secondPost.count(Items.FLINT)
                + ", secondCargo=" + secondState.cargo()
                + ", firstAssigned=" + firstPost.getAssignedMerchant()
                + ", secondAssigned=" + secondPost.getAssignedMerchant()
                + ", targetAvailable="
                + com.fluffybacon.merchantvillager.trade.TargetMerchantAvailability.canTradeNow(target)
                + ", target=" + target.getEntityPos()
                + ", firstWorker=" + firstWorker.getEntityPos()
                + ", secondWorker=" + secondWorker.getEntityPos()
                + ", rewards=" + rewards
                + ", unused=" + unusedInputs
                + ")";
            context.assertEquals(
                4,
                offer.getUses(),
                "Shared offer must stop at its exact max-use count" + diagnostics
            );
            context.assertEquals(4, rewards, "Four uses must create exactly four total rewards");
            context.assertEquals(2, unusedInputs, "Six inputs minus four uses must leave exactly two");
            context.assertFalse(
                ((MerchantWorker)firstWorker).merchantVillager$getState().hasCargo(),
                "First worker must settle its cargo"
            );
            context.assertFalse(
                ((MerchantWorker)secondWorker).merchantVillager$getState().hasCargo(),
                "Second worker must settle its cargo"
            );
            context.complete();
        });
    }

    private static VillagerEntity targetWithOffer(
        TestContext context, BlockPos position, int maxUses, Item input, Item output
    ) {
        VillagerEntity target = spawnVillager(context, position);
        target.setAiDisabled(true);
        target.getBrain().doExclusively(Activity.IDLE);
        target.getOffers().clear();
        target.getOffers().add(new TradeOffer(
            new TradedItem(input, 1),
            Optional.empty(),
            new ItemStack(output),
            maxUses,
            1,
            0.05F
        ));
        return target;
    }

    private static void prepareWorker(
        TestContext context, VillagerEntity worker, BlockPos relativePost
    ) {
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

    private static VillagerEntity spawnVillager(TestContext context, BlockPos position) {
        VillagerEntity villager = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(position),
            SpawnReason.COMMAND
        );
        villager.getBrain().doExclusively(Activity.IDLE);
        return villager;
    }

    private static void createFloor(TestContext context, int y, int maxX) {
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    private static int droppedCount(
        TestContext context, BlockPos center, Item item
    ) {
        return context.getWorld().getEntitiesByClass(
            ItemEntity.class,
            new Box(center).expand(5.0),
            entity -> entity.getStack().isOf(item)
        ).stream().mapToInt(entity -> entity.getStack().getCount()).sum();
    }

    private static int droppedExperience(TestContext context, BlockPos center) {
        return context.getWorld().getEntitiesByClass(
            ExperienceOrbEntity.class,
            new Box(center).expand(5.0),
            entity -> true
        ).stream().mapToInt(ExperienceOrbEntity::getValue).sum();
    }
}
