package com.fluffybacon.merchantvillager.clienttest.mixin;

import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps the dedicated client GameTest on portable Java NIO in headless CI. */
@Mixin(GameOptions.class)
public interface GameOptionsAccessor {
    @Accessor("useNativeTransport")
    void merchantVillager$setUseNativeTransport(boolean enabled);
}
