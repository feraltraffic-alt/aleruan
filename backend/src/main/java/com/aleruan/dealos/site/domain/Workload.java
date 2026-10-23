package com.aleruan.dealos.site.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Weekly load of one subcontractor: how many of its tasks run in parallel in each week. */
public final class Workload {

    public record Span(LocalDate start, LocalDate end) {
    }

    public record Week(LocalDate monday, int parallelTasks) {
    }

    private Workload() {
    }

    /** Mondays of the weeks touching [from, to]. */
    public static List<LocalDate> weeks(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The period ends before it starts");
        }
        List<LocalDate> mondays = new ArrayList<>();
        for (LocalDate m = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); !m.isAfter(to); m = m.plusWeeks(1)) {
            mondays.add(m);
        }
        return mondays;
    }

    /**
     * For each week, the highest number of tasks running on the same working day (Mon–Fri), so two
     * tasks in one week that do not overlap count as one crew, not two.
     */
    public static List<Week> perWeek(List<Span> spans, LocalDate from, LocalDate to) {
        List<Week> result = new ArrayList<>();
        for (LocalDate monday : weeks(from, to)) {
            int peak = 0;
            for (int d = 0; d < 5; d++) {
                LocalDate day = monday.plusDays(d);
                int running = 0;
                for (Span s : spans) {
                    if (!day.isBefore(s.start()) && !day.isAfter(s.end())) {
                        running++;
                    }
                }
                peak = Math.max(peak, running);
            }
            result.add(new Week(monday, peak));
        }
        return result;
    }
}
