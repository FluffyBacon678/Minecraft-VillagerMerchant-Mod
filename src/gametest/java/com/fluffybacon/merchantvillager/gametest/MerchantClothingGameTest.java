package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.merchant.MerchantClothing;
import com.fluffybacon.merchantvillager.merchant.MerchantInteractions;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public final class MerchantClothingGameTest {
    @GameTest(maxTicks = 100)
    public void survivalDyeColorsAdultMerchantAndConsumesExactlyOne(TestContext context) {
        VillagerEntity merchant = spawnMerchant(context, new BlockPos(2, 1, 2));
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(2, 1, 3))));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BLUE_DYE, 3));

        ActionResult result = MerchantInteractions.tryDye(
            player, context.getWorld(), Hand.MAIN_HAND, merchant, true
        );
        context.assertEquals(ActionResult.SUCCESS, result, "Fresh dye must handle the interaction");
        context.assertEquals(DyeColor.BLUE, MerchantClothing.get(merchant), "Blue must persist on worker");
        context.assertEquals(2, player.getStackInHand(Hand.MAIN_HAND).getCount(), "Consume one dye");

        ActionResult repeated = MerchantInteractions.tryDye(
            player, context.getWorld(), Hand.MAIN_HAND, merchant, true
        );
        context.assertEquals(ActionResult.PASS, repeated, "Same color must preserve normal interaction");
        context.assertEquals(2, player.getStackInHand(Hand.MAIN_HAND).getCount(), "Same color is free");

        NbtWriteView write = NbtWriteView.create(
            ErrorReporter.EMPTY,
            context.getWorld().getRegistryManager()
        );
        merchant.writeData(write);
        VillagerEntity restored = EntityType.VILLAGER.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(restored != null, "A Villager must be constructible for the reload check");
        restored.readData(NbtReadView.create(
            ErrorReporter.EMPTY,
            context.getWorld().getRegistryManager(),
            write.getNbt()
        ));
        context.assertEquals(DyeColor.BLUE, MerchantClothing.get(restored), "Color must survive entity NBT");
        context.complete();
    }

    @GameTest(maxTicks = 100)
    public void creativeDyeIsNotConsumedAndInvalidTargetsIgnoreIt(TestContext context) {
        VillagerEntity merchant = spawnMerchant(context, new BlockPos(2, 1, 2));
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        player.getAbilities().creativeMode = true;
        player.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(2, 1, 3))));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LIME_DYE, 1));

        context.assertEquals(
            ActionResult.SUCCESS,
            MerchantInteractions.tryDye(player, context.getWorld(), Hand.MAIN_HAND, merchant, true),
            "Creative dye must work"
        );
        context.assertEquals(DyeColor.LIME, MerchantClothing.get(merchant), "Lime must apply");
        context.assertEquals(1, player.getStackInHand(Hand.MAIN_HAND).getCount(), "Creative keeps dye");

        VillagerEntity child = spawnMerchant(context, new BlockPos(3, 1, 2));
        child.setBaby(true);
        context.assertEquals(
            ActionResult.PASS,
            MerchantInteractions.tryDye(player, context.getWorld(), Hand.MAIN_HAND, child, true),
            "Child Merchants cannot be dyed"
        );
        context.assertTrue(MerchantClothing.get(child) == null, "Child color remains undyed");

        VillagerEntity ordinary = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(4, 1, 2)),
            SpawnReason.COMMAND
        );
        context.assertEquals(
            ActionResult.PASS,
            MerchantInteractions.tryDye(player, context.getWorld(), Hand.MAIN_HAND, ordinary, false),
            "Ordinary villagers ignore Merchant dyeing"
        );
        context.assertTrue(MerchantClothing.get(ordinary) == null, "Ordinary villager remains undyed");
        context.complete();
    }

    private static VillagerEntity spawnMerchant(TestContext context, BlockPos relativePos) {
        VillagerEntity villager = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(relativePos),
            SpawnReason.COMMAND
        );
        villager.setVillagerData(
            villager.getVillagerData().withProfession(context.getWorld().getRegistryManager()
                .getOrThrow(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION)
                .getOrThrow(ModVillagerProfessions.MERCHANT_KEY))
        );
        return villager;
    }
}
