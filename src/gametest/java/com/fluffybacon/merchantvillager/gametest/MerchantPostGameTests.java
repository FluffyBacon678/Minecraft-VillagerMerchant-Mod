package com.fluffybacon.merchantvillager.gametest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.screen.MerchantCargoInventory;
import com.fluffybacon.merchantvillager.inventory.ChestRoleMarker;
import com.fluffybacon.merchantvillager.inventory.OrphanedMarkerCleanupState;
import com.fluffybacon.merchantvillager.inventory.PendingMarkerRemovals;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

public final class MerchantPostGameTests {
    @GameTest
    public void merchantCargoSlotsLetPlayersTakeInputsAndRewards(TestContext context) {
        BlockPos postPos = new BlockPos(1, 1, 1);
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        VillagerEntity worker = context.spawnEntity(
            EntityType.VILLAGER,
            Vec3d.ofBottomCenter(new BlockPos(2, 1, 1)),
            SpawnReason.COMMAND
        );
        post.assignMerchant(worker.getUuid());
        var state = ((MerchantWorker)worker).merchantVillager$getState();
        context.assertTrue(state.add(new ItemStack(Items.PAPER, 5), false), "Input setup must fit cargo");
        context.assertTrue(state.add(new ItemStack(Items.EMERALD, 3), true), "Reward setup must fit cargo");

        MerchantCargoInventory cargo = new MerchantCargoInventory(post);
        ItemStack paper = cargo.removeStack(0, 2);
        ItemStack emeralds = cargo.removeStack(1);
        context.assertEquals(2, paper.getCount(), "Players must be able to take reserved input early");
        context.assertTrue(paper.isOf(Items.PAPER), "The exact input stack must be returned");
        context.assertEquals(3, cargo.getStack(0).getCount(), "Partial input withdrawal must remain exact");
        context.assertEquals(3, emeralds.getCount(), "Players must be able to take all earned rewards early");
        context.assertTrue(emeralds.isOf(Items.EMERALD), "The exact reward stack must be returned");
        context.assertTrue(cargo.getStack(1).isEmpty(), "Taken rewards must no longer be available for Export");
        context.assertFalse(state.isRewardSlot(1), "An emptied reward slot must clear its reward marker");
        context.complete();
    }

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
    public void identicalTradesShareGlobalApprovalWhileChangedTradesRemainDisabled(TestContext context) {
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
        context.assertTrue(
            post.isEnabled(secondSnapshot.fingerprint()),
            "The same global trade on another villager must inherit pre-approval"
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

    @GameTest(maxTicks = 80)
    public void adjacentChestsReceiveRolesAndImportApprovedMaterials(TestContext context) {
        BlockPos postPos = new BlockPos(3, 1, 3);
        BlockPos westChest = postPos.west();
        BlockPos eastChest = postPos.east();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(westChest, Blocks.CHEST);
        context.setBlockState(eastChest, Blocks.CHEST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        VillagerEntity target = targetWithPaperOffer(context, new BlockPos(3, 1, 5));
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(offer -> offer.targetUuid().equals(target.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);

        context.runAtTick(5, () -> {
            context.assertTrue(post.getImportChestPos().isPresent(), "An Import chest must be assigned");
            context.assertTrue(post.getExportChestPos().isPresent(), "An Export chest must be assigned");
            context.assertFalse(
                post.getImportChestPos().equals(post.getExportChestPos()),
                "Two logical chests must receive distinct roles"
            );
            context.assertTrue(post.getImportMarkerPos().isPresent(), "Import marker sign must be placed");
            context.assertTrue(post.getExportMarkerPos().isPresent(), "Export marker sign must be placed");
            context.assertEquals(
                post.getImportChestPos().orElseThrow().up(),
                post.getImportMarkerPos().orElseThrow(),
                "Import marker must stand directly on its chest"
            );
            context.assertEquals(
                post.getExportChestPos().orElseThrow().up(),
                post.getExportMarkerPos().orElseThrow(),
                "Export marker must stand directly on its chest"
            );
            assertMarker(context, post.getImportMarkerPos().orElseThrow(), "[Import]", "");
            assertMarker(context, post.getExportMarkerPos().orElseThrow(), "[Export]", "");
            Inventory source = post.getImportInventory(context.getWorld());
            context.assertTrue(source != null, "Assigned Import inventory must resolve");
            source.setStack(0, new ItemStack(Items.PAPER, 12));
            source.markDirty();
        });
        context.runAtTick(30, () -> {
            context.assertEquals(
                1,
                post.count(Items.PAPER),
                "Import must stage one approved execution instead of draining the chest"
            );
            Inventory source = post.getImportInventory(context.getWorld());
            context.assertTrue(source != null, "Assigned Import inventory must remain available");
            context.assertEquals(11, source.count(Items.PAPER), "Excess Import stock must remain in its chest");
            Inventory export = post.getExportInventory(context.getWorld());
            context.assertTrue(export != null, "Assigned Export inventory must resolve");
            context.assertEquals(0, export.count(Items.PAPER), "Separate Export must never be drained as Import");
            context.complete();
        });
    }

    @GameTest(maxTicks = 80)
    public void oneChestIsDualAndItsBrokenMarkerIsRecreated(TestContext context) {
        BlockPos postPos = new BlockPos(2, 1, 2);
        BlockPos chestPos = postPos.east();
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        final BlockPos[] firstMarker = new BlockPos[1];
        context.runAtTick(5, () -> {
            context.assertEquals(
                post.getImportChestPos(),
                post.getExportChestPos(),
                "One logical chest must be Dual-Purpose"
            );
            firstMarker[0] = post.getImportMarkerPos().orElseThrow();
            context.assertEquals(
                post.getImportChestPos().orElseThrow().up(),
                firstMarker[0],
                "Dual marker must stand directly on its chest"
            );
            assertMarker(context, firstMarker[0], "[Import]", "[Export]");
            context.assertEquals(
                Optional.of(firstMarker[0]),
                ChestRoleMarker.reconcile(
                    context.getWorld(),
                    context.getAbsolutePos(postPos),
                    context.getAbsolutePos(chestPos),
                    null,
                    ChestRoleMarker.Role.DUAL,
                    Set.of()
                ),
                "An owned marker must be adopted after a crash before marker-position persistence"
            );
            context.setBlockState(context.getRelativePos(firstMarker[0]), Blocks.AIR);
        });
        context.runAtTick(35, () -> {
            BlockPos replacement = post.getImportMarkerPos().orElseThrow();
            assertMarker(context, replacement, "[Import]", "[Export]");
            context.complete();
        });
    }

    @GameTest(maxTicks = 80)
    public void distantPersistedMarkerCannotFreezeRolesOrEnterCleanupQueue(TestContext context) {
        BlockPos postPos = new BlockPos(3, 1, 3);
        BlockPos firstChest = postPos.east();
        BlockPos secondChest = postPos.west();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(firstChest, Blocks.CHEST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);

        context.runAtTick(5, () -> {
            BlockPos realMarker = post.getImportMarkerPos().orElseThrow();
            context.getWorld().removeBlock(realMarker, false);
            BlockPos unavailableMarker = context.getAbsolutePos(postPos).add(512, 0, 512);
            context.assertFalse(
                context.getWorld().isChunkLoaded(unavailableMarker),
                "Synthetic old marker chunk must begin unavailable"
            );
            setPrivateBlockPos(post, "importMarker", unavailableMarker);
            context.setBlockState(secondChest, Blocks.CHEST);
            post.requestChestRescan();
        });
        context.runAtTick(10, () -> {
            BlockPos unavailableMarker = context.getAbsolutePos(postPos).add(512, 0, 512);
            context.assertFalse(
                post.getImportChestPos().equals(post.getExportChestPos()),
                "A stale unloaded pointer must not freeze a valid role transition"
            );
            context.assertFalse(
                post.getImportMarkerPos().filter(unavailableMarker::equals).isPresent(),
                "The invalid stale marker pointer must be replaced"
            );
            context.assertEquals(
                0,
                post.getPendingMarkerRemovalCount(),
                "Out-of-radius persisted positions must never enter the cleanup queue"
            );
            context.complete();
        });
    }

    @GameTest(maxTicks = 80)
    public void destroyedPostCleansOrPersistsCrossChunkMarkerWork(TestContext context) {
        var world = context.getWorld();
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos postPos = null;
        BlockPos chestPos = null;
        BlockPos markerPos = null;
        for (int offset = 512; offset <= 8192; offset += 512) {
            int x = ((origin.getX() + offset) & ~15) + 15;
            int z = ((origin.getZ() + offset) & ~15) + 8;
            BlockPos candidatePost = new BlockPos(x, origin.getY() + 1, z);
            BlockPos candidateChest = candidatePost.east();
            BlockPos candidateMarker = candidateChest.east();
            if (!world.isChunkLoaded(candidatePost)
                && !world.isChunkLoaded(candidateMarker)) {
                postPos = candidatePost;
                chestPos = candidateChest;
                markerPos = candidateMarker;
                break;
            }
        }
        context.assertTrue(postPos != null, "Test must find an unloaded cross-chunk fixture");
        BlockPos finalPostPos = postPos;
        BlockPos finalChestPos = chestPos;
        BlockPos finalMarkerPos = markerPos;
        world.getChunk(finalPostPos);
        context.assertFalse(
            world.isChunkLoaded(finalMarkerPos),
            "Loading the post chunk must leave the adjacent marker chunk unavailable"
        );
        world.setBlockState(finalPostPos, ModBlocks.MERCHANT_POST.getDefaultState());
        MerchantPostBlockEntity post = (MerchantPostBlockEntity)world.getBlockEntity(finalPostPos);
        context.assertTrue(post != null, "Remote Merchant's Post must create its block entity");
        setPrivateBlockPos(post, "importChest", finalChestPos);
        setPrivateBlockPos(post, "exportChest", finalChestPos);
        setPrivateBlockPos(post, "importMarker", finalMarkerPos);
        setPrivateBlockPos(post, "exportMarker", finalMarkerPos);
        PendingMarkerRemovals.Entry cleanup = new PendingMarkerRemovals.Entry(
            finalMarkerPos, finalChestPos, ChestRoleMarker.Role.DUAL
        );
        context.assertTrue(
            OrphanedMarkerCleanupState.isValid(finalPostPos, cleanup),
            "Cross-chunk cleanup tuple must satisfy the ownership radius"
        );
        context.assertEquals(world, post.getWorld(), "Remote post must be attached to this world");
        context.assertEquals(
            Optional.of(finalChestPos), post.getImportChestPos(), "Synthetic Import pointer"
        );
        context.assertEquals(
            Optional.of(finalMarkerPos), post.getImportMarkerPos(), "Synthetic marker pointer"
        );

        post.onPostDestroyed();
        boolean markerLoadedAfterDestroy = world.isChunkLoaded(finalMarkerPos);
        boolean cleanupQueued = OrphanedMarkerCleanupState.contains(
            world, finalPostPos, cleanup
        );
        if (markerLoadedAfterDestroy) {
            context.assertFalse(
                cleanupQueued,
                "Already-available marker cleanup must finish without a stale ledger entry"
            );
        } else {
            context.assertTrue(
                cleanupQueued,
                "Unavailable marker cleanup must outlive the destroyed block entity"
            );
        }
        world.removeBlock(finalPostPos, false);

        world.getChunk(finalMarkerPos);
        OrphanedMarkerCleanupState.onChunkLoaded(world, new ChunkPos(finalMarkerPos));
        context.assertFalse(
            OrphanedMarkerCleanupState.contains(world, finalPostPos, cleanup),
            "World-owned cleanup must retire after the marker chunk becomes available"
        );
        context.complete();
    }

    @GameTest(maxTicks = 80)
    public void activeWorkerCargoPreventsImportFromBlockingRecovery(TestContext context) {
        BlockPos postPos = new BlockPos(2, 1, 2);
        BlockPos chestPos = postPos.east();
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);
        VillagerEntity worker = targetWithPaperOffer(context, new BlockPos(7, 1, 2));
        post.refreshCatalogue(true);
        String fingerprint = post.getOffers().stream()
            .filter(offer -> offer.targetUuid().equals(worker.getUuid()))
            .findFirst()
            .orElseThrow()
            .fingerprint();
        post.setOfferEnabledInternal(fingerprint, true);
        var workerState = ((MerchantWorker)worker).merchantVillager$getState();
        workerState.bindPost(context.getWorld(), context.getAbsolutePos(postPos));
        context.assertTrue(
            workerState.add(new ItemStack(Items.WHEAT), false),
            "Recovery cargo must load"
        );
        post.assignMerchant(worker.getUuid());

        context.runAtTick(5, () -> {
            Inventory source = post.getImportInventory(context.getWorld());
            context.assertTrue(source != null, "Dual Import must resolve");
            source.setStack(0, new ItemStack(Items.PAPER, 12));
            source.markDirty();
            post.requestChestRescan();
        });
        context.runAtTick(15, () -> {
            Inventory source = post.getImportInventory(context.getWorld());
            context.assertTrue(source != null, "Dual Import must remain assigned");
            context.assertEquals(12, source.count(Items.PAPER), "Active cargo must pause automatic imports");
            context.assertEquals(0, post.count(Items.PAPER), "Recovery space must remain unoccupied");
            context.assertTrue(workerState.hasCargo(), "The test worker must still carry recovery cargo");
            context.complete();
        });
    }

    @GameTest(maxTicks = 40)
    public void breakingGeneratedMarkerSuppressesItsFreeSignDrop(TestContext context) {
        BlockPos postPos = new BlockPos(2, 1, 2);
        BlockPos chestPos = postPos.east();
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        context.setBlockState(postPos, ModBlocks.MERCHANT_POST);
        context.setBlockState(chestPos, Blocks.CHEST);
        MerchantPostBlockEntity post = context.getBlockEntity(postPos, MerchantPostBlockEntity.class);

        context.runAtTick(5, () -> {
            BlockPos markerPos = post.getImportMarkerPos().orElseThrow();
            var state = context.getWorld().getBlockState(markerPos);
            SignBlockEntity sign = (SignBlockEntity)context.getWorld().getBlockEntity(markerPos);
            context.assertTrue(ChestRoleMarker.isGeneratedMarker(sign), "Marker must carry ownership data");
            context.assertTrue(
                Block.getDroppedStacks(state, context.getWorld(), markerPos, sign).isEmpty(),
                "Generated marker loot must be empty at the block-loot boundary"
            );
            BlockPos ordinaryPos = context.getAbsolutePos(new BlockPos(5, 1, 5));
            context.getWorld().setBlockState(ordinaryPos, Blocks.OAK_SIGN.getDefaultState());
            SignBlockEntity ordinary = (SignBlockEntity)context.getWorld().getBlockEntity(ordinaryPos);
            context.assertFalse(
                Block.getDroppedStacks(
                    context.getWorld().getBlockState(ordinaryPos),
                    context.getWorld(),
                    ordinaryPos,
                    ordinary
                ).isEmpty(),
                "Ordinary player signs must retain vanilla loot"
            );
            context.getWorld().breakBlock(markerPos, true, null);
            context.assertEquals(
                0,
                context.getWorld().getEntitiesByClass(
                    ItemEntity.class,
                    new Box(markerPos).expand(1.0),
                    item -> item.getStack().isOf(Items.OAK_SIGN)
                ).size(),
                "An automatically generated marker must not become a farmable sign item"
            );
            context.complete();
        });
    }

    private static void assertMarker(
        TestContext context, BlockPos absoluteMarker, String first, String second
    ) {
        context.assertTrue(
            context.getWorld().getBlockEntity(absoluteMarker) instanceof SignBlockEntity,
            "Assigned chest marker must be a sign"
        );
        SignBlockEntity sign = (SignBlockEntity)context.getWorld().getBlockEntity(absoluteMarker);
        context.assertEquals(first, sign.getText(true).getMessage(0, false).getString(), "Marker line 1");
        context.assertEquals(second, sign.getText(true).getMessage(1, false).getString(), "Marker line 2");
    }

    private static void setPrivateBlockPos(
        MerchantPostBlockEntity post, String fieldName, BlockPos value
    ) {
        try {
            var field = MerchantPostBlockEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(post, value.toImmutable());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to configure persisted marker regression", exception);
        }
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
