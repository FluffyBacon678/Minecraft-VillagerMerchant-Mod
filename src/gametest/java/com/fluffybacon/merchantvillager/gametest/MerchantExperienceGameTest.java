package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.AutomatedTradeExperience;
import com.fluffybacon.merchantvillager.merchant.MerchantTradeExecutor;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.test.TestContext;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

public final class MerchantExperienceGameTest {
    @GameTest
    public void automatedVillagerTradeStoresXpAndLeavesNormalTradeUntouched(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        VillagerEntity target = spawnVillager(context, new BlockPos(3, 1, 2));
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
            "Automated XP test must load one exact input"
        );

        context.assertTrue(
            MerchantTradeExecutor.executeOne(context.getWorld(), worker, target, state, post),
            "The automated trade must execute"
        );
        int captured = state.storedExperience();
        context.assertTrue(
            captured >= 3 && captured <= 6,
            "The exact vanilla 3-6 player XP reward must be stored"
        );
        context.assertEquals(1, offer.getUses(), "Automated trade must use the live offer once");
        context.assertEquals(1, target.getExperience(), "Target career XP must retain vanilla behavior");
        context.assertEquals(0, experienceValueNear(context, target), "Automated XP must not reach the floor");

        target.trade(offer);
        context.assertEquals(
            captured,
            state.storedExperience(),
            "A trade outside the capture scope must not alter worker XP"
        );
        context.assertTrue(
            experienceValueNear(context, target) >= 3,
            "A normal vanilla trade must still spawn its XP orb"
        );
        context.complete();
    }

    @GameTest
    public void automatedWanderingTraderTradeStoresXp(TestContext context) {
        createFloor(context);
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        VillagerEntity worker = spawnVillager(context, new BlockPos(1, 1, 2));
        WanderingTraderEntity target = context.spawnEntity(
            EntityType.WANDERING_TRADER,
            Vec3d.ofBottomCenter(new BlockPos(3, 1, 2)),
            SpawnReason.COMMAND
        );
        target.setAiDisabled(true);
        target.setDespawnDelay(1200);
        target.getOffers().clear();
        TradeOffer offer = new TradeOffer(
            new TradedItem(Items.EMERALD, 1),
            Optional.empty(),
            new ItemStack(Items.OAK_SAPLING),
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
            state.loadInputs(java.util.List.of(new ItemStack(Items.EMERALD))),
            "Wandering Trader XP test must load one exact input"
        );

        context.assertTrue(
            MerchantTradeExecutor.executeOne(context.getWorld(), worker, target, state, post),
            "The Wandering Trader trade must execute"
        );
        context.assertTrue(
            state.storedExperience() >= 3 && state.storedExperience() <= 6,
            "Wandering Trader player XP must be stored"
        );
        context.assertEquals(0, experienceValueNear(context, target), "Wandering Trader XP must not reach the floor");
        context.complete();
    }

    @GameTest
    public void storedXpPersistsAndReleasesExactlyOnce(TestContext context) {
        VillagerEntity worker = spawnVillager(context, new BlockPos(2, 1, 2));
        MerchantWorkerState original = ((MerchantWorker)worker).merchantVillager$getState();
        original.addStoredExperience(23);

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
        context.assertEquals(23, restored.storedExperience(), "Stored XP must survive entity NBT");

        restored.drainStoredExperience();
        restored.addStoredExperience(3);
        context.assertEquals(
            3,
            AutomatedTradeExperience.releaseStoredExperience(context.getWorld(), worker, restored),
            "Interaction release must return the drained amount"
        );
        context.assertEquals(0, restored.storedExperience(), "Interaction release must drain stored XP");
        context.assertEquals(3, experienceValueNear(context, worker), "Release must create equivalent vanilla XP");
        context.assertEquals(
            0,
            AutomatedTradeExperience.releaseStoredExperience(context.getWorld(), worker, restored),
            "A second interaction must not release XP twice"
        );
        context.assertEquals(3, experienceValueNear(context, worker), "A second release must create no additional XP");
        context.complete();
    }

    private static VillagerEntity spawnVillager(TestContext context, BlockPos position) {
        return context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(position),
            SpawnReason.COMMAND
        );
    }

    private static int experienceValueNear(TestContext context, net.minecraft.entity.Entity entity) {
        return context.getWorld().getEntitiesByClass(
            ExperienceOrbEntity.class,
            entity.getBoundingBox().expand(6.0),
            orb -> true
        ).stream().mapToInt(ExperienceOrbEntity::getValue).sum();
    }

    private static void createFloor(TestContext context) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 4; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}
