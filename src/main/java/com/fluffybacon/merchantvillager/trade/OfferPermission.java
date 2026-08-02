package com.fluffybacon.merchantvillager.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.util.Uuids;

public record OfferPermission(String fingerprint, UUID targetUuid, boolean enabled, long lastSeen) {
    public static final Codec<OfferPermission> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("fingerprint").forGetter(OfferPermission::fingerprint),
        Uuids.CODEC.fieldOf("target").forGetter(OfferPermission::targetUuid),
        Codec.BOOL.optionalFieldOf("enabled", false).forGetter(OfferPermission::enabled),
        Codec.LONG.optionalFieldOf("last_seen", 0L).forGetter(OfferPermission::lastSeen)
    ).apply(instance, OfferPermission::new));
}
