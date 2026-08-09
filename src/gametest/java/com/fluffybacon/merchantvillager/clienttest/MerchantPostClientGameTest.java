package com.fluffybacon.merchantvillager.clienttest;

import com.fluffybacon.merchantvillager.client.ClientCatalogueCache;
import com.fluffybacon.merchantvillager.network.CataloguePayload;
import com.fluffybacon.merchantvillager.registry.ModScreenHandlers;
import com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler;
import com.fluffybacon.merchantvillager.screen.client.MerchantPostScreen;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradedItem;

/**
 * Renders the real production Merchant screen without booting a world. World
 * generation is unrelated to the GUI and proved needlessly nondeterministic on
 * software-rendered CI runners; the server GameTests exercise the live gameplay
 * and container paths separately.
 */
@SuppressWarnings({"UnstableApiUsage", "DataFlowIssue"})
public final class MerchantPostClientGameTest implements FabricClientGameTest {
    private static final BlockPos POST_POS = new BlockPos(37, 72, -19);
    private static final UUID WORKER_UUID = UUID.fromString("9ab688e1-8e42-4f71-a9ec-3b98dd3d20d1");
    private static final UUID LIBRARIAN_UUID = UUID.fromString("5fd655ab-e667-46dd-91b6-ae6b633ea487");
    private static final UUID FARMER_UUID = UUID.fromString("7e67ec6e-bb15-4d84-94d7-d91f05021962");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1024, 768);
        context.runOnClient(client -> {
            client.options.getGuiScale().setValue(3);
            client.options.getMaxFps().setValue(30);
            client.options.getEnableVsync().setValue(false);
            client.onResolutionChanged();
        });

        BlockPos decodedPostPos = roundTripOpeningData(POST_POS);
        if (!decodedPostPos.equals(POST_POS) || decodedPostPos.equals(BlockPos.ORIGIN)) {
            throw new AssertionError("Merchant screen opening data lost its non-origin position");
        }

        context.setScreen(() -> createScreen(decodedPostPos, 7));
        context.waitForScreen(MerchantPostScreen.class);
        context.runOnClient(client -> {
            ClientCatalogueCache.accept(roundTripCatalogue(catalogue(decodedPostPos, 40)));
            // Neither another post nor an older packet may replace this session.
            ClientCatalogueCache.accept(roundTripCatalogue(catalogue(decodedPostPos.east(), 99)));
            ClientCatalogueCache.accept(roundTripCatalogue(catalogue(decodedPostPos, 39)));
            assertScreenState(client.currentScreen, decodedPostPos, 40);
        });
        context.waitTicks(2);
        context.takeScreenshot("merchant-post-non-origin-compact");

        // Reopening the same physical post starts a new cache session. A new
        // block entity can legitimately restart its revision counter at one.
        context.setScreen(() -> createScreen(decodedPostPos, 8));
        context.waitForScreen(MerchantPostScreen.class);
        context.runOnClient(client -> {
            if (ClientCatalogueCache.latest() != null) {
                throw new AssertionError("Replacing the screen did not clear the previous catalogue session");
            }
            ClientCatalogueCache.accept(roundTripCatalogue(catalogue(decodedPostPos, 1)));
            assertScreenState(client.currentScreen, decodedPostPos, 1);
        });
        context.waitTicks(2);

        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
        context.runOnClient(client -> {
            if (ClientCatalogueCache.latest() != null) {
                throw new AssertionError("Closing the Merchant screen did not clear its catalogue cache");
            }
        });
    }

    private static BlockPos roundTripOpeningData(BlockPos postPos) {
        RegistryByteBuf buffer = new RegistryByteBuf(
            Unpooled.buffer(), DynamicRegistryManager.of(Registries.REGISTRIES)
        );
        try {
            ModScreenHandlers.MERCHANT_POST.getPacketCodec().encode(buffer, postPos);
            return ModScreenHandlers.MERCHANT_POST.getPacketCodec().decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static CataloguePayload roundTripCatalogue(CataloguePayload payload) {
        RegistryByteBuf buffer = new RegistryByteBuf(
            Unpooled.buffer(), DynamicRegistryManager.of(Registries.REGISTRIES)
        );
        try {
            CataloguePayload.CODEC.encode(buffer, payload);
            return CataloguePayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static MerchantPostScreen createScreen(BlockPos postPos, int syncId) {
        PlayerInventory inventory = new PlayerInventory(null, new EntityEquipment());
        MerchantPostScreenHandler handler = ModScreenHandlers.MERCHANT_POST.create(
            syncId, inventory, postPos
        );
        handler.getSlot(0).setStack(new ItemStack(Items.EMERALD, 12));
        handler.getSlot(1).setStack(new ItemStack(Items.PAPER, 48));
        handler.getSlot(2).setStack(new ItemStack(Items.WHEAT, 40));
        handler.getSlot(3).setStack(new ItemStack(Items.BOOK, 4));
        handler.getSlot(4).setStack(new ItemStack(Items.BREAD, 6));
        return new MerchantPostScreen(
            handler, inventory, Text.translatable("block.merchant_villager.merchant_post")
        );
    }

    private static void assertScreenState(
        Object currentScreen, BlockPos expectedPostPos, int expectedRevision
    ) {
        if (!(currentScreen instanceof MerchantPostScreen screen)) {
            throw new AssertionError("The real Merchant Post screen did not remain open");
        }
        if (!screen.getScreenHandler().getPostPos().equals(expectedPostPos)) {
            throw new AssertionError("Screen handler received the wrong Merchant's Post position");
        }
        CataloguePayload payload = ClientCatalogueCache.latest();
        if (payload == null
            || !payload.postPos().equals(expectedPostPos)
            || payload.revision() != expectedRevision
            || payload.entries().size() != 4
            || payload.workerUuid().isEmpty()) {
            throw new AssertionError("Merchant catalogue was not scoped to the active screen session");
        }
        if (screen.getScreenHandler().getSlot(8).x + 16 > 320) {
            throw new AssertionError("Rightmost backpack slot exceeds the compact screen width");
        }
    }

    private static CataloguePayload catalogue(BlockPos postPos, int revision) {
        List<CataloguePayload.Entry> entries = List.of(
            entry(
                LIBRARIAN_UUID, "Librarian Esme", "minecraft:librarian", 0,
                new TradedItem(Items.PAPER, 24), Optional.empty(), Items.EMERALD, 1,
                true, false, 2, true, 48, 0, 'a', 16.0
            ),
            entry(
                LIBRARIAN_UUID, "Librarian Esme", "minecraft:librarian", 1,
                new TradedItem(Items.EMERALD, 5), Optional.of(new TradedItem(Items.BOOK)),
                Items.BOOKSHELF, 1, false, false, 0, false, 12, 4, 'b', 16.0
            ),
            entry(
                FARMER_UUID, "Farmer Rowan", "minecraft:farmer", 0,
                new TradedItem(Items.WHEAT, 20), Optional.empty(), Items.EMERALD, 1,
                true, false, 2, false, 40, 0, 'c', 25.0
            ),
            entry(
                FARMER_UUID, "Farmer Rowan", "minecraft:farmer", 1,
                new TradedItem(Items.EMERALD), Optional.empty(), Items.BREAD, 6,
                true, true, 6, false, 12, 0, 'd', 25.0
            )
        );
        CataloguePayload.WorkerStats stats = new CataloguePayload.WorkerStats(
            "Merchant Alden",
            18.0F,
            20.0F,
            4.0,
            List.of(new ItemStack(Items.PAPER, 3), new ItemStack(Items.EMERALD, 2)),
            Optional.of(LIBRARIAN_UUID),
            3,
            1,
            Optional.of(postPos.east()),
            "Ready (touching chest)"
        );
        return new CataloguePayload(
            postPos,
            revision,
            Optional.of(WORKER_UUID),
            "TRAVELLING_TO_TARGET",
            "Delivering approved trades",
            "",
            2,
            3,
            2,
            Optional.of(stats),
            entries
        );
    }

    private static CataloguePayload.Entry entry(
        UUID targetUuid,
        String targetName,
        String profession,
        int offerIndex,
        TradedItem firstInput,
        Optional<TradedItem> secondInput,
        Item output,
        int outputCount,
        boolean enabled,
        boolean coolingDown,
        int fundable,
        boolean selected,
        int storedFirst,
        int storedSecond,
        char fingerprintCharacter,
        double distanceSquared
    ) {
        OfferSnapshot offer = new OfferSnapshot(
            targetUuid,
            targetName,
            profession,
            3,
            offerIndex,
            firstInput,
            secondInput,
            new ItemStack(output, outputCount),
            1,
            12,
            distanceSquared,
            false,
            true,
            -1,
            String.valueOf(fingerprintCharacter).repeat(64)
        );
        return new CataloguePayload.Entry(
            offer,
            enabled,
            coolingDown,
            fundable,
            selected,
            storedFirst,
            storedSecond,
            firstInput.count(),
            secondInput.map(TradedItem::count).orElse(0)
        );
    }
}
