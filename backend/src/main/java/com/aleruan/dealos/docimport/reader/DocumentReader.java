package com.aleruan.dealos.docimport.reader;

/** Reads a document into an {@link Extraction}. Implementations may be slow (seconds) and may fail. */
public interface DocumentReader {

    record Result(Extraction extraction, String model, Integer inputTokens, Integer outputTokens) {
    }

    /**
     * Thrown with a message a person can act on. {@code retryable} when trying again later may work
     * (service busy, network); not for a refusal, a document too long or a bad key, so those are not paid for twice.
     */
    class ReadFailed extends RuntimeException {
        private final boolean retryable;

        public ReadFailed(String message) {
            this(message, false, null);
        }

        public ReadFailed(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    Result read(byte[] data, String contentType, String fileName);

    /** The model name shown in the UI. */
    String model();
}
