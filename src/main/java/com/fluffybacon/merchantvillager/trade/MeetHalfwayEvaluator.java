package com.fluffybacon.merchantvillager.trade;

import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;

public final class MeetHalfwayEvaluator {
    public enum Range {
        NORMAL,
        OBSERVE,
        REJECT
    }

    public static Range classify(double distance) {
        if (distance <= MerchantVillagerConfig.NORMAL_RADIUS) {
            return Range.NORMAL;
        }
        if (distance <= MerchantVillagerConfig.EXTENDED_RADIUS) {
            return Range.OBSERVE;
        }
        return Range.REJECT;
    }

    /**
     * Extended targets must provide two independent signals over the
     * observation window: at least 0.75 blocks of measured progress toward the
     * post, and a positive instantaneous velocity projection in that same
     * direction. Requiring both rejects sideways wandering, movement away, and
     * floating-point position noise.
     */
    public static boolean converging(double initialDistance, double currentDistance, double velocityProjection) {
        return initialDistance - currentDistance >= MerchantVillagerConfig.MEET_HALFWAY_MIN_PROGRESS
            && velocityProjection > 0.005;
    }

    private MeetHalfwayEvaluator() {
    }
}
