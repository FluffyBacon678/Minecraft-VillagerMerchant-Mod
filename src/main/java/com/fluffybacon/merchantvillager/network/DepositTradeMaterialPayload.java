package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record DepositTradeMaterialPayload(
    BlockPos postPos,
    String fingerprint,
    int inputIndex,
    int mode
) implements CustomPayload {
    public static final int CURSOR_ALL = 0;
    public static final int CURSOR_ONE = 1;
    public static final int PLAYER_ALL = 2;
    public static final Id<DepositTradeMaterialPayload> ID =
        new Id<>(MerchantVillagerMod.id("deposit_trade_material"));
    public static final PacketCodec<RegistryByteBuf, DepositTradeMaterialPayload> CODEC =
        PacketCodec.ofStatic(DepositTradeMaterialPayload::write, DepositTradeMaterialPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, DepositTradeMaterialPayload payload) {
        BlockPos.PACKET_CODEC.encode(buf, payload.postPos);
        buf.writeString(payload.fingerprint, 80);
        buf.writeByte(payload.inputIndex);
        buf.writeByte(payload.mode);
    }

    private static DepositTradeMaterialPayload read(RegistryByteBuf buf) {
        return new DepositTradeMaterialPayload(
            BlockPos.PACKET_CODEC.decode(buf),
            buf.readString(80),
            buf.readUnsignedByte(),
            buf.readUnsignedByte()
        );
    }
}
