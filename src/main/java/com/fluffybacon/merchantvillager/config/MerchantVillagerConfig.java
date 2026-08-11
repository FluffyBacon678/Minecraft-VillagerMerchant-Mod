package com.fluffybacon.merchantvillager.config;

public final class MerchantVillagerConfig {
    public static final int NORMAL_RADIUS = 55;
    public static final int EXTENDED_RADIUS = 66;
    public static final int CATALOGUE_SCAN_INTERVAL = 40;
    public static final int PATH_RETRY_INTERVAL = 20;
    public static final int MAX_PATH_RETRIES = 4;
    public static final double PATH_MIN_PROGRESS = 0.5;
    public static final int PATH_STALL_OBSERVATIONS = 2;
    public static final double DIRECT_APPROACH_DISTANCE_SQUARED = 12.0 * 12.0;
    public static final double STALLED_DIRECT_APPROACH_DISTANCE_SQUARED = 32.0 * 32.0;
    public static final int MEET_HALFWAY_OBSERVATION_TICKS = 20;
    public static final int MEET_HALFWAY_FAILURE_TICKS = 40;
    public static final double MEET_HALFWAY_MIN_PROGRESS = 0.75;
    public static final int CARGO_SIZE = 9;
    public static final int MAX_EXECUTIONS_PER_TICK = 2;
    public static final int RESERVATION_TIMEOUT = 20 * 60;
    public static final int UNREACHABLE_COOLDOWN = 20 * 30;
    public static final int OUTPUT_CHEST_RETRY_INTERVAL = 40;
    public static final int TARGET_BUSY_TIMEOUT = 20 * 15;
    public static final int MIN_TRADE_INTERACTION_TICKS = 20 * 3;
    public static final int MAX_TRADE_INTERACTION_TICKS = 20 * 10;
    public static final boolean NEW_OFFERS_ENABLED = false;
    public static final double INTERACTION_DISTANCE_SQUARED = 16.0;

    private MerchantVillagerConfig() {
    }
}
