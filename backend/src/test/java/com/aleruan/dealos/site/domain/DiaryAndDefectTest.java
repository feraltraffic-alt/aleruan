package com.aleruan.dealos.site.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiaryAndDefectTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

    @Test
    void diaryEntriesLockAfterADayAndOnlyForTheirAuthor() {
        DiaryEntry e = new DiaryEntry(UUID.randomUUID(), TODAY, "Sunny, 12 °C", 6, "Lešení postaveno", null, "pavel", NOW, TODAY);
        assertTrue(e.editableBy("pavel", NOW.plus(Duration.ofHours(23))));
        assertFalse(e.editableBy("lena", NOW));
        assertFalse(e.editableBy("pavel", NOW.plus(Duration.ofHours(24))));
        e.edit(TODAY.minusDays(1), null, 7, "Lešení postaveno, kontrola BOZP", "pavel", NOW.plusSeconds(60), TODAY);
        assertEquals(7, e.getWorkers());
        assertNull(e.getWeather());
        assertThrows(IllegalStateException.class,
                () -> e.edit(TODAY, null, 7, "x", "pavel", NOW.plus(Duration.ofDays(2)), TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> new DiaryEntry(UUID.randomUUID(), TODAY.plusDays(1), null, 1, "zítra", null, "pavel", NOW, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> new DiaryEntry(UUID.randomUUID(), TODAY, null, -1, "x", null, "pavel", NOW, TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> new DiaryEntry(UUID.randomUUID(), TODAY, null, 1, "  ", null, "pavel", NOW, TODAY));
    }

    private static Defect.Details details(DefectSeverity sev, LocalDate found, LocalDate due) {
        return new Defect.Details("Prasklina v omítce", null, "2. NP, chodba", sev, null, null, found, due);
    }

    @Test
    void defectDueDateFollowsSeverityAndWarrantyFollowsHandover() {
        Defect d = new Defect(UUID.randomUUID(), 1, details(DefectSeverity.CRITICAL, TODAY, null), null, NOW, "lena");
        assertEquals(TODAY.plusDays(2), d.getDueDate());
        assertFalse(d.isWarranty());
        assertTrue(d.isOverdue(TODAY.plusDays(3)));
        Defect w = new Defect(UUID.randomUUID(), 2, details(null, TODAY, TODAY.plusDays(30)), TODAY.minusMonths(3), NOW, "lena");
        assertTrue(w.isWarranty());
        assertEquals(DefectSeverity.MINOR, w.getSeverity());
        assertThrows(IllegalArgumentException.class,
                () -> new Defect(UUID.randomUUID(), 3, details(null, TODAY, TODAY.minusDays(1)), null, NOW, "lena"));
    }

    @Test
    void defectStatusFlowNeedsReasonsWhereItMatters() {
        Defect d = new Defect(UUID.randomUUID(), 1, details(DefectSeverity.MAJOR, TODAY, null), null, NOW, "lena");
        assertThrows(IllegalStateException.class, () -> d.moveTo(DefectStatus.CLOSED, null, "lena", NOW));
        d.moveTo(DefectStatus.FIXED, "Přetmeleno", "pavel", NOW);
        assertFalse(d.isOverdue(TODAY.plusYears(1)), "a fixed defect is waiting for the check, not overdue");
        assertThrows(IllegalArgumentException.class, () -> d.moveTo(DefectStatus.OPEN, " ", "lena", NOW));
        d.moveTo(DefectStatus.OPEN, "Prasklina se vrátila", "lena", NOW);
        assertNull(d.getFixedAt());
        d.moveTo(DefectStatus.FIXED, null, "pavel", NOW);
        d.moveTo(DefectStatus.CLOSED, null, "lena", NOW);
        assertEquals("lena", d.getClosedBy());
        assertFalse(d.getStatus().isOpen());
        Defect other = new Defect(UUID.randomUUID(), 2, details(null, TODAY, null), null, NOW, "lena");
        assertThrows(IllegalArgumentException.class, () -> other.moveTo(DefectStatus.DISMISSED, null, "lena", NOW));
        other.moveTo(DefectStatus.DISMISSED, "Není vada, dle projektu", "lena", NOW);
    }

    @Test
    void warrantyRunsFromHandover() {
        Site site = new Site(UUID.randomUUID(), "Byt", "CZ", NOW, "lena");
        assertNull(site.warrantyUntil());
        site.setHandover(LocalDate.of(2026, 11, 10), 24);
        assertEquals(LocalDate.of(2028, 11, 9), site.warrantyUntil());
        assertThrows(IllegalArgumentException.class, () -> site.setHandover(TODAY, 121));
        site.setHandover(null, 24);
        assertNull(site.getWarrantyMonths());
    }
}
