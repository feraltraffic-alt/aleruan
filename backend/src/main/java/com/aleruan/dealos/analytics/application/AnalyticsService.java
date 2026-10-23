package com.aleruan.dealos.analytics.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.DealEconomics;
import com.aleruan.dealos.deal.DealCatalog.EconomicsLine;
import com.aleruan.dealos.evidence.EvidenceCatalog;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import com.aleruan.dealos.platform.rates.ExchangeRates;
import com.aleruan.dealos.site.SiteCatalog;
import com.aleruan.dealos.site.SiteCatalog.SiteHealth;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Margin cuts over agreed deals (or open quotes) in one currency at today's ČNB rates, the state of
 * running sites, and risks: counterparties flagged in registries and incomplete evidence packages.
 */
@Service
public class AnalyticsService {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    static final Set<String> CONTRACTED = Set.of("WON", "IN_PROGRESS", "COMPLETED");
    static final Set<String> PIPELINE = Set.of("QUOTED");
    private static final Set<String> RUNNING = Set.of("WON", "IN_PROGRESS");

    public enum Scope { CONTRACTED, PIPELINE }

    public record Filter(Scope scope, LocalDate from, LocalDate to, String direction, String country, String currency) {
    }

    public record RateUsed(String currency, BigDecimal czkPerUnit, LocalDate validFor, String source) {
    }

    public record Margin(String currency, LocalDate ratesOn, List<RateUsed> rates, List<String> missingRates,
                         MarginCuts.Totals totals, Map<MarginCuts.Cut, List<MarginCuts.Row>> cuts) {
    }

    public record SiteRow(UUID siteId, UUID dealId, String dealNumber, String name, String city, String country,
                          String status, LocalDate plannedEnd, int progressPct, long lateTasks, long openDefects,
                          long overdueDefects, int pendingChangeOrders, BigDecimal pendingChangePrice,
                          String currency) {
    }

    public record PartyRisk(UUID id, String name, String country, String status, Set<String> roles,
                            List<String> deals) {
    }

    public record PackageRisk(UUID dealId, String number, String title, String status, int missing, int warn,
                              List<EvidenceCheck> gaps) {
    }

    public record Health(List<SiteRow> sites, List<PartyRisk> counterparties, List<PackageRisk> packages) {
    }

    private final DealCatalog deals;
    private final SiteCatalog sites;
    private final CounterpartyLookup counterparties;
    private final EvidenceCatalog evidence;
    private final ExchangeRates rates;
    private final Clock clock;

    AnalyticsService(DealCatalog deals, SiteCatalog sites, CounterpartyLookup counterparties, EvidenceCatalog evidence,
                     ExchangeRates rates, Clock clock) {
        this.deals = deals;
        this.sites = sites;
        this.counterparties = counterparties;
        this.evidence = evidence;
        this.rates = rates;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Margin margin(Filter f) {
        String target = f.currency() == null || f.currency().isBlank() ? "CZK" : f.currency().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now(clock.withZone(PRAGUE));
        List<DealEconomics> list = deals.economics(f.scope() == Scope.PIPELINE ? PIPELINE : CONTRACTED).stream()
                .filter(d -> f.direction() == null || f.direction().equalsIgnoreCase(d.direction()))
                .filter(d -> f.country() == null || f.country().equalsIgnoreCase(d.siteCountry()))
                .filter(d -> f.from() == null || !day(d).isBefore(f.from()))
                .filter(d -> f.to() == null || !day(d).isAfter(f.to()))
                .toList();

        Map<String, ExchangeRates.Rate> found = new LinkedHashMap<>();
        Set<String> missing = new TreeSet<>();
        Function<String, ExchangeRates.Rate> rate = c -> found.computeIfAbsent(c, code -> {
            try {
                return rates.rate(code, today);
            } catch (RuntimeException e) {
                missing.add(code);
                return null;
            }
        });
        ExchangeRates.Rate to = rate.apply(target);
        if (to == null) {
            throw new IllegalArgumentException("No exchange rate for " + target + " today");
        }

        Map<UUID, String> names = new HashMap<>();
        Function<UUID, String> name = id -> id == null ? null : names.computeIfAbsent(id,
                k -> counterparties.find(k).map(CounterpartySummary::legalName).orElse(k.toString()));
        List<MarginCuts.Deal> converted = new ArrayList<>();
        for (DealEconomics d : list) {
            ExchangeRates.Rate from = rate.apply(d.currency());
            if (from == null) {
                continue; // reported in missingRates
            }
            List<MarginCuts.Line> lines = d.lines().stream().map(l -> new MarginCuts.Line(l.workType(),
                    l.subcontractorId() == null ? null : l.subcontractorId().toString(), name.apply(l.subcontractorId()),
                    convert(l.cost(), from, to), convert(l.price(), from, to))).toList();
            converted.add(new MarginCuts.Deal(d.id().toString(), d.number(), d.title(), d.customerId().toString(),
                    name.apply(d.customerId()), d.direction(), d.siteCountry(), day(d).toString().substring(0, 7), lines));
        }
        MarginCuts.Report report = MarginCuts.build(converted);
        List<RateUsed> used = found.values().stream().filter(Objects::nonNull)
                .map(r -> new RateUsed(r.currency(), r.czkPerUnit(), r.validFor(), r.source())).toList();
        return new Margin(target, today, used, List.copyOf(missing), report.totals(), report.cuts());
    }

    @Transactional(readOnly = true)
    public Health health() {
        Set<String> all = new LinkedHashSet<>(CONTRACTED);
        Map<UUID, DealEconomics> byId = deals.economics(all).stream()
                .collect(Collectors.toMap(DealEconomics::id, d -> d, (a, b) -> a, LinkedHashMap::new));

        List<SiteRow> siteRows = sites.health().stream()
                .filter(s -> !"DONE".equals(s.status()) || s.openDefects() > 0)
                .map(s -> siteRow(s, byId.get(s.dealId())))
                .sorted(Comparator.comparingLong((SiteRow s) -> s.lateTasks() + s.overdueDefects()).reversed()
                        .thenComparing(Comparator.comparingLong(SiteRow::openDefects).reversed()))
                .toList();

        Map<UUID, PartyRisk> parties = new LinkedHashMap<>();
        for (DealEconomics d : byId.values()) {
            if (!RUNNING.contains(d.status())) {
                continue;
            }
            party(parties, d.customerId(), "CUSTOMER", d.number());
            d.lines().stream().map(EconomicsLine::subcontractorId).filter(Objects::nonNull).distinct()
                    .forEach(id -> party(parties, id, "SUBCONTRACTOR", d.number()));
        }
        List<PartyRisk> risky = parties.values().stream().filter(p -> !"GREEN".equals(p.status()))
                .sorted(Comparator.comparingInt((PartyRisk p) -> severity(p.status())).reversed()
                        .thenComparing(PartyRisk::name))
                .toList();

        List<PackageRisk> packages = new ArrayList<>();
        for (DealEconomics d : byId.values()) {
            List<EvidenceCheck> gaps = evidence.gaps(d.id()).stream()
                    .filter(c -> c.level() != EvidenceCheck.Level.OK).toList();
            int missingCount = (int) gaps.stream().filter(c -> c.level() == EvidenceCheck.Level.MISSING).count();
            if (!gaps.isEmpty()) {
                packages.add(new PackageRisk(d.id(), d.number(), d.title(), d.status(), missingCount,
                        gaps.size() - missingCount, gaps));
            }
        }
        packages.sort(Comparator.comparingInt(PackageRisk::missing).reversed()
                .thenComparing(Comparator.comparingInt(PackageRisk::warn).reversed())
                .thenComparing(PackageRisk::number));
        return new Health(siteRows, risky, packages);
    }

    private SiteRow siteRow(SiteHealth s, DealEconomics d) {
        String number = d != null ? d.number() : deals.find(s.dealId()).map(DealCatalog.DealSummary::number).orElse(null);
        return new SiteRow(s.siteId(), s.dealId(), number, s.name(), s.city(), s.country(), s.status(), s.plannedEnd(),
                s.progressPct(), s.lateTasks(), s.openDefects(), s.overdueDefects(),
                d == null ? 0 : d.pendingChangeOrders(), d == null ? BigDecimal.ZERO : d.pendingChangePrice(),
                d == null ? null : d.currency());
    }

    private void party(Map<UUID, PartyRisk> parties, UUID id, String role, String dealNumber) {
        PartyRisk p = parties.get(id);
        if (p == null) {
            Optional<CounterpartySummary> c = counterparties.find(id);
            if (c.isEmpty()) {
                return;
            }
            p = new PartyRisk(id, c.get().legalName(), c.get().country(), c.get().checkStatus(), new TreeSet<>(),
                    new ArrayList<>());
            parties.put(id, p);
        }
        p.roles().add(role);
        if (!p.deals().contains(dealNumber)) {
            p.deals().add(dealNumber);
        }
    }

    /** Red first, then never checked, then yellow. */
    private static int severity(String status) {
        if (status == null) {
            return 1;
        }
        return switch (status) {
            case "RED" -> 2;
            case "YELLOW" -> 0;
            default -> -1;
        };
    }

    private static LocalDate day(DealEconomics d) {
        return d.createdAt().atZone(PRAGUE).toLocalDate();
    }

    static BigDecimal convert(BigDecimal amount, ExchangeRates.Rate from, ExchangeRates.Rate to) {
        if (from.currency().equalsIgnoreCase(to.currency())) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(from.czkPerUnit()).divide(to.czkPerUnit(), 2, RoundingMode.HALF_UP);
    }
}
