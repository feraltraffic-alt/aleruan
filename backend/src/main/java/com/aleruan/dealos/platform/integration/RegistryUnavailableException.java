package com.aleruan.dealos.platform.integration;

/** The external registry could not be reached or answered with an error after all retries. */
public class RegistryUnavailableException extends RuntimeException {

    public RegistryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
