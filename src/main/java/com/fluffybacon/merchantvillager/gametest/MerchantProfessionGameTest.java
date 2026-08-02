package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
}
