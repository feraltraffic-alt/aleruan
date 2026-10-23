package com.aleruan.dealos.docimport.domain;

/** Uploaded → being read → read (or failed) → confirmed by a person, or rejected. */
public enum ImportStatus {
    QUEUED, PROCESSING, EXTRACTED, FAILED, CONFIRMED, REJECTED;

    public boolean isOpen() {
        return this != CONFIRMED && this != REJECTED;
    }
}
