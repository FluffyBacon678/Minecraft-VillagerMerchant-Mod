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
    List<Entry> entries
) implements CustomPayload {
    public static final Id<CataloguePayload> ID = new Id<>(MerchantVillagerMod.id("catalogue"));
    public static final PacketCodec<RegistryByteBuf, CataloguePayload> CODEC =
        PacketCodec.ofStatic(CataloguePayload::write, CataloguePayload::read);
    private static final int MAX_OFFERS = 512;
    private static final int MAX_CARGO_SLOTS = 9;

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
        buf.writeVarInt(Math.max(0, payload.targetCount));
        buf.writeVarInt(Math.max(0, payload.enabledCount));
        buf.writeVarInt(Math.max(0, payload.executableCount));
        buf.writeBoolean(payload.workerStats.isPresent());
        payload.workerStats.ifPresent(stats -> stats.write(buf));
        int size = Math.min(payload.entries.size(), MAX_OFFERS);
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
        int targets = readBoundedCount(buf, MAX_OFFERS, "target");
        int enabled = readBoundedCount(buf, MAX_OFFERS, "enabled offer");
        int executable = readBoundedCount(buf, MAX_OFFERS, "executable offer");
        Optional<WorkerStats> workerStats = buf.readBoolean()
            ? Optional.of(WorkerStats.read(buf))
            : Optional.empty();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_OFFERS) {
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

    public record WorkerStats(
        String name,
        float health,
        float maxHealth,
        double distanceSquared,
        List<ItemStack> cargo,
        Optional<UUID> targetUuid,
        int plannedExecutions,
        int completedExecutions,
        Optional<BlockPos> outputChest,
        String outputChestStatus
    ) {
        public WorkerStats {
            cargo = cargo.stream().map(ItemStack::copy).toList();
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(name, 128);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeDouble(distanceSquared);
            int cargoSize = Math.min(cargo.size(), MAX_CARGO_SLOTS);
            buf.writeVarInt(cargoSize);
            for (int slot = 0; slot < cargoSize; slot++) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, cargo.get(slot));
            }
            buf.writeBoolean(targetUuid.isPresent());
            targetUuid.ifPresent(uuid -> Uuids.PACKET_CODEC.encode(buf, uuid));
            buf.writeVarInt(Math.max(0, plannedExecutions));
            buf.writeVarInt(Math.max(0, completedExecutions));
            buf.writeBoolean(outputChest.isPresent());
            outputChest.ifPresent(pos -> BlockPos.PACKET_CODEC.encode(buf, pos));
            buf.writeString(outputChestStatus, 64);
        }

        private static WorkerStats read(RegistryByteBuf buf) {
            String name = buf.readString(128);
            float health = buf.readFloat();
            float maxHealth = buf.readFloat();
            double distanceSquared = buf.readDouble();
            int cargoSize = readBoundedCount(buf, MAX_CARGO_SLOTS, "cargo");
            List<ItemStack> cargo = new ArrayList<>(cargoSize);
            for (int slot = 0; slot < cargoSize; slot++) {
                cargo.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
            }
            Optional<UUID> target = buf.readBoolean()
                ? Optional.of(Uuids.PACKET_CODEC.decode(buf))
                : Optional.empty();
            int planned = readBoundedCount(buf, Integer.MAX_VALUE, "planned execution");
            int completed = readBoundedCount(buf, Integer.MAX_VALUE, "completed execution");
            Optional<BlockPos> chest = buf.readBoolean()
                ? Optional.of(BlockPos.PACKET_CODEC.decode(buf))
                : Optional.empty();
            String chestStatus = buf.readString(64);
            return new WorkerStats(
                name,
                health,
                maxHealth,
                distanceSquared,
                List.copyOf(cargo),
                target,
                planned,
                completed,
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
        private void write(RegistryByteBuf buf) {
            Uuids.PACKET_CODEC.encode(buf, offer.targetUuid());
            buf.writeString(offer.targetName(), 128);
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
            buf.writeString(offer.fingerprint(), 64);
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
            UUID targetUuid = Uuids.PACKET_CODEC.decode(buf);
            String targetName = buf.readString(128);
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
            String fingerprint = buf.readString(64);
            boolean enabled = buf.readBoolean();
            boolean coolingDown = buf.readBoolean();
            int fundable = buf.readVarInt();
            boolean selected = buf.readBoolean();
            int storedFirst = buf.readVarInt();
            int storedSecond = buf.readVarInt();
            int effectiveFirst = buf.readVarInt();
            int effectiveSecond = buf.readVarInt();
            return new Entry(
                new OfferSnapshot(
                    targetUuid, targetName, profession, level, offerIndex, first, second, output,
                    uses, maxUses, distanceSquared, wandering, targetAvailable, despawnDelay,
                    fingerprint
                ),
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
}
