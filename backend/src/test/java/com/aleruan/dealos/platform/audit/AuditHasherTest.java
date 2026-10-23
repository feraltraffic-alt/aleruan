package com.aleruan.dealos.platform.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditHasherTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00.123456Z");

    private static List<AuditRecord> chainOf(int n) {
        List<AuditRecord> chain = new ArrayList<>();
        AuditRecord prev = null;
        for (int i = 0; i < n; i++) {
            prev = AuditRecord.next(prev, T0.plusSeconds(i), "lena", "Counterparty", "id-" + i, "CREATED",
                    "{\"n\":" + i + "}");
            chain.add(prev);
        }
        return chain;
    }

    @Test
    void firstRecordStartsFromGenesis() {
        AuditRecord first = chainOf(1).get(0);
        assertEquals(1, first.seq());
        assertEquals(AuditHasher.GENESIS, first.prevHash());
        assertEquals(64, first.hash().length());
    }

    @Test
    void hashIsDeterministic() {
        String a = AuditHasher.hash(AuditHasher.GENESIS, 1, T0, "lena", "X", "1", "CREATED", "{}");
        String b = AuditHasher.hash(AuditHasher.GENESIS, 1, T0, "lena", "X", "1", "CREATED", "{}");
        assertEquals(a, b);
    }

    @Test
    void intactChainVerifies() {
        ChainVerification v = AuditHasher.verify(chainOf(5));
        assertTrue(v.valid());
        assertEquals(5, v.verifiedRecords());
        assertNull(v.brokenAtSeq());
    }

    @Test
    void emptyChainIsValid() {
        assertTrue(AuditHasher.verify(List.of()).valid());
    }

    @Test
    void editedPayloadIsDetected() {
        List<AuditRecord> chain = chainOf(5);
        AuditRecord r = chain.get(2);
        chain.set(2, new AuditRecord(r.seq(), r.occurredAt(), r.actor(), r.entityType(), r.entityId(), r.action(),
                "{\"n\":999}", r.prevHash(), r.hash()));
        ChainVerification v = AuditHasher.verify(chain);
        assertFalse(v.valid());
        assertEquals(Long.valueOf(3), v.brokenAtSeq());
        assertEquals(2, v.verifiedRecords());
    }

    @Test
    void recomputedHashStillBreaksTheNextLink() {
        List<AuditRecord> chain = chainOf(5);
        AuditRecord r = chain.get(1);
        String forged = AuditHasher.hash(r.prevHash(), r.seq(), r.occurredAt(), "mallory", r.entityType(),
                r.entityId(), r.action(), r.payload());
        chain.set(1, new AuditRecord(r.seq(), r.occurredAt(), "mallory", r.entityType(), r.entityId(), r.action(),
                r.payload(), r.prevHash(), forged));
        ChainVerification v = AuditHasher.verify(chain);
        assertFalse(v.valid());
        assertEquals(Long.valueOf(3), v.brokenAtSeq());
    }

    @Test
    void deletedRecordIsDetected() {
        List<AuditRecord> chain = chainOf(5);
        chain.remove(3);
        ChainVerification v = AuditHasher.verify(chain);
        assertFalse(v.valid());
        assertEquals(Long.valueOf(5), v.brokenAtSeq());
    }
}
