package com.fluffybacon.merchantvillager.network;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Independent per-player request budgets. Called only on the server thread. */
final class MerchantPostRequestLimiter<K> {
    enum Action {
        TOGGLE(4), REFRESH(20), DISABLE_ALL(20);

        private final long minimumTicks;

        Action(long minimumTicks) {
            this.minimumTicks = minimumTicks;
        }
    }

    private final Map<K, EnumMap<Action, Long>> lastTicks = new WeakHashMap<>();

    boolean allow(K player, Action action, long now, boolean authorized) {
        // Invalid screens, positions and distances must not consume a valid
        // request's budget when the player subsequently opens the right post.
        if (!authorized) {
            return false;
        }
        EnumMap<Action, Long> playerTicks = lastTicks.computeIfAbsent(
            player, ignored -> new EnumMap<>(Action.class)
        );
        Long previous = playerTicks.get(action);
        if (previous != null && now >= previous && now - previous < action.minimumTicks) {
            return false;
        }
        playerTicks.put(action, now);
        return true;
    }
}
