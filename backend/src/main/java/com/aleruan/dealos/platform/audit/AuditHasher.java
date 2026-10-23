package com.aleruan.dealos.platform.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** SHA-256 hash chain over audit records. Framework-free so it can be verified anywhere. */
public final class AuditHasher {

    /** prevHash of the very first record. */
    public static final String GENESIS = "0".repeat(64);

    private static final String SEPARATOR = "\u001F"; // ASCII unit separator, never typed by users

    private AuditHasher() {
    }

    public static String hash(String prevHash, long seq, Instant occurredAt, String actor,
                              String entityType, String entityId, String action, String payload) {
        String canonical = String.join(SEPARATOR,
                prevHash, Long.toString(seq), occurredAt.toString(),
                nz(actor), nz(entityType), nz(entityId), nz(action), nz(payload));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Verifies records ordered by seq, starting from seq 1. Reports the first broken link:
     * a gap in seq, a prevHash that does not point at the previous record, or a hash that
     * does not match the record content.
     */
    public static ChainVerification verify(List<AuditRecord> orderedRecords) {
        String expectedPrev = GENESIS;
        long expectedSeq = 1;
        for (AuditRecord record : orderedRecords) {
            if (record.seq() != expectedSeq
                    || !record.prevHash().equals(expectedPrev)
                    || !record.selfConsistent()) {
                return ChainVerification.broken(expectedSeq - 1, record.seq());
            }
            expectedPrev = record.hash();
            expectedSeq++;
        }
        return ChainVerification.ok(orderedRecords.size());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
