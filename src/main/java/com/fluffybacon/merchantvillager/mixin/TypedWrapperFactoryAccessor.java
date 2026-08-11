package com.fluffybacon.merchantvillager.mixin;

import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Resolves the delegate selected for a concrete villager type without executing it. */
@Mixin(targets = "net.minecraft.village.TradeOffers$TypedWrapperFactory")
public interface TypedWrapperFactoryAccessor {
    @Accessor("typeToFactory")
    Map<RegistryKey<VillagerType>, TradeOffers.Factory> merchantVillager$getTypeToFactory();
}
