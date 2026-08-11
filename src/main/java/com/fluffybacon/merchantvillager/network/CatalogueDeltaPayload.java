package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * A revision-checked patch for the mutable state of catalogue rows. Full
 * component-heavy rows are included only when their displayed live offer has
 * actually changed; ordinary approvals and inventory updates carry just the
 * trade key and small numeric state.
 */
public record CatalogueDeltaPayload(
    BlockPos postPos,
    int baseRevision,
    int revision,
    int chunkIndex,
    int chunkCount,
    List<RowDelta> deltas
) implements CustomPayload {
    public static final Id<CatalogueDeltaPayload> ID =
        new Id<>(MerchantVillagerMod.id("catalogue_delta"));
    public static final PacketCodec<RegistryByteBuf, CatalogueDeltaPayload> CODEC =
        PacketCodec.ofStatic(CatalogueDeltaPayload::write, CatalogueDeltaPayload::read);
    public static final int MAX_DELTAS_PER_CHUNK = 64;
    public static final int MAX_CHUNKS = 256;
    private static final int MAX_ROW_COUNT = 1_000_000;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, CatalogueDeltaPayload payload) {
        BlockPos.PACKET_CODEC.encode(buf, payload.postPos);
        buf.writeVarInt(payload.baseRevision);
        buf.writeVarInt(payload.revision);
        buf.writeVarInt(payload.chunkIndex);
        buf.writeVarInt(payload.chunkCount);
        if (payload.deltas.size() > MAX_DELTAS_PER_CHUNK) {
            throw new IllegalArgumentException(
                "Merchant catalogue delta chunk is too large: " + payload.deltas.size()
            );
        }
        buf.writeVarInt(payload.deltas.size());
        for (RowDelta delta : payload.deltas) {
            delta.write(buf);
        }
    }

    private static CatalogueDeltaPayload read(RegistryByteBuf buf) {
        BlockPos postPos = BlockPos.PACKET_CODEC.decode(buf);
        int baseRevision = buf.readVarInt();
        int revision = buf.readVarInt();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        if (baseRevision < 0 || revision < baseRevision
            || chunkCount < 1 || chunkCount > MAX_CHUNKS
            || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                "Invalid Merchant catalogue delta revision/chunk sequence"
            );
        }
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_DELTAS_PER_CHUNK) {
            throw new IllegalArgumentException("Invalid Merchant catalogue delta size: " + size);
        }
        List<RowDelta> deltas = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            deltas.add(RowDelta.read(buf));
        }
        return new CatalogueDeltaPayload(
            postPos, baseRevision, revision, chunkIndex, chunkCount, List.copyOf(deltas)
        );
    }

    public record RowDelta(
        String tradeKey,
        Optional<OfferSnapshot> replacementOffer,
        boolean enabled,
        boolean coolingDown,
        int fundableExecutions,
        boolean selected,
        int storedFirstCount,
        int storedSecondCount,
        int effectiveFirstCount,
        int effectiveSecondCount
    ) {
        public static RowDelta between(
            String tradeKey,
            CataloguePayload.Entry current,
            boolean replaceOffer
        ) {
            return new RowDelta(
                tradeKey,
                replaceOffer ? Optional.of(current.offer()) : Optional.empty(),
                current.enabled(),
                current.coolingDown(),
                current.fundableExecutions(),
                current.selected(),
                current.storedFirstCount(),
                current.storedSecondCount(),
                current.effectiveFirstCount(),
                current.effectiveSecondCount()
            );
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(tradeKey, 80);
            buf.writeBoolean(replacementOffer.isPresent());
            replacementOffer.ifPresent(offer -> CataloguePayload.writeOffer(buf, offer));
            buf.writeBoolean(enabled);
            buf.writeBoolean(coolingDown);
            buf.writeVarInt(Math.max(0, fundableExecutions));
            buf.writeBoolean(selected);
            buf.writeVarInt(Math.max(0, storedFirstCount));
            buf.writeVarInt(Math.max(0, storedSecondCount));
            buf.writeVarInt(Math.max(1, effectiveFirstCount));
            buf.writeVarInt(Math.max(0, effectiveSecondCount));
        }

        private static RowDelta read(RegistryByteBuf buf) {
            String tradeKey = buf.readString(80);
            if (tradeKey.length() < 64 || tradeKey.length() > 80) {
                throw new IllegalArgumentException("Invalid Merchant catalogue delta trade key");
            }
            Optional<OfferSnapshot> replacement = buf.readBoolean()
                ? Optional.of(CataloguePayload.readOffer(buf))
                : Optional.empty();
            if (replacement.isPresent()
                && !replacement.get().fingerprint().equals(tradeKey)) {
                throw new IllegalArgumentException(
                    "Merchant catalogue replacement does not match its trade key"
                );
            }
            boolean enabled = buf.readBoolean();
            boolean coolingDown = buf.readBoolean();
            int fundable = readCount(buf, "fundable execution", 0);
            boolean selected = buf.readBoolean();
            int storedFirst = readCount(buf, "stored first input", 0);
            int storedSecond = readCount(buf, "stored second input", 0);
            int effectiveFirst = readCount(buf, "effective first price", 1);
            int effectiveSecond = readCount(buf, "effective second price", 0);
            return new RowDelta(
                tradeKey,
                replacement,
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

        private static int readCount(RegistryByteBuf buf, String label, int minimum) {
            int value = buf.readVarInt();
            if (value < minimum || value > MAX_ROW_COUNT) {
                throw new IllegalArgumentException(
                    "Invalid Merchant " + label + " count: " + value
                );
            }
            return value;
        }

        public CataloguePayload.Entry apply(CataloguePayload.Entry previous) {
            OfferSnapshot offer = replacementOffer.orElse(previous.offer());
            return new CataloguePayload.Entry(
                offer,
                enabled,
                coolingDown,
                fundableExecutions,
                selected,
                storedFirstCount,
                storedSecondCount,
                effectiveFirstCount,
                effectiveSecondCount
            );
        }
    }
}
