package com.aleruan.dealos.analytics.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarginCutsTest {

    private static MarginCuts.Line line(String workType, String sub, String cost, String price) {
        return new MarginCuts.Line(workType, sub, sub == null ? null : "Sub " + sub, new BigDecimal(cost), new BigDecimal(price));
    }

    private static final List<MarginCuts.Deal> DEALS = List.of(
            new MarginCuts.Deal("d1", "D-1", "Hala", "c1", "Alfa", "CONSTRUCTION", "PL", "2026-09", List.of(
                    line("INSTALLATION", "s1", "1000.00", "1300.00"), line("ELECTRICAL", "s2", "500.00", "600.00"))),
            new MarginCuts.Deal("d2", "D-2", "Fasáda", "c1", "Alfa", "CONSTRUCTION", null, "2026-10", List.of(
                    line("INSTALLATION", "s1", "200.00", "260.00"), line(null, null, "100.00", "100.00"))),
            new MarginCuts.Deal("d3", "D-3", "Dodávka", "c2", "Beta", "SUPPLY", "CZ", "2026-09", List.of(
                    line(null, null, "0.00", "0.00"))));

    @Test
    void totalsAndMarginPercent() {
        MarginCuts.Report r = MarginCuts.build(DEALS);
        assertEquals(3, r.totals().deals());
        assertEquals(new BigDecimal("2260.00"), r.totals().price());
        assertEquals(new BigDecimal("1800.00"), r.totals().cost());
        assertEquals(new BigDecimal("460.00"), r.totals().margin());
        assertEquals(new BigDecimal("20.4"), r.totals().marginPct());
    }

    @Test
    void lineCutsCountEachDealOnceAndGroupMissingValues() {
        MarginCuts.Report r = MarginCuts.build(DEALS);
        List<MarginCuts.Row> subs = r.cuts().get(MarginCuts.Cut.SUBCONTRACTOR);
        MarginCuts.Row s1 = subs.get(0);
        assertEquals("s1", s1.key());
        assertEquals("Sub s1", s1.label());
        assertEquals(2, s1.deals());
        assertEquals(new BigDecimal("360.00"), s1.margin());
        MarginCuts.Row none = subs.stream().filter(x -> x.key().equals(MarginCuts.NONE)).findFirst().orElseThrow();
        assertEquals(2, none.deals());
        assertNull(r.cuts().get(MarginCuts.Cut.DEAL).stream().filter(x -> x.key().equals("d3")).findFirst()
                .orElseThrow().marginPct(), "no price, no percent");

        List<MarginCuts.Row> customers = r.cuts().get(MarginCuts.Cut.CUSTOMER);
        assertEquals("Alfa", customers.get(0).label());
        assertEquals(2, customers.get(0).deals());
        assertEquals(MarginCuts.NONE, r.cuts().get(MarginCuts.Cut.COUNTRY).stream()
                .filter(x -> x.margin().compareTo(new BigDecimal("60.00")) == 0).findFirst().orElseThrow().key());
    }

    @Test
    void monthsAreInCalendarOrder() {
        List<MarginCuts.Row> months = MarginCuts.build(DEALS).cuts().get(MarginCuts.Cut.MONTH);
        assertEquals(List.of("2026-09", "2026-10"), months.stream().map(MarginCuts.Row::key).toList());
        assertEquals(2, months.get(0).deals());
    }
}
