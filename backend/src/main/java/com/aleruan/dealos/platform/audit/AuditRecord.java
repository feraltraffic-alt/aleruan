package com.aleruan.dealos.platform.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * One link of the immutable audit chain. Framework-free: the hash covers every field
 * plus the hash of the previous link, so any later change to any record breaks the chain.
 */
public record AuditRecord(
        long seq,
        Instant occurredAt,
        String actor,
        String entityType,
        String entityId,
        String action,
        String payload,
        String prevHash,
        String hash) {

    public AuditRecord {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(prevHash, "prevHash");
        Objects.requireNonNull(hash, "hash");
    }

    /** Builds the record that follows {@code previous} (or the first record when it is null). */
    public static AuditRecord next(AuditRecord previous, Instant occurredAt, String actor,
                                   String entityType, String entityId, String action, String payload) {
        long seq = previous == null ? 1L : previous.seq() + 1;
        String prevHash = previous == null ? AuditHasher.GENESIS : previous.hash();
        String hash = AuditHasher.hash(prevHash, seq, occurredAt, actor, entityType, entityId, action, payload);
        return new AuditRecord(seq, occurredAt, actor, entityType, entityId, action, payload, prevHash, hash);
    }

    /** True when the stored hash matches the record's own content. */
    public boolean selfConsistent() {
        return hash.equals(AuditHasher.hash(prevHash, seq, occurredAt, actor, entityType, entityId, action, payload));
    }
}
