package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.DyeColor;
import org.jspecify.annotations.Nullable;

/** Persistent and client-synchronized clothing color for Merchant villagers. */
public final class MerchantClothing {
    private static final int UNDYED = -1;

    public static final AttachmentType<Integer> COLOR = AttachmentRegistry.create(
        MerchantVillagerMod.id("clothing_color"),
        builder -> builder
            .persistent(Codec.intRange(UNDYED, DyeColor.values().length - 1))
            .copyOnDeath()
            .syncWith(PacketCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    public static void initialize() {
        // Loading this class registers the attachment before any worlds load.
    }

    @Nullable
    public static DyeColor get(VillagerEntity villager) {
        Integer stored = ((AttachmentTarget)villager).getAttached(COLOR);
        return stored == null || stored == UNDYED ? null : DyeColor.byIndex(stored);
    }

    public static boolean set(VillagerEntity villager, DyeColor color) {
        DyeColor current = get(villager);
        if (current == color) {
            return false;
        }
        ((AttachmentTarget)villager).setAttached(COLOR, color.getIndex());
        return true;
    }

    private MerchantClothing() {
    }
}
