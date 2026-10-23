package com.aleruan.dealos.platform.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientCallTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final ResilientCall.Sleeper recordingSleeper = sleeps::add;
    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofMillis(150));

    @Test
    void retriesThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = ResilientCall.execute("TEST", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("timeout");
            }
            return "ok";
        }, policy, recordingSleeper);
        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(150)), sleeps); // capped by maxBackoff
    }

    @Test
    void givesUpAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RegistryUnavailableException.class, () -> ResilientCall.execute("TEST", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("down");
        }, policy, recordingSleeper));
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryNonRetryable() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(NonRetryableException.class, () -> ResilientCall.execute("TEST", () -> {
            calls.incrementAndGet();
            throw new NonRetryableException("bad input");
        }, policy, recordingSleeper));
        assertEquals(1, calls.get());
        assertEquals(0, sleeps.size());
    }
}
