package com.inventra.api.security;

public final class RateLimitConstants {
    private RateLimitConstants() {}

    public static final int MAX_REQUESTS_PER_WINDOW = 20;
    public static final long WINDOW_SIZE_SECONDS = 60;
    public static final int MAX_TRACKED_IPS = 10_000;
}
