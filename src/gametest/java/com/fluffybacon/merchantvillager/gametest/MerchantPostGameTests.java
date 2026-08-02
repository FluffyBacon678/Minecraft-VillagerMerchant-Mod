package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

public final class MerchantPostGameTests {
    @GameTest
    public void postPlacesWithExactlyTwentySevenSlots(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.MERCHANT_POST);
        context.expectBlock(ModBlocks.MERCHANT_POST, pos);
        MerchantPostBlockEntity post = context.getBlockEntity(pos, MerchantPostBlockEntity.class);
        context.assertEquals(27, post.size(), "Merchant's Post must have exactly 27 slots");
        context.complete();
    }

    @GameTest
    public void undiscoveredRandomItemIsRejected(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(pos, MerchantPostBlockEntity.class);
        context.assertFalse(post.isValid(0, new ItemStack(Items.DIRT)), "Dirt must be rejected without a matching discovered offer");
        context.complete();
    }

    @GameTest
    public void inventoryAndPermissionsRoundTripThroughBlockEntityNbt(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(pos, MerchantPostBlockEntity.class);
        VillagerEntity target = targetWithPaperOffer(context, new BlockPos(2, 1, 1));
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(offer -> offer.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        post.setStack(0, new ItemStack(Items.PAPER, 37));

        var lookup = context.getWorld().getRegistryManager();
        var nbt = post.createNbtWithIdentifyingData(lookup);
        BlockEntity decoded = BlockEntity.createFromNbt(
            context.getAbsolutePos(pos),
            context.getBlockState(pos),
            nbt,
            lookup
        );
        context.assertTrue(decoded instanceof MerchantPostBlockEntity, "Saved post must decode as its real type");
        MerchantPostBlockEntity restored = (MerchantPostBlockEntity)decoded;
        context.assertEquals(37, restored.count(Items.PAPER), "All 27-slot inventory data must persist");
        context.assertTrue(restored.isEnabled(fingerprint), "V/X permission must persist");
        context.complete();
    }

    @GameTest(maxTicks = 50)
    public void hopperInsertionUsesTheSameOfferFilter(TestContext context) {
        BlockPos postPos = new BlockPos(1, 1, 1);
        BlockPos hopperPos = postPos.up();
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        targetWithPaperOffer(context, new BlockPos(3, 1, 1));
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        post.refreshCatalogue(true);
        context.assertTrue(
            post.canInsert(0, new ItemStack(Items.PAPER), Direction.UP),
            "Top must accept a discovered trade input"
        );
        context.assertTrue(
            post.canInsert(0, new ItemStack(Items.PAPER), Direction.NORTH),
            "Horizontal sides must accept a discovered trade input"
        );
        context.assertFalse(
            post.canInsert(0, new ItemStack(Items.PAPER), Direction.DOWN),
            "Bottom is extraction-only"
        );
        context.setBlockState(
            hopperPos,
            Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING, Direction.DOWN)
        );
        HopperBlockEntity hopper = context.getBlockEntity(hopperPos, HopperBlockEntity.class);
        hopper.setStack(0, new ItemStack(Items.PAPER, 4));
        hopper.setStack(1, new ItemStack(Items.DIRT, 4));

        context.runAtTick(32, () -> {
            context.assertEquals(4, post.count(Items.PAPER), "Matching paper must enter through the top");
            context.assertEquals(0, post.count(Items.DIRT), "Hopper must not bypass the trade-input filter");
            context.assertEquals(4, hopper.count(Items.DIRT), "Rejected hopper items must remain in the hopper");
            context.complete();
        });
    }

    @GameTest(maxTicks = 20)
    public void breakingPostDropsStoredMaterialsExactlyOnce(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(pos, MerchantPostBlockEntity.class);
        post.setStack(0, new ItemStack(Items.PAPER, 7));
        context.setBlockState(pos, Blocks.AIR);

        context.runAtTick(2, () -> {
            BlockPos absolute = context.getAbsolutePos(pos);
            int dropped = context.getWorld().getEntitiesByClass(
                ItemEntity.class,
                new Box(absolute).expand(2.0),
                item -> item.getStack().isOf(Items.PAPER)
            ).stream().mapToInt(item -> item.getStack().getCount()).sum();
            context.assertEquals(7, dropped, "Stored material must drop once when the post breaks");
            context.complete();
        });
    }

    @GameTest
    public void componentSensitiveInputFilterRejectsChangedStacksWithoutDeletingStoredItems(
        TestContext context
    ) {
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        VillagerEntity target = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(2, 1, 1)),
            SpawnReason.COMMAND
        );
        target.setAiDisabled(true);
        target.getOffers().clear();
        TradedItem namedDiamond = new TradedItem(Items.DIAMOND, 1).withComponents(
            builder -> builder.add(DataComponentTypes.CUSTOM_NAME, Text.literal("Approved"))
        );
        target.getOffers().add(new TradeOffer(
            namedDiamond,
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        ));
        post.refreshCatalogue(true);

        ItemStack matching = new ItemStack(Items.DIAMOND);
        matching.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Approved"));
        ItemStack changed = new ItemStack(Items.DIAMOND);
        changed.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Changed"));
        context.assertTrue(post.isValid(0, matching), "Required custom-name component must match");
        context.assertFalse(post.isValid(0, changed), "Changed component must be rejected");
        context.assertFalse(
            post.isValid(0, new ItemStack(Items.DIAMOND)),
            "Missing required component must be rejected"
        );

        post.setStack(0, matching.copyWithCount(3));
        target.getOffers().clear();
        post.refreshCatalogue(true);
        context.assertEquals(
            3,
            post.getStack(0).getCount(),
            "Stored input must remain after its discovered offer disappears"
        );
        context.assertTrue(
            ItemStack.areItemsAndComponentsEqual(matching, post.getStack(0)),
            "Stored component data must remain intact"
        );
        context.complete();
    }

    @GameTest
    public void changedAndIdenticalLookingOffersHaveSafeIndependentPermissions(TestContext context) {
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        VillagerEntity first = targetWithPaperOffer(context, new BlockPos(2, 1, 1));
        VillagerEntity second = targetWithPaperOffer(context, new BlockPos(3, 1, 1));
        post.refreshCatalogue(true);
        var firstSnapshot = post.getOffers().stream()
            .filter(offer -> offer.targetUuid().equals(first.getUuid()))
            .findFirst()
            .orElseThrow();
        var secondSnapshot = post.getOffers().stream()
            .filter(offer -> offer.targetUuid().equals(second.getUuid()))
            .findFirst()
            .orElseThrow();
        context.assertFalse(
            firstSnapshot.fingerprint().equals(secondSnapshot.fingerprint()),
            "Identical-looking offers from different target UUIDs must remain separate"
        );
        post.setOfferEnabledInternal(firstSnapshot.fingerprint(), true);
        context.assertTrue(post.isEnabled(firstSnapshot.fingerprint()), "Explicit V must enable one exact offer");
        context.assertFalse(
            post.isEnabled(secondSnapshot.fingerprint()),
            "An identical-looking offer on another target must remain X"
        );

        first.getOffers().clear();
        first.getOffers().add(new TradeOffer(
            new TradedItem(Items.WHEAT, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        ));
        post.refreshCatalogue(true);
        String changedFingerprint = post.getOffers().stream()
            .filter(offer -> offer.targetUuid().equals(first.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        context.assertFalse(
            changedFingerprint.equals(firstSnapshot.fingerprint()),
            "Materially changed offer must receive a new identity"
        );
        context.assertFalse(
            post.isEnabled(changedFingerprint),
            "A materially changed offer must default back to X"
        );
        context.complete();
    }

    private static VillagerEntity targetWithPaperOffer(TestContext context, BlockPos pos) {
        VillagerEntity target = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(pos),
            SpawnReason.COMMAND
        );
        target.setAiDisabled(true);
        target.getOffers().clear();
        target.getOffers().add(new TradeOffer(
            new TradedItem(Items.PAPER, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD),
            8,
            1,
            0.05F
        ));
        return target;
    }
}
