package com.fluffybacon.merchantvillager.clienttest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Fabric's client GameTest API hardcodes world connection to one logical
 * minute (1,200 ticks). Software-rendered CI can exhaust that logical budget
 * while the server's asynchronous chunk workers are still preparing spawn.
 * Keep the production mod untouched and widen only the test harness guard.
 */
@Mixin(
    targets = "net.fabricmc.fabric.impl.client.gametest.util.ClientGameTestImpl",
    remap = false
)
abstract class ClientGameTestWorldLoadTimeoutMixin {
    private static final int CI_WORLD_LOAD_TIMEOUT_TICKS = 20 * 60 * 3;

    @ModifyConstant(
        method = "waitForWorldLoad",
        constant = @Constant(intValue = 20 * 60),
        require = 1,
        remap = false
    )
    private static int merchantVillager$extendWorldLoadTimeout(int original) {
        return Math.max(original, CI_WORLD_LOAD_TIMEOUT_TICKS);
    }
}
