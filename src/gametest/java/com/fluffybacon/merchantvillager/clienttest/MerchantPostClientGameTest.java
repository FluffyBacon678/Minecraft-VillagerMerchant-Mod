package com.fluffybacon.merchantvillager.clienttest;

import com.fluffybacon.merchantvillager.client.ClientCatalogueCache;
import com.fluffybacon.merchantvillager.network.CataloguePayload;
import com.fluffybacon.merchantvillager.network.CatalogueDeltaPayload;
import com.fluffybacon.merchantvillager.network.WorkerTelemetryBounds;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.nbt.NbtCompound;
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
    private static final String LONG_PROVIDER_LIST = "Librarian L1 · ".repeat(40);

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
            CataloguePayload complete = catalogue(decodedPostPos, 40);
            CataloguePayload second = chunk(complete, 1, 2, complete.entries().subList(2, 4));
            CataloguePayload first = chunk(complete, 0, 2, complete.entries().subList(0, 2));
            ClientCatalogueCache.accept(roundTripCatalogue(second));
            if (ClientCatalogueCache.latest() != null) {
                throw new AssertionError("Incomplete out-of-order catalogue became visible");
            }
            ClientCatalogueCache.accept(roundTripCatalogue(first));
            if (ClientCatalogueCache.latest() == null
                || ClientCatalogueCache.latest().workerStats().isPresent()) {
                throw new AssertionError(
                    "Row-only baseline did not reassemble independently of worker telemetry"
                );
            }
            CataloguePayload telemetry = new CataloguePayload(
                complete.postPos(), complete.revision(), complete.workerUuid(), complete.workerState(),
                "Fresh telemetry", complete.lastFailure(), complete.targetCount(),
                complete.enabledCount(), complete.executableCount(), complete.workerStats(),
                false, 0, 0, List.of()
            );
            ClientCatalogueCache.accept(roundTripCatalogue(telemetry));
            if (ClientCatalogueCache.latest().entries().size() != 4
                || !ClientCatalogueCache.latest().status().equals("Fresh telemetry")
                || ClientCatalogueCache.latest().workerStats().isEmpty()
                || !ClientCatalogueCache.latest().entries().getFirst().offer().targetName()
                    .equals(LONG_PROVIDER_LIST)) {
                throw new AssertionError("Telemetry did not preserve the reassembled static rows");
            }
            CataloguePayload.Entry firstRow = complete.entries().get(0);
            CataloguePayload.Entry secondRow = complete.entries().get(1);
            CatalogueDeltaPayload.RowDelta stateOnly =
                CatalogueDeltaPayload.RowDelta.between(
                    firstRow.offer().fingerprint(),
                    new CataloguePayload.Entry(
                        firstRow.offer(), false, false, 0, false,
                        firstRow.storedFirstCount(), firstRow.storedSecondCount(),
                        firstRow.effectiveFirstCount(), firstRow.effectiveSecondCount()
                    ),
                    false
                );
            CatalogueDeltaPayload.RowDelta replacement =
                CatalogueDeltaPayload.RowDelta.between(
                    secondRow.offer().fingerprint(),
                    new CataloguePayload.Entry(
                        replacementOffer(secondRow.offer(), new ItemStack(Items.DIAMOND)),
                        true, false, 1, false,
                        secondRow.storedFirstCount(), secondRow.storedSecondCount(),
                        secondRow.effectiveFirstCount(), secondRow.effectiveSecondCount()
                    ),
                    true
                );
            ClientCatalogueCache.accept(roundTripDelta(new CatalogueDeltaPayload(
                decodedPostPos, 40, 41, 1, 2, List.of(replacement)
            )));
            if (ClientCatalogueCache.latest().revision() != 40) {
                throw new AssertionError("Incomplete out-of-order delta became visible");
            }
            ClientCatalogueCache.accept(roundTripDelta(new CatalogueDeltaPayload(
                decodedPostPos, 40, 41, 0, 2, List.of(stateOnly)
            )));
            if (ClientCatalogueCache.latest().revision() != 41
                || ClientCatalogueCache.latest().entries().get(0).enabled()
                || !ClientCatalogueCache.latest().entries().get(1).enabled()
                || !ClientCatalogueCache.latest().entries().get(1).offer().output().isOf(Items.DIAMOND)) {
                throw new AssertionError("Catalogue row delta did not apply atomically");
            }
            // A patch based on a baseline this client never held must fail closed.
            ClientCatalogueCache.accept(roundTripDelta(new CatalogueDeltaPayload(
                decodedPostPos, 39, 42, 0, 1, List.of(stateOnly)
            )));
            // Neither another post nor an older packet may replace this session.
            ClientCatalogueCache.accept(roundTripCatalogue(catalogue(decodedPostPos.east(), 99)));
            ClientCatalogueCache.accept(roundTripCatalogue(catalogue(decodedPostPos, 39)));
            assertScreenState(client.currentScreen, decodedPostPos, 41);
            assertHeavyCargoIsBounded(decodedPostPos);
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

    private static CatalogueDeltaPayload roundTripDelta(CatalogueDeltaPayload payload) {
        RegistryByteBuf buffer = new RegistryByteBuf(
            Unpooled.buffer(), DynamicRegistryManager.of(Registries.REGISTRIES)
        );
        try {
            CatalogueDeltaPayload.CODEC.encode(buffer, payload);
            return CatalogueDeltaPayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static OfferSnapshot replacementOffer(OfferSnapshot old, ItemStack output) {
        return new OfferSnapshot(
            old.targetUuid(), old.targetName(), old.profession(), old.villagerLevel(),
            old.offerIndex(), old.firstInput(), old.secondInput(), output,
            old.uses(), old.maxUses(), old.distanceSquared(), old.wanderingTrader(),
            old.targetAvailable(), old.despawnDelay(), old.fingerprint()
        );
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
        handler.getSlot(27).setStack(new ItemStack(Items.EMERALD, 6));
        handler.getSlot(28).setStack(new ItemStack(Items.PAPER, 24));
        if (handler.getSlot(27).canInsert(new ItemStack(Items.EMERALD))) {
            throw new AssertionError("Merchant Cargo slots must be take-only");
        }
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
                LIBRARIAN_UUID, LONG_PROVIDER_LIST, "minecraft:librarian", 0,
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
            9,
            4.0,
            List.of(new ItemStack(Items.PAPER, 3), new ItemStack(Items.EMERALD, 2)),
            false,
            0b10,
            Optional.of(LIBRARIAN_UUID),
            3,
            1,
            Optional.of(postPos.west()),
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

    private static CataloguePayload chunk(
        CataloguePayload base,
        int index,
        int count,
        List<CataloguePayload.Entry> entries
    ) {
        return new CataloguePayload(
            base.postPos(), base.revision(), base.workerUuid(), base.workerState(), base.status(),
            base.lastFailure(), base.targetCount(), base.enabledCount(), base.executableCount(),
            Optional.empty(), true, index, count, List.copyOf(entries)
        );
    }

    private static void assertHeavyCargoIsBounded(BlockPos postPos) {
        ItemStack componentHeavy = new ItemStack(Items.PAPER, 7);
        NbtCompound custom = new NbtCompound();
        custom.putString("payload", "x".repeat(60_000));
        componentHeavy.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
        DynamicRegistryManager registries = DynamicRegistryManager.of(Registries.REGISTRIES);
        WorkerTelemetryBounds.CargoView bounded = WorkerTelemetryBounds.summarizeCargo(
            registries, List.of(componentHeavy)
        );
        if (!bounded.summarized()
            || bounded.encodedBytes() > WorkerTelemetryBounds.MAX_ENCODED_CARGO_BYTES
            || bounded.cargo().size() != 1
            || !bounded.cargo().getFirst().isOf(Items.PAPER)
            || bounded.cargo().getFirst().getCount() != 7
            || bounded.cargo().getFirst().contains(DataComponentTypes.CUSTOM_DATA)) {
            throw new AssertionError("Component-heavy worker cargo was not safely summarized");
        }

        CataloguePayload.WorkerStats stats = new CataloguePayload.WorkerStats(
            "M".repeat(300),
            20.0F,
            20.0F,
            0,
            0.0,
            bounded.cargo(),
            bounded.summarized(),
            0,
            Optional.empty(),
            0,
            0,
            Optional.empty(),
            Optional.empty(),
            "Ready"
        );
        CataloguePayload telemetry = new CataloguePayload(
            postPos,
            42,
            Optional.of(WORKER_UUID),
            "IDLE",
            "Bounded",
            "",
            0,
            0,
            0,
            Optional.of(stats),
            false,
            0,
            0,
            List.of()
        );
        CataloguePayload decoded = roundTripCatalogue(telemetry);
        if (decoded.workerStats().isEmpty()
            || decoded.workerStats().get().name().length() > 128
            || !decoded.workerStats().get().cargoSummarized()
            || encodedCatalogueBytes(telemetry) > 128 * 1024) {
            throw new AssertionError("Bounded worker telemetry violated its codec limits");
        }
    }

    private static int encodedCatalogueBytes(CataloguePayload payload) {
        RegistryByteBuf buffer = new RegistryByteBuf(
            Unpooled.buffer(), DynamicRegistryManager.of(Registries.REGISTRIES)
        );
        try {
            CataloguePayload.CODEC.encode(buffer, payload);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
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
