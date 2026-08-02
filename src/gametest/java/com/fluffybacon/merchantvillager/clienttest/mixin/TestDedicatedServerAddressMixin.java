package com.fluffybacon.merchantvillager.clienttest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric's dedicated client GameTest context advertises its local server as
 * {@code localhost}. Some Linux CI runners can stall while Minecraft resolves
 * that hostname, before a TCP connection ever reaches the already-listening
 * server. Preserve Fabric's selected port while using the unambiguous IPv4
 * loopback address. This mixin is packaged only in the isolated test mod.
 */
@Mixin(
    targets = "net.fabricmc.fabric.impl.client.gametest.context.TestDedicatedServerContextImpl",
    remap = false
)
abstract class TestDedicatedServerAddressMixin {
    private static final String LOCALHOST_PREFIX = "localhost:";

    @Inject(
        method = "getConnectionAddress()Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1,
        remap = false
    )
    private void merchantVillager$useIpv4Loopback(CallbackInfoReturnable<String> callback) {
        String original = callback.getReturnValue();
        if (!original.startsWith(LOCALHOST_PREFIX)) {
            throw new AssertionError("Unexpected Fabric client GameTest address: " + original);
        }

        callback.setReturnValue("127.0.0.1:" + original.substring(LOCALHOST_PREFIX.length()));
    }
}
