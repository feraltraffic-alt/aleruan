package com.aleruan.dealos.docimport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportDocumentTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    private static ImportDocument doc() {
        return new ImportDocument(UUID.randomUUID(), UUID.randomUUID(), "f.pdf", "application/pdf", "a".repeat(64), NOW, "lena");
    }

    @Test
    void passingFailuresAreRetriedThreeTimesOthersFailAtOnce() {
        ImportDocument d = doc();
        for (int i = 1; i < ImportDocument.MAX_ATTEMPTS; i++) {
            d.start(NOW);
            d.failed("busy", true, NOW);
            assertEquals(ImportStatus.QUEUED, d.getStatus());
        }
        d.start(NOW);
        d.failed("busy", true, NOW);
        assertEquals(ImportStatus.FAILED, d.getStatus());

        ImportDocument refused = doc();
        refused.start(NOW);
        refused.failed("Claude declined to read this document", false, NOW);
        assertEquals(ImportStatus.FAILED, refused.getStatus());
        assertEquals(1, refused.getAttempts());
    }

    @Test
    void nobodyRejectsOrConfirmsWhileItIsBeingRead() {
        ImportDocument d = doc();
        d.start(NOW);
        assertThrows(IllegalStateException.class, () -> d.reject("no", NOW, "lena"));
        assertThrows(IllegalStateException.class, () -> d.confirm(DocType.OTHER, "{}", null, "ARCHIVED", null, NOW, "lena"));
        d.extracted(DocType.OTHER, "{}", "[]", "m", 1, 1, NOW);
        d.reject("not ours", NOW, "lena");
        assertEquals(ImportStatus.REJECTED, d.getStatus());
        assertThrows(IllegalStateException.class, d::requeue);
    }
}
