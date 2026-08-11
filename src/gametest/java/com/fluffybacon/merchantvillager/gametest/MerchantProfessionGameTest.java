package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantController;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.registry.ModPointOfInterests;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;

public final class MerchantProfessionGameTest {
    @GameTest(maxTicks = 400, skyAccess = true)
    public void unemployedVillagerClaimsMerchantPost(TestContext context) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos post = new BlockPos(2, 1, 2);
        context.setBlockState(post, ModBlocks.MERCHANT_POST);
        VillagerEntity villager = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(2, 1, 3)),
            SpawnReason.COMMAND
        );
        context.getWorld().setTimeOfDay(3000L);
        context.runAtTick(300, () -> {
            BlockPos absolutePost = context.getAbsolutePos(post);
            var poiType = context.getWorld().getPointOfInterestStorage().getType(absolutePost);
            String poi = poiType.map(entry -> entry.getIdAsString()
                + ",acquirable=" + entry.isIn(net.minecraft.registry.tag.PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE))
                .orElse("<none>");
            var path = villager.getNavigation().findPathTo(absolutePost, 1);
            String pathState = path == null
                ? "<none>"
                : "reaches=" + path.reachesTarget() + ",target=" + path.getTarget();
            String profession = villager.getVillagerData().profession().getKey()
                .map(key -> key.getValue().toString())
                .orElse("<unregistered>");
            String jobSite = villager.getBrain()
                .getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.JOB_SITE)
                .map(Object::toString)
                .orElse("<none>");
            String potentialJobSite = villager.getBrain()
                .getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.POTENTIAL_JOB_SITE)
                .map(Object::toString)
                .orElse("<none>");
            context.assertTrue(
                villager.getVillagerData().profession().matchesKey(ModVillagerProfessions.MERCHANT_KEY),
                "Unemployed adult villager must claim the Merchant's Post through normal Brain POI behavior"
                    + " (profession=" + profession + ", jobSite=" + jobSite
                    + ", potentialJobSite=" + potentialJobSite + ", poi=" + poi
                    + ", path=" + pathState + ")"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 400, skyAccess = true)
    public void onePostIsClaimedByExactlyOneMerchant(TestContext context) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos postPos = new BlockPos(2, 1, 2);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity first = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(2, 1, 3)),
            SpawnReason.COMMAND
        );
        VillagerEntity second = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(3, 1, 3)),
            SpawnReason.COMMAND
        );
        context.getWorld().setTimeOfDay(3000L);

        context.runAtTick(300, () -> {
            int merchants = 0;
            if (first.getVillagerData().profession().matchesKey(ModVillagerProfessions.MERCHANT_KEY)) {
                merchants++;
            }
            if (second.getVillagerData().profession().matchesKey(ModVillagerProfessions.MERCHANT_KEY)) {
                merchants++;
            }
            context.assertEquals(1, merchants, "One Merchant's Post must support exactly one Merchant");
            MerchantPostBlockEntity post =
                context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
            context.assertTrue(post.getAssignedMerchant().isPresent(), "Claimed post must retain one worker UUID");
            context.assertTrue(
                post.getAssignedMerchant().get().equals(first.getUuid())
                    || post.getAssignedMerchant().get().equals(second.getUuid()),
                "Assigned UUID must belong to the villager that claimed the POI"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 20)
    public void professionLossReleasesMerchantPost(TestContext context) {
        BlockPos postPos = new BlockPos(2, 1, 2);
        context.setBlockState(postPos.down(), Blocks.STONE);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = prepareAssignedMerchant(context, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
        workerState.addStoredExperience(7);

        worker.setVillagerData(worker.getVillagerData().withProfession(
            context.getWorld().getRegistryManager(),
            VillagerProfession.NONE
        ));
        MerchantController.tick(context.getWorld(), worker, workerState);

        context.assertTrue(post.getAssignedMerchant().isEmpty(), "Profession loss must release the post assignment");
        context.assertTrue(workerState.postPos() == null, "Profession loss must clear the cached post position");
        context.assertEquals(0, workerState.storedExperience(), "Profession loss must drain stored XP");
        context.assertEquals(7, releasedExperience(context, worker), "Profession loss must release stored XP once");
        assertPostCanBeReservedAgain(context, postPos);
        context.complete();
    }

    @GameTest(maxTicks = 20)
    public void jobSiteLossReleasesMerchantPost(TestContext context) {
        BlockPos postPos = new BlockPos(2, 1, 2);
        context.setBlockState(postPos.down(), Blocks.STONE);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = prepareAssignedMerchant(context, postPos);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
        workerState.addStoredExperience(11);

        worker.getBrain().forget(MemoryModuleType.JOB_SITE);
        MerchantController.tick(context.getWorld(), worker, workerState);

        context.assertTrue(post.getAssignedMerchant().isEmpty(), "Job-site loss must release the post assignment");
        context.assertTrue(workerState.postPos() == null, "Job-site loss must clear the cached post position");
        context.assertEquals(0, workerState.storedExperience(), "Job-site loss must drain stored XP");
        context.assertEquals(11, releasedExperience(context, worker), "Job-site loss must release stored XP once");
        assertPostCanBeReservedAgain(context, postPos);
        context.complete();
    }

    @GameTest(maxTicks = 20)
    public void jobSiteChangeReleasesFormerMerchantPost(TestContext context) {
        BlockPos firstPostPos = new BlockPos(2, 1, 2);
        BlockPos secondPostPos = new BlockPos(5, 1, 2);
        context.setBlockState(firstPostPos.down(), Blocks.STONE);
        context.setBlockState(firstPostPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(secondPostPos.down(), Blocks.STONE);
        context.setBlockState(secondPostPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = prepareAssignedMerchant(context, firstPostPos);
        MerchantPostBlockEntity firstPost =
            context.getBlockEntity(firstPostPos, MerchantPostBlockEntity.class);
        MerchantPostBlockEntity secondPost =
            context.getBlockEntity(secondPostPos, MerchantPostBlockEntity.class);
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();

        worker.getBrain().remember(
            MemoryModuleType.JOB_SITE,
            GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(secondPostPos))
        );
        MerchantController.tick(context.getWorld(), worker, workerState);

        context.assertTrue(
            firstPost.getAssignedMerchant().isEmpty(),
            "Changing job sites must release the former post assignment"
        );
        context.assertTrue(
            secondPost.getAssignedMerchant().isEmpty(),
            "A stale cached assignment must not silently jump to a new post"
        );
        context.assertTrue(workerState.postPos() == null, "Job-site mismatch must clear cached post state");
        context.assertFalse(
            worker.getBrain().hasMemoryModule(MemoryModuleType.JOB_SITE),
            "Job-site mismatch must be reacquired through vanilla POI ownership"
        );
        assertPostCanBeReservedAgain(context, firstPostPos);
        context.complete();
    }

    @GameTest(maxTicks = 20)
    public void staleWorkerCannotReleaseAnotherWorkersPoiTicket(TestContext context) {
        BlockPos postPos = new BlockPos(2, 1, 2);
        BlockPos absolutePost = context.getAbsolutePos(postPos);
        context.setBlockState(postPos.down(), Blocks.STONE);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        Optional<BlockPos> reservation = context.getWorld().getPointOfInterestStorage().getPosition(
            entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
            (entry, candidate) -> candidate.equals(absolutePost),
            absolutePost,
            1
        );
        context.assertTrue(
            reservation.filter(absolutePost::equals).isPresent(),
            "Regression setup must consume the post's only POI ticket"
        );
        VillagerEntity staleWorker = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(postPos.south()),
            SpawnReason.COMMAND
        );
        staleWorker.setVillagerData(staleWorker.getVillagerData().withProfession(
            context.getWorld().getRegistryManager(),
            ModVillagerProfessions.MERCHANT_KEY
        ));
        MerchantWorkerState staleState = ((MerchantWorker)staleWorker).merchantVillager$getState();
        staleState.bindPost(context.getWorld(), absolutePost);
        java.util.UUID actualOwner = java.util.UUID.randomUUID();
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.assignMerchant(actualOwner);

        MerchantController.tick(context.getWorld(), staleWorker, staleState);

        context.assertEquals(
            Optional.of(actualOwner),
            post.getAssignedMerchant(),
            "A stale worker must not clear another worker's post assignment"
        );
        Optional<BlockPos> stolenReservation =
            context.getWorld().getPointOfInterestStorage().getPosition(
                entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
                (entry, candidate) -> candidate.equals(absolutePost),
                absolutePost,
                1
            );
        context.assertTrue(
            stolenReservation.isEmpty(),
            "A stale worker must not release another worker's anonymous POI ticket"
        );
        context.complete();
    }

    @GameTest(maxTicks = 20)
    public void crossDimensionMerchantPreservesPostOwnershipUntilReturn(TestContext context) {
        VillagerEntity worker = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(2, 1, 2)),
            SpawnReason.COMMAND
        );
        worker.setVillagerData(worker.getVillagerData().withProfession(
            context.getWorld().getRegistryManager(),
            ModVillagerProfessions.MERCHANT_KEY
        ));
        var otherWorld = context.getWorld().getServer().getWorld(net.minecraft.world.World.NETHER);
        context.assertTrue(otherWorld != null, "Regression setup requires the Nether world");
        BlockPos rememberedPost = context.getAbsolutePos(new BlockPos(3, 1, 3));
        GlobalPos crossDimensionJobSite = GlobalPos.create(
            net.minecraft.world.World.NETHER,
            rememberedPost
        );
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
        workerState.bindPost(otherWorld, rememberedPost);
        worker.getBrain().remember(MemoryModuleType.JOB_SITE, crossDimensionJobSite);

        MerchantController.tick(context.getWorld(), worker, workerState);

        context.assertEquals(
            rememberedPost,
            workerState.postPos(),
            "Cross-dimensional ticking must preserve the cached post position"
        );
        context.assertTrue(
            workerState.isPostIn(otherWorld),
            "Cross-dimensional ticking must preserve the post dimension"
        );
        context.assertEquals(
            Optional.of(crossDimensionJobSite),
            worker.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE),
            "Cross-dimensional ticking must preserve the vanilla job-site memory"
        );
        context.complete();
    }

    private static VillagerEntity prepareAssignedMerchant(TestContext context, BlockPos relativePost) {
        BlockPos absolutePost = context.getAbsolutePos(relativePost);
        Optional<BlockPos> reservedPost = context.getWorld().getPointOfInterestStorage().getPosition(
            entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
            (entry, candidate) -> candidate.equals(absolutePost),
            absolutePost,
            1
        );
        context.assertTrue(
            reservedPost.filter(absolutePost::equals).isPresent(),
            "Regression setup must reserve the exact Merchant's Post"
        );
        VillagerEntity worker = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(relativePost.south()),
            SpawnReason.COMMAND
        );
        worker.setVillagerData(worker.getVillagerData().withProfession(
            context.getWorld().getRegistryManager(),
            ModVillagerProfessions.MERCHANT_KEY
        ));
        worker.reinitializeBrain(context.getWorld());
        worker.getBrain().doExclusively(Activity.IDLE);
        worker.getBrain().remember(
            MemoryModuleType.JOB_SITE,
            GlobalPos.create(context.getWorld().getRegistryKey(), absolutePost)
        );
        MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
        workerState.bindPost(context.getWorld(), absolutePost);
        context.getBlockEntity(relativePost, MerchantPostBlockEntity.class).assignMerchant(worker.getUuid());
        return worker;
    }

    private static void assertPostCanBeReservedAgain(TestContext context, BlockPos relativePost) {
        BlockPos absolutePost = context.getAbsolutePos(relativePost);
        Optional<BlockPos> reservation = context.getWorld().getPointOfInterestStorage().getPosition(
            entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
            (entry, candidate) -> candidate.equals(absolutePost),
            absolutePost,
            1
        );
        context.assertTrue(
            reservation.filter(absolutePost::equals).isPresent(),
            "Released Merchant's Post POI must be immediately reservable"
        );
    }

    private static int releasedExperience(TestContext context, VillagerEntity worker) {
        return context.getWorld().getEntitiesByClass(
            ExperienceOrbEntity.class,
            worker.getBoundingBox().expand(2.0),
            orb -> true
        ).stream().mapToInt(ExperienceOrbEntity::getValue).sum();
    }
}
