package com.fluffybacon.merchantvillager.clienttest;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hands one in-process server address from the client GameTest thread to
 * Minecraft's connector thread. The address is consumed exactly once.
 */
public final class ClientTestLocalTransport {
    private static final AtomicReference<SocketAddress> NEXT_ADDRESS = new AtomicReference<>();

    private ClientTestLocalTransport() {
    }

    public static void arm(SocketAddress address) {
        if (!NEXT_ADDRESS.compareAndSet(null, address)) {
            throw new AssertionError("A client GameTest local connection is already armed");
        }
    }

    public static SocketAddress take() {
        return NEXT_ADDRESS.getAndSet(null);
    }

    public static void disarm(SocketAddress address) {
        NEXT_ADDRESS.compareAndSet(address, null);
    }
}
