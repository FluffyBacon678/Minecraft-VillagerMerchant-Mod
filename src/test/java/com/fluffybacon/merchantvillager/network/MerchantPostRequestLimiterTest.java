package com.fluffybacon.merchantvillager.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MerchantPostRequestLimiterTest {
    private final MerchantPostRequestLimiter<Object> limiter = new MerchantPostRequestLimiter<>();
    private final Object player = new Object();

    @Test void refreshAndDisableAllHaveIndependentBudgets() {
        assertTrue(allow(MerchantPostRequestLimiter.Action.REFRESH, 100));
        assertTrue(allow(MerchantPostRequestLimiter.Action.DISABLE_ALL, 100));
        assertTrue(allow(MerchantPostRequestLimiter.Action.TOGGLE, 100));
        assertFalse(allow(MerchantPostRequestLimiter.Action.REFRESH, 101));
        assertFalse(allow(MerchantPostRequestLimiter.Action.DISABLE_ALL, 101));
        assertFalse(allow(MerchantPostRequestLimiter.Action.TOGGLE, 101));
    }

    @Test void rejectedRequestsDoNotConsumeTheAuthorizedRequestBudget() {
        for (var action : MerchantPostRequestLimiter.Action.values()) {
            assertFalse(limiter.allow(player, action, 100, false));
            assertTrue(allow(action, 100));
            assertFalse(limiter.allow(player, action, 102, false));
        }
        assertTrue(allow(MerchantPostRequestLimiter.Action.TOGGLE, 104));
        assertTrue(allow(MerchantPostRequestLimiter.Action.REFRESH, 120));
        assertTrue(allow(MerchantPostRequestLimiter.Action.DISABLE_ALL, 120));
    }

    @Test void eachActionAllowsExactlyAtItsIntervalBoundary() {
        assertTrue(allow(MerchantPostRequestLimiter.Action.TOGGLE, 100));
        assertFalse(allow(MerchantPostRequestLimiter.Action.TOGGLE, 103));
        assertTrue(allow(MerchantPostRequestLimiter.Action.TOGGLE, 104));
        for (var action : new MerchantPostRequestLimiter.Action[] {
            MerchantPostRequestLimiter.Action.REFRESH, MerchantPostRequestLimiter.Action.DISABLE_ALL
        }) {
            assertTrue(allow(action, 100));
            assertFalse(allow(action, 119));
            assertTrue(allow(action, 120));
        }
    }

    @Test void playersDoNotShareBudgets() {
        assertTrue(allow(MerchantPostRequestLimiter.Action.DISABLE_ALL, 100));
        assertTrue(limiter.allow(new Object(), MerchantPostRequestLimiter.Action.DISABLE_ALL, 100, true));
        assertFalse(allow(MerchantPostRequestLimiter.Action.DISABLE_ALL, 100));
    }

    @Test void rewoundWorldClockDoesNotLockOutRequests() {
        assertTrue(allow(MerchantPostRequestLimiter.Action.REFRESH, 100));
        assertTrue(allow(MerchantPostRequestLimiter.Action.REFRESH, 10));
        assertFalse(allow(MerchantPostRequestLimiter.Action.REFRESH, 29));
        assertTrue(allow(MerchantPostRequestLimiter.Action.REFRESH, 30));
    }

    private boolean allow(MerchantPostRequestLimiter.Action action, long tick) {
        return limiter.allow(player, action, tick, true);
    }
}
