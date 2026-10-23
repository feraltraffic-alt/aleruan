package com.aleruan.dealos.site.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SiteTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static Site.Details details(BigDecimal lat, BigDecimal lng, LocalDate start, LocalDate end) {
        return new Site.Details("Kraków office", "ul. Długa 5", "Kraków", "pl", lat, lng, "lena", "pavel", start, end,
                SiteStatus.ACTIVE, null);
    }

    @Test
    void validatesCoordinatesDatesAndCountry() {
        Site site = new Site(UUID.randomUUID(), "Kraków", "PL", Instant.now(), "test");
        site.edit(details(new BigDecimal("50.061400"), new BigDecimal("19.936600"), d("2026-11-02"), d("2026-12-18")));
        assertEquals("PL", site.getCountry());
        assertThrows(IllegalArgumentException.class, () -> site.edit(details(new BigDecimal("50"), null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> site.edit(details(new BigDecimal("91"), BigDecimal.ONE, null, null)));
        assertThrows(IllegalArgumentException.class, () -> site.edit(details(null, null, d("2026-12-18"), d("2026-11-02"))));
        assertThrows(IllegalArgumentException.class, () -> new Site(UUID.randomUUID(), " ", "PL", Instant.now(), "t"));
        assertThrows(IllegalArgumentException.class, () -> new Site(UUID.randomUUID(), "x", "POL", Instant.now(), "t"));
    }

    @Test
    void progressIsWeightedByTaskLengthAndLateTasksAreFlagged() {
        Site site = new Site(UUID.randomUUID(), "Kraków", "PL", Instant.now(), "test");
        assertEquals(0L, (long) site.progressPct());
        site.addTask("Demontáž", "DISMANTLING", null, null, d("2026-11-02"), d("2026-11-03"), 100);   // 2 days done
        SiteTask long_ = site.addTask("Příčky", "INTERIOR", null, null, d("2026-11-04"), d("2026-11-11"), 25); // 8 days
        assertEquals(40L, (long) site.progressPct()); // (2×100 + 8×25) / 10
        assertEquals(1L, site.lateTasks(d("2026-11-12")));
        assertEquals(0L, site.lateTasks(d("2026-11-11")));
        assertThrows(IllegalArgumentException.class,
                () -> site.updateTask(long_.getId(), "x", null, null, d("2026-11-05"), d("2026-11-04"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> site.updateTask(long_.getId(), "x", null, null, d("2026-11-05"), d("2026-11-06"), 101));
        site.removeTask(long_.getId());
        assertEquals(100L, (long) site.progressPct());
        assertEquals(2L, (long) site.addTask("Malování", null, null, null, d("2026-11-12"), d("2026-11-12"), 0).getPosition());
        assertEquals(2L, (long) site.getTasks().size());
    }
}
