package com.aleruan.dealos.docimport.reader;

/** Reads a document into an {@link Extraction}. Implementations may be slow (seconds) and may fail. */
public interface DocumentReader {

    record Result(Extraction extraction, String model, Integer inputTokens, Integer outputTokens) {
    }

    /** Thrown with a message a person can act on (key invalid, document too long, service busy). */
    class ReadFailed extends RuntimeException {
        public ReadFailed(String message) {
            super(message);
        }

        public ReadFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }

    Result read(byte[] data, String contentType, String fileName);

    /** The model name shown in the UI. */
    String model();
}
