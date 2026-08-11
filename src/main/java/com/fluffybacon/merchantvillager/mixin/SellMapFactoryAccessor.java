package com.fluffybacon.merchantvillager.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access used to preview explorer-map trades without executing them. */
@Mixin(targets = "net.minecraft.village.TradeOffers$SellMapFactory")
public interface SellMapFactoryAccessor {
    @Accessor("price")
    int merchantVillager$getPrice();

    @Accessor("nameKey")
    String merchantVillager$getNameKey();

    @Accessor("maxUses")
    int merchantVillager$getMaxUses();

    @Accessor("experience")
    int merchantVillager$getExperience();
}
