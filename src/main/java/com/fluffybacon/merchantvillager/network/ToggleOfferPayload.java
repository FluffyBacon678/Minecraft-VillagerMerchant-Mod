package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record ToggleOfferPayload(BlockPos postPos, String fingerprint, boolean enabled) implements CustomPayload {
    public static final Id<ToggleOfferPayload> ID = new Id<>(MerchantVillagerMod.id("toggle_offer"));
    public static final PacketCodec<RegistryByteBuf, ToggleOfferPayload> CODEC =
        PacketCodec.ofStatic(ToggleOfferPayload::write, ToggleOfferPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, ToggleOfferPayload payload) {
        BlockPos.PACKET_CODEC.encode(buf, payload.postPos);
        buf.writeString(payload.fingerprint, 80);
        buf.writeBoolean(payload.enabled);
    }

    private static ToggleOfferPayload read(RegistryByteBuf buf) {
        return new ToggleOfferPayload(
            BlockPos.PACKET_CODEC.decode(buf),
            buf.readString(80),
            buf.readBoolean()
        );
    }
}
