package com.aleruan.dealos.platform.integration;

import java.time.Duration;

/** Exponential backoff policy for calls to external registries. */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(300), 2.0, Duration.ofSeconds(3));
    }

    /** Wait before retry number {@code retryIndex} (0 = the first retry). */
    public Duration backoff(int retryIndex) {
        double millis = initialBackoff.toMillis() * Math.pow(multiplier, retryIndex);
        return Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis()));
    }
}
