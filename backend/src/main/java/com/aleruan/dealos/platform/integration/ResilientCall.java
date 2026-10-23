package com.aleruan.dealos.platform.integration;

import java.time.Duration;
import java.util.concurrent.Callable;

/** Runs a registry call with retries and backoff. Framework-free; the sleeper is injectable for tests. */
public final class ResilientCall {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;

        Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());
    }

    private ResilientCall() {
    }

    public static <T> T execute(String sourceName, Callable<T> call, RetryPolicy policy, Sleeper sleeper) {
        Exception last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return call.call();
            } catch (NonRetryableException e) {
                throw e;
            } catch (Exception e) {
                last = e;
                if (attempt < policy.maxAttempts()) {
                    try {
                        sleeper.sleep(policy.backoff(attempt - 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new RegistryUnavailableException(sourceName + ": interrupted while retrying", interrupted);
                    }
                }
            }
        }
        throw new RegistryUnavailableException(
                sourceName + ": unavailable after " + policy.maxAttempts() + " attempts", last);
    }
}
