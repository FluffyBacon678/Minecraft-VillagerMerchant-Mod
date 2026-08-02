package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record RefreshCataloguePayload(BlockPos postPos) implements CustomPayload {
    public static final Id<RefreshCataloguePayload> ID = new Id<>(MerchantVillagerMod.id("refresh_catalogue"));
    public static final PacketCodec<RegistryByteBuf, RefreshCataloguePayload> CODEC =
        PacketCodec.ofStatic(RefreshCataloguePayload::write, RefreshCataloguePayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, RefreshCataloguePayload payload) {
        BlockPos.PACKET_CODEC.encode(buf, payload.postPos);
    }

    private static RefreshCataloguePayload read(RegistryByteBuf buf) {
        return new RefreshCataloguePayload(BlockPos.PACKET_CODEC.decode(buf));
    }
}
