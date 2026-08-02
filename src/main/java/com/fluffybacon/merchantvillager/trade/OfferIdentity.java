package com.fluffybacon.merchantvillager.trade;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.village.TradedItem;

public final class OfferIdentity {
    public static String create(
        RegistryWrapper.WrapperLookup registries,
        UUID targetUuid,
        int offerIndex,
        TradedItem first,
        Optional<TradedItem> second,
        ItemStack output,
        int maxUses,
        int merchantExperience,
        float priceMultiplier
    ) {
        var ops = registries.getOps(NbtOps.INSTANCE);
        String canonical = targetUuid + "|" + offerIndex + "|"
            + TradedItem.CODEC.encodeStart(ops, first).getOrThrow() + "|"
            + second.map(value -> TradedItem.CODEC.encodeStart(ops, value).getOrThrow().toString()).orElse("-") + "|"
            + ItemStack.CODEC.encodeStart(ops, output).getOrThrow() + "|"
            + maxUses + "|" + merchantExperience + "|" + Float.toHexString(priceMultiplier);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private OfferIdentity() {
    }
}
