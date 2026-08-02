package com.fluffybacon.merchantvillager.network;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record DisableAllOffersPayload(BlockPos postPos) implements CustomPayload {
    public static final Id<DisableAllOffersPayload> ID =
        new Id<>(MerchantVillagerMod.id("disable_all_offers"));
    public static final PacketCodec<RegistryByteBuf, DisableAllOffersPayload> CODEC =
        PacketCodec.tuple(BlockPos.PACKET_CODEC, DisableAllOffersPayload::postPos, DisableAllOffersPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
