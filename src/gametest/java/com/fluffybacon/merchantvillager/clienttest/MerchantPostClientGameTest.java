package com.fluffybacon.merchantvillager.clienttest;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.client.ClientCatalogueCache;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import com.fluffybacon.merchantvillager.screen.client.MerchantPostScreen;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

@SuppressWarnings("UnstableApiUsage")
public final class MerchantPostClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1024, 768);
        context.runOnClient(client -> {
            client.options.getGuiScale().setValue(3);
            client.onResolutionChanged();
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksDownload();
            BlockPos postPos = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().getFirst();
                ServerWorld world = server.getOverworld();
                BlockPos position = player.getBlockPos().add(3, 0, 2);
                world.setBlockState(position.down(), Blocks.STONE.getDefaultState());
                world.setBlockState(position, ModBlocks.MERCHANT_POST.getDefaultState());
                world.setBlockState(position.east(), Blocks.CHEST.getDefaultState());
                MerchantPostBlockEntity post = (MerchantPostBlockEntity)world.getBlockEntity(position);
                if (post == null) {
                    throw new AssertionError("Merchant's Post block entity was not created");
                }
                post.setStack(0, new ItemStack(Items.EMERALD, 12));
                post.setStack(1, new ItemStack(Items.PAPER, 48));
                post.setStack(2, new ItemStack(Items.WHEAT, 40));
                post.markDirty();
                VillagerEntity worker = spawnVillager(
                    world,
                    position.south(),
                    "Merchant Alden",
                    ModVillagerProfessions.MERCHANT_KEY
                );
                worker.reinitializeBrain(world);
                worker.getBrain().remember(
                    MemoryModuleType.JOB_SITE,
                    GlobalPos.create(world.getRegistryKey(), position)
                );
                MerchantWorkerState workerState = ((MerchantWorker)worker).merchantVillager$getState();
                workerState.bindPost(world, position);
                post.assignMerchant(worker.getUuid());
                worker.setAiDisabled(true);

                VillagerEntity librarian = spawnVillager(
                    world,
                    position.east(4),
                    "Librarian Esme",
                    VillagerProfession.LIBRARIAN
                );
                librarian.getOffers().clear();
                librarian.getOffers().add(new TradeOffer(
                    new TradedItem(Items.PAPER, 24),
                    Optional.empty(),
                    new ItemStack(Items.EMERALD),
                    12,
                    2,
                    0.05F
                ));
                librarian.getOffers().add(new TradeOffer(
                    new TradedItem(Items.EMERALD, 3),
                    Optional.empty(),
                    new ItemStack(Items.BOOKSHELF),
                    12,
                    2,
                    0.05F
                ));

                VillagerEntity farmer = spawnVillager(
                    world,
                    position.west(4),
                    "Farmer Rowan",
                    VillagerProfession.FARMER
                );
                farmer.getOffers().clear();
                farmer.getOffers().add(new TradeOffer(
                    new TradedItem(Items.WHEAT, 20),
                    Optional.empty(),
                    new ItemStack(Items.EMERALD),
                    12,
                    2,
                    0.05F
                ));
                farmer.getOffers().add(new TradeOffer(
                    new TradedItem(Items.EMERALD),
                    Optional.empty(),
                    new ItemStack(Items.BREAD, 6),
                    12,
                    2,
                    0.05F
                ));
                post.refreshCatalogue(true);
                for (int index = 0; index < post.getOffers().size(); index++) {
                    post.setOfferEnabledInternal(
                        post.getOffers().get(index).fingerprint(),
                        index != 1
                    );
                }
                post.setPaused(true);
                player.openHandledScreen(post);
                post.sendCatalogue(player);
                return position.toImmutable();
            });

            if (postPos.equals(BlockPos.ORIGIN)) {
                throw new AssertionError("Regression setup must open a non-origin Merchant's Post");
            }
            context.waitForScreen(MerchantPostScreen.class);
            context.waitFor(client -> ClientCatalogueCache.latest() != null
                && ClientCatalogueCache.latest().entries().size() == 4
                && ClientCatalogueCache.latest().workerUuid().isPresent());
            context.waitFor(client -> client.player != null
                && client.player.currentScreenHandler.getSlot(0).getStack().isOf(Items.EMERALD));
            context.runOnClient(client -> {
                if (!(client.currentScreen instanceof MerchantPostScreen screen)) {
                    throw new AssertionError("Merchant Post screen did not remain open");
                }
                if (!screen.getScreenHandler().getPostPos().equals(postPos)) {
                    throw new AssertionError("Screen handler received the wrong post position");
                }
                if (!ClientCatalogueCache.latest().postPos().equals(postPos)) {
                    throw new AssertionError("Catalogue payload was not scoped to the opened post");
                }
                int scaledWidth = client.getWindow().getScaledWidth();
                if (scaledWidth < 320 || scaledWidth >= 414) {
                    throw new AssertionError(
                        "Expected the compact-width regression window, got " + scaledWidth
                    );
                }
                if (screen.getScreenHandler().getSlot(8).x + 16 > 320) {
                    throw new AssertionError("Rightmost backpack slot exceeds the compact screen width");
                }
            });
            context.waitTicks(2);
            context.takeScreenshot("merchant-post-non-origin-compact");

            int previousRevision = context.computeOnClient(
                client -> ClientCatalogueCache.latest().revision()
            );
            singleplayer.getServer().runOnServer(server ->
                server.getPlayerManager().getPlayerList().getFirst().closeHandledScreen()
            );
            context.waitForScreen(null);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().getFirst();
                if (world.getBlockEntity(postPos) instanceof MerchantPostBlockEntity oldPost) {
                    oldPost.clear();
                }
                world.removeBlock(postPos, false);
                world.setBlockState(postPos, ModBlocks.MERCHANT_POST.getDefaultState());
                MerchantPostBlockEntity replacement =
                    (MerchantPostBlockEntity)world.getBlockEntity(postPos);
                if (replacement == null) {
                    throw new AssertionError("Replacement Merchant's Post was not created");
                }
                replacement.setStack(0, new ItemStack(Items.EMERALD));
                replacement.refreshCatalogue(true);
                player.openHandledScreen(replacement);
                replacement.sendCatalogue(player);
            });
            context.waitForScreen(MerchantPostScreen.class);
            context.waitFor(client -> ClientCatalogueCache.latest() != null
                && ClientCatalogueCache.latest().postPos().equals(postPos)
                && ClientCatalogueCache.latest().revision() < previousRevision);

            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().getFirst();
                world.getEntitiesByClass(
                    VillagerEntity.class,
                    new Box(postPos).expand(16.0),
                    villager -> !villager.getVillagerData().profession()
                        .matchesKey(ModVillagerProfessions.MERCHANT_KEY)
                ).forEach(VillagerEntity::discard);
                player.closeHandledScreen();
                Vec3d cameraPosition = Vec3d.ofBottomCenter(postPos.south(4));
                player.teleport(
                    world,
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z,
                    Set.of(),
                    180.0F,
                    0.0F,
                    true
                );
            });
            context.waitForScreen(null);
            context.runOnClient(client -> client.options.hudHidden = true);
            singleplayer.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("merchant-villager-original-outfit");
        }
    }

    private static VillagerEntity spawnVillager(
        ServerWorld world,
        BlockPos position,
        String name,
        net.minecraft.registry.RegistryKey<VillagerProfession> profession
    ) {
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.COMMAND);
        if (villager == null) {
            throw new AssertionError("Could not create villager " + name);
        }
        villager.setPosition(Vec3d.ofBottomCenter(position));
        villager.setCustomName(Text.literal(name));
        villager.setCustomNameVisible(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(
            world.getRegistryManager(),
            profession
        ));
        villager.setAiDisabled(true);
        if (!world.spawnEntity(villager)) {
            throw new AssertionError("Could not spawn villager " + name);
        }
        return villager;
    }
}
