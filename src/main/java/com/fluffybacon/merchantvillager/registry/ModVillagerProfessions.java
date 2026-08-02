package com.fluffybacon.merchantvillager.registry;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.google.common.collect.ImmutableSet;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.village.VillagerProfession;

public final class ModVillagerProfessions {
    public static final RegistryKey<VillagerProfession> MERCHANT_KEY =
        RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, MerchantVillagerMod.id("merchant"));

    public static final VillagerProfession MERCHANT = Registry.register(
        Registries.VILLAGER_PROFESSION,
        MERCHANT_KEY,
        new VillagerProfession(
            Text.translatable("entity.merchant_villager.villager.merchant"),
            entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
            entry -> entry.matchesKey(ModPointOfInterests.MERCHANT_POST_KEY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.ENTITY_VILLAGER_WORK_LIBRARIAN
        )
    );

    public static void initialize() {
    }

    private ModVillagerProfessions() {
    }
}
