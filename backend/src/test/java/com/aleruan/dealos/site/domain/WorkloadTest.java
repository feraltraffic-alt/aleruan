package com.aleruan.dealos.site.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkloadTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void weeksStartOnMondayAndCoverThePeriod() {
        // 2026-10-23 is a Friday
        assertEquals(List.of(d("2026-10-19"), d("2026-10-26"), d("2026-11-02")), Workload.weeks(d("2026-10-23"), d("2026-11-02")));
        assertThrows(IllegalArgumentException.class, () -> Workload.weeks(d("2026-10-23"), d("2026-10-22")));
    }

    @Test
    void countsTasksRunningOnTheSameDayNotJustInTheSameWeek() {
        List<Workload.Span> spans = List.of(
                new Workload.Span(d("2026-10-26"), d("2026-10-27")),   // Mon–Tue
                new Workload.Span(d("2026-10-29"), d("2026-10-30")),   // Thu–Fri: same week, no overlap
                new Workload.Span(d("2026-11-02"), d("2026-11-20")),   // three weeks
                new Workload.Span(d("2026-11-04"), d("2026-11-05")));  // overlaps the long one
        List<Workload.Week> weeks = Workload.perWeek(spans, d("2026-10-26"), d("2026-11-22"));
        assertEquals(List.of(1, 2, 1, 1), weeks.stream().map(Workload.Week::parallelTasks).toList());
        assertEquals(d("2026-11-02"), weeks.get(1).monday());
    }

    @Test
    void weekendOnlyWorkDoesNotCountAsWeekdayLoad() {
        List<Workload.Week> weeks = Workload.perWeek(List.of(new Workload.Span(d("2026-10-24"), d("2026-10-25"))),
                d("2026-10-19"), d("2026-10-25"));
        assertEquals(0L, (long) weeks.get(0).parallelTasks());
    }
}
