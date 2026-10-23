package com.aleruan.dealos.site.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptanceStageTest {

    private static AcceptanceStage stage() {
        AcceptanceStage s = new AcceptanceStage(UUID.randomUUID(), 1, Instant.now(), "lena");
        s.edit("Etapa 1", null, new BigDecimal("8540"), null, Set.of());
        return s;
    }

    @Test
    void handoverRulesRequireWhoDecidedAndRemarksUnlessClean() {
        AcceptanceStage s = stage();
        assertEquals(new BigDecimal("8540.00"), s.getAmount());
        assertThrows(IllegalStateException.class, () -> s.decide(StageStatus.ACCEPTED, "x", null, null, Instant.now()));
        s.submit(Instant.now());
        assertThrows(IllegalStateException.class, () -> s.edit("x", null, BigDecimal.ONE, null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> s.decide(StageStatus.ACCEPTED, " ", null, null, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> s.decide(StageStatus.ACCEPTED_WITH_REMARKS, "Herr Krüger", " ", null, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> s.decide(StageStatus.SUBMITTED, "x", null, null, Instant.now()));
        s.decide(StageStatus.ACCEPTED_WITH_REMARKS, "Herr Krüger", "Sockel", LocalDate.of(2026, 11, 30), Instant.now());
        assertEquals(LocalDate.of(2026, 11, 30), s.getFixUntil());
        assertThrows(IllegalStateException.class, () -> s.submit(Instant.now()));
    }

    @Test
    void rejectedStageCanBeFixedAndHandedOverAgain() {
        AcceptanceStage s = stage();
        s.submit(Instant.now());
        s.decide(StageStatus.REJECTED, "Herr Krüger", "Nicht fertig", LocalDate.of(2026, 11, 30), Instant.now());
        assertNull(s.getFixUntil(), "a deadline only applies to acceptance with remarks");
        s.edit("Etapa 1", "opraveno", new BigDecimal("8540"), null, Set.of());
        s.submit(Instant.now());
        assertNull(s.getDecidedBy());
        s.withdraw();
        assertEquals(StageStatus.PLANNED, s.getStatus());
        assertThrows(IllegalArgumentException.class, () -> s.edit("x", null, new BigDecimal("-1"), null, Set.of()));
        assertEquals("D-2026-0003-P1", s.protocolNumber("D-2026-0003"));
    }
}
