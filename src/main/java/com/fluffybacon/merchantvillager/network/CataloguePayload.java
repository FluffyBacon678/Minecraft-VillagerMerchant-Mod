package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradedItem;

public record CataloguePayload(
    BlockPos postPos,
    int revision,
    Optional<UUID> workerUuid,
    String workerState,
    String status,
    String lastFailure,
    int targetCount,
    int enabledCount,
    int executableCount,
    Optional<WorkerStats> workerStats,
    boolean fullCatalogue,
    int chunkIndex,
    int chunkCount,
    List<Entry> entries
) implements CustomPayload {
    public static final Id<CataloguePayload> ID = new Id<>(MerchantVillagerMod.id("catalogue"));
    public static final PacketCodec<RegistryByteBuf, CataloguePayload> CODEC =
        PacketCodec.ofStatic(CataloguePayload::write, CataloguePayload::read);
    public static final int MAX_ENTRIES_PER_CHUNK = 64;
    public static final int MAX_CHUNKS = 256;
    private static final int MAX_SUMMARY_COUNT = 1_000_000;
    private static final int MAX_CARGO_SLOTS = WorkerTelemetryBounds.MAX_CARGO_SLOTS;

    public CataloguePayload {
        workerState = WorkerTelemetryBounds.clamp(workerState, 64);
        status = WorkerTelemetryBounds.clamp(status, 256);
        lastFailure = WorkerTelemetryBounds.clamp(lastFailure, 256);
        entries = List.copyOf(entries);
    }

    public CataloguePayload(
        BlockPos postPos,
        int revision,
        Optional<UUID> workerUuid,
        String workerState,
        String status,
        String lastFailure,
        int targetCount,
        int enabledCount,
        int executableCount,
        Optional<WorkerStats> workerStats,
        List<Entry> entries
    ) {
        this(
            postPos, revision, workerUuid, workerState, status, lastFailure,
            targetCount, enabledCount, executableCount, workerStats,
            true, 0, 1, entries
        );
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, CataloguePayload payload) {
        BlockPos.PACKET_CODEC.encode(buf, payload.postPos);
        buf.writeVarInt(payload.revision);
        buf.writeBoolean(payload.workerUuid.isPresent());
        payload.workerUuid.ifPresent(uuid -> Uuids.PACKET_CODEC.encode(buf, uuid));
        buf.writeString(payload.workerState, 64);
        buf.writeString(payload.status, 256);
        buf.writeString(payload.lastFailure, 256);
        buf.writeVarInt(boundedSummary(payload.targetCount));
        buf.writeVarInt(boundedSummary(payload.enabledCount));
        buf.writeVarInt(boundedSummary(payload.executableCount));
        buf.writeBoolean(payload.workerStats.isPresent());
        payload.workerStats.ifPresent(stats -> stats.write(buf));
        buf.writeBoolean(payload.fullCatalogue);
        buf.writeVarInt(payload.chunkIndex);
        buf.writeVarInt(payload.chunkCount);
        int size = payload.entries.size();
        if (size > MAX_ENTRIES_PER_CHUNK) {
            throw new IllegalArgumentException("Merchant catalogue chunk is too large: " + size);
        }
        buf.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            payload.entries.get(index).write(buf);
        }
    }

    private static CataloguePayload read(RegistryByteBuf buf) {
        BlockPos pos = BlockPos.PACKET_CODEC.decode(buf);
        int revision = buf.readVarInt();
        Optional<UUID> worker = buf.readBoolean()
            ? Optional.of(Uuids.PACKET_CODEC.decode(buf))
            : Optional.empty();
        String state = buf.readString(64);
        String status = buf.readString(256);
        String failure = buf.readString(256);
        int targets = readBoundedCount(buf, MAX_SUMMARY_COUNT, "target");
        int enabled = readBoundedCount(buf, MAX_SUMMARY_COUNT, "enabled offer");
        int executable = readBoundedCount(buf, MAX_SUMMARY_COUNT, "executable offer");
        Optional<WorkerStats> workerStats = buf.readBoolean()
            ? Optional.of(WorkerStats.read(buf))
            : Optional.empty();
        boolean fullCatalogue = buf.readBoolean();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        if (fullCatalogue) {
            if (chunkCount < 1 || chunkCount > MAX_CHUNKS
                || chunkIndex < 0 || chunkIndex >= chunkCount) {
                throw new IllegalArgumentException(
                    "Invalid Merchant catalogue chunk " + chunkIndex + "/" + chunkCount
                );
            }
        } else if (chunkIndex != 0 || chunkCount != 0) {
            throw new IllegalArgumentException("Telemetry packet cannot declare catalogue chunks");
        }
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES_PER_CHUNK || (!fullCatalogue && size != 0)) {
            throw new IllegalArgumentException("Invalid Merchant catalogue size: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(Entry.read(buf));
        }
        return new CataloguePayload(
            pos,
            revision,
            worker,
            state,
            status,
            failure,
            targets,
            enabled,
            executable,
            workerStats,
            fullCatalogue,
            chunkIndex,
            chunkCount,
            List.copyOf(entries)
        );
    }

    private static int readBoundedCount(RegistryByteBuf buf, int maximum, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid Merchant " + label + " count: " + count);
        }
        return count;
    }

    private static int boundedSummary(int value) {
        return Math.min(MAX_SUMMARY_COUNT, Math.max(0, value));
    }

    public record WorkerStats(
        String name,
        float health,
        float maxHealth,
        int storedExperience,
        double distanceSquared,
        List<ItemStack> cargo,
        boolean cargoSummarized,
        int rewardSlotMask,
        Optional<UUID> targetUuid,
        int plannedExecutions,
        int completedExecutions,
        Optional<BlockPos> importChest,
        Optional<BlockPos> outputChest,
        String outputChestStatus
    ) {
        public WorkerStats {
            name = WorkerTelemetryBounds.clamp(name, 128);
            cargoSummarized = cargoSummarized || cargo.size() > MAX_CARGO_SLOTS;
            cargo = cargo.stream().limit(MAX_CARGO_SLOTS).map(ItemStack::copy).toList();
            outputChestStatus = WorkerTelemetryBounds.clamp(outputChestStatus, 64);
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(name, 128);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeVarInt(Math.max(0, storedExperience));
            buf.writeDouble(distanceSquared);
            int cargoSize = Math.min(cargo.size(), MAX_CARGO_SLOTS);
            buf.writeVarInt(cargoSize);
            for (int slot = 0; slot < cargoSize; slot++) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, cargo.get(slot));
            }
            buf.writeBoolean(cargoSummarized);
            buf.writeVarInt(rewardSlotMask & ((1 << MAX_CARGO_SLOTS) - 1));
            buf.writeBoolean(targetUuid.isPresent());
            targetUuid.ifPresent(uuid -> Uuids.PACKET_CODEC.encode(buf, uuid));
            buf.writeVarInt(Math.max(0, plannedExecutions));
            buf.writeVarInt(Math.max(0, completedExecutions));
            buf.writeBoolean(importChest.isPresent());
            importChest.ifPresent(pos -> BlockPos.PACKET_CODEC.encode(buf, pos));
            buf.writeBoolean(outputChest.isPresent());
            outputChest.ifPresent(pos -> BlockPos.PACKET_CODEC.encode(buf, pos));
            buf.writeString(outputChestStatus, 64);
        }

        private static WorkerStats read(RegistryByteBuf buf) {
            String name = buf.readString(128);
            float health = buf.readFloat();
            float maxHealth = buf.readFloat();
            int storedExperience = readBoundedCount(buf, Integer.MAX_VALUE, "stored experience");
            double distanceSquared = buf.readDouble();
            int cargoSize = readBoundedCount(buf, MAX_CARGO_SLOTS, "cargo");
            List<ItemStack> cargo = new ArrayList<>(cargoSize);
            for (int slot = 0; slot < cargoSize; slot++) {
                cargo.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
            }
            boolean cargoSummarized = buf.readBoolean();
            int rewardSlotMask = readBoundedCount(
                buf, (1 << MAX_CARGO_SLOTS) - 1, "reward slot mask"
            );
            Optional<UUID> target = buf.readBoolean()
                ? Optional.of(Uuids.PACKET_CODEC.decode(buf))
                : Optional.empty();
            int planned = readBoundedCount(buf, Integer.MAX_VALUE, "planned execution");
            int completed = readBoundedCount(buf, Integer.MAX_VALUE, "completed execution");
            Optional<BlockPos> importChest = buf.readBoolean()
                ? Optional.of(BlockPos.PACKET_CODEC.decode(buf))
                : Optional.empty();
            Optional<BlockPos> chest = buf.readBoolean()
                ? Optional.of(BlockPos.PACKET_CODEC.decode(buf))
                : Optional.empty();
            String chestStatus = buf.readString(64);
            return new WorkerStats(
                name,
                health,
                maxHealth,
                storedExperience,
                distanceSquared,
                List.copyOf(cargo),
                cargoSummarized,
                rewardSlotMask,
                target,
                planned,
                completed,
                importChest,
                chest,
                chestStatus
            );
        }
    }

    public record Entry(
        OfferSnapshot offer,
        boolean enabled,
        boolean coolingDown,
        int fundableExecutions,
        boolean selected,
        int storedFirstCount,
        int storedSecondCount,
        int effectiveFirstCount,
        int effectiveSecondCount
    ) {
        void write(RegistryByteBuf buf) {
            writeOffer(buf, offer);
            buf.writeBoolean(enabled);
            buf.writeBoolean(coolingDown);
            buf.writeVarInt(Math.max(0, fundableExecutions));
            buf.writeBoolean(selected);
            buf.writeVarInt(Math.max(0, storedFirstCount));
            buf.writeVarInt(Math.max(0, storedSecondCount));
            buf.writeVarInt(Math.max(1, effectiveFirstCount));
            buf.writeVarInt(Math.max(0, effectiveSecondCount));
        }

        private static Entry read(RegistryByteBuf buf) {
            OfferSnapshot offer = readOffer(buf);
            boolean enabled = buf.readBoolean();
            boolean coolingDown = buf.readBoolean();
            int fundable = buf.readVarInt();
            boolean selected = buf.readBoolean();
            int storedFirst = buf.readVarInt();
            int storedSecond = buf.readVarInt();
            int effectiveFirst = buf.readVarInt();
            int effectiveSecond = buf.readVarInt();
            return new Entry(
                offer,
                enabled,
                coolingDown,
                fundable,
                selected,
                storedFirst,
                storedSecond,
                effectiveFirst,
                effectiveSecond
            );
        }
    }

    static void writeOffer(RegistryByteBuf buf, OfferSnapshot offer) {
            Uuids.PACKET_CODEC.encode(buf, offer.targetUuid());
            buf.writeString(offer.targetName(), 1024);
            buf.writeString(offer.profession(), 128);
            buf.writeVarInt(offer.villagerLevel());
            buf.writeVarInt(offer.offerIndex());
            TradedItem.PACKET_CODEC.encode(buf, offer.firstInput());
            TradedItem.OPTIONAL_PACKET_CODEC.encode(buf, offer.secondInput());
            ItemStack.PACKET_CODEC.encode(buf, offer.output());
            buf.writeVarInt(offer.uses());
            buf.writeVarInt(offer.maxUses());
            buf.writeDouble(offer.distanceSquared());
            buf.writeBoolean(offer.wanderingTrader());
            buf.writeBoolean(offer.targetAvailable());
            buf.writeVarInt(Math.max(0, offer.despawnDelay() + 1));
            buf.writeString(offer.fingerprint(), 80);
    }

    static OfferSnapshot readOffer(RegistryByteBuf buf) {
            UUID targetUuid = Uuids.PACKET_CODEC.decode(buf);
            String targetName = buf.readString(1024);
            String profession = buf.readString(128);
            int level = buf.readVarInt();
            int offerIndex = buf.readVarInt();
            TradedItem first = TradedItem.PACKET_CODEC.decode(buf);
            Optional<TradedItem> second = TradedItem.OPTIONAL_PACKET_CODEC.decode(buf);
            ItemStack output = ItemStack.PACKET_CODEC.decode(buf);
            int uses = buf.readVarInt();
            int maxUses = buf.readVarInt();
            double distanceSquared = buf.readDouble();
            boolean wandering = buf.readBoolean();
            boolean targetAvailable = buf.readBoolean();
            int despawnDelay = buf.readVarInt() - 1;
            String fingerprint = buf.readString(80);
            return new OfferSnapshot(
                targetUuid, targetName, profession, level, offerIndex, first, second, output,
                uses, maxUses, distanceSquared, wandering, targetAvailable, despawnDelay,
                fingerprint
            );
    }
}
