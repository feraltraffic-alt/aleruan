package com.aleruan.dealos.platform.web;

/** Maps to HTTP 409 (duplicates, invalid state transitions). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
