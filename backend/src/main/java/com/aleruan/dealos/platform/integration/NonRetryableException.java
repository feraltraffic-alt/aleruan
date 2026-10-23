package com.aleruan.dealos.platform.integration;

/** Thrown by a registry call when repeating it cannot help (bad input, not found, etc.). */
public class NonRetryableException extends RuntimeException {

    public NonRetryableException(String message) {
        super(message);
    }

    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
