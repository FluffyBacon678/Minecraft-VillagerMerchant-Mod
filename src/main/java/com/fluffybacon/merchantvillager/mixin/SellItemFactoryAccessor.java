package com.fluffybacon.merchantvillager.mixin;

import java.util.Optional;
import net.minecraft.enchantment.provider.EnchantmentProvider;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only metadata used to decide whether vanilla trade sampling needs multiple seeds. */
@Mixin(targets = "net.minecraft.village.TradeOffers$SellItemFactory")
public interface SellItemFactoryAccessor {
    @Accessor("enchantmentProviderKey")
    Optional<RegistryKey<EnchantmentProvider>> merchantVillager$getEnchantmentProviderKey();
}
