package com.aleruan.dealos.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Margin of deals cut by one dimension at a time. Pure arithmetic over amounts already converted to one
 * currency; a deal counts once per group even when several of its lines fall into it.
 */
public final class MarginCuts {

    /** Group key for lines without a subcontractor or work type, and deals without a site country. */
    public static final String NONE = "NONE";

    public enum Cut { DEAL, CUSTOMER, SUBCONTRACTOR, WORK_TYPE, DIRECTION, COUNTRY, MONTH }

    public record Line(String workType, String subcontractorId, String subcontractorName, BigDecimal cost,
                       BigDecimal price) {
    }

    public record Deal(String id, String number, String title, String customerId, String customerName,
                       String direction, String country, String month, List<Line> lines) {
    }

    public record Row(String key, String label, String sub, int deals, BigDecimal price, BigDecimal cost,
                      BigDecimal margin, BigDecimal marginPct) {
    }

    public record Totals(int deals, BigDecimal price, BigDecimal cost, BigDecimal margin, BigDecimal marginPct) {
    }

    public record Report(Totals totals, Map<Cut, List<Row>> cuts) {
    }

    private MarginCuts() {
    }

    public static Report build(List<Deal> deals) {
        Map<Cut, List<Row>> cuts = new LinkedHashMap<>();
        cuts.put(Cut.DEAL, byDeal(deals, d -> d.id(), d -> d.number(), d -> d.title() + " · " + nz(d.customerName())));
        cuts.put(Cut.CUSTOMER, byDeal(deals, Deal::customerId, Deal::customerName, d -> null));
        cuts.put(Cut.SUBCONTRACTOR, byLine(deals, l -> l.subcontractorId() == null ? NONE : l.subcontractorId(),
                l -> l.subcontractorId() == null ? NONE : l.subcontractorName()));
        cuts.put(Cut.WORK_TYPE, byLine(deals, l -> l.workType() == null ? NONE : l.workType(),
                l -> l.workType() == null ? NONE : l.workType()));
        cuts.put(Cut.DIRECTION, byDeal(deals, Deal::direction, Deal::direction, d -> null));
        cuts.put(Cut.COUNTRY, byDeal(deals, d -> d.country() == null ? NONE : d.country(),
                d -> d.country() == null ? NONE : d.country(), d -> null));
        List<Row> months = new ArrayList<>(byDeal(deals, Deal::month, Deal::month, d -> null));
        months.sort(Comparator.comparing(Row::key));
        cuts.put(Cut.MONTH, months);

        BigDecimal price = zero();
        BigDecimal cost = zero();
        for (Deal d : deals) {
            for (Line l : d.lines()) {
                price = price.add(l.price());
                cost = cost.add(l.cost());
            }
        }
        BigDecimal margin = price.subtract(cost);
        return new Report(new Totals(deals.size(), price, cost, margin, pct(margin, price)), cuts);
    }

    private static List<Row> byDeal(List<Deal> deals, Function<Deal, String> key, Function<Deal, String> label,
                                    Function<Deal, String> sub) {
        Map<String, Acc> groups = new LinkedHashMap<>();
        for (Deal d : deals) {
            Acc a = groups.computeIfAbsent(key.apply(d), k -> new Acc(k, label.apply(d), sub.apply(d)));
            a.deals.add(d.id());
            d.lines().forEach(a::add);
        }
        return rows(groups);
    }

    private static List<Row> byLine(List<Deal> deals, Function<Line, String> key, Function<Line, String> label) {
        Map<String, Acc> groups = new LinkedHashMap<>();
        for (Deal d : deals) {
            for (Line l : d.lines()) {
                Acc a = groups.computeIfAbsent(key.apply(l), k -> new Acc(k, label.apply(l), null));
                a.deals.add(d.id());
                a.add(l);
            }
        }
        return rows(groups);
    }

    private static List<Row> rows(Map<String, Acc> groups) {
        return groups.values().stream().map(Acc::row)
                .sorted(Comparator.comparing(Row::margin).reversed().thenComparing(Row::key)).toList();
    }

    static BigDecimal pct(BigDecimal margin, BigDecimal price) {
        return price.signum() == 0 ? null
                : margin.multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final class Acc {
        final String key;
        final String label;
        final String sub;
        final Set<String> deals = new HashSet<>();
        BigDecimal price = zero();
        BigDecimal cost = zero();

        Acc(String key, String label, String sub) {
            this.key = Objects.requireNonNullElse(key, NONE);
            this.label = Objects.requireNonNullElse(label, this.key);
            this.sub = sub;
        }

        void add(Line l) {
            price = price.add(l.price());
            cost = cost.add(l.cost());
        }

        Row row() {
            BigDecimal margin = price.subtract(cost);
            return new Row(key, label, sub, deals.size(), price, cost, margin, pct(margin, price));
        }
    }
}
