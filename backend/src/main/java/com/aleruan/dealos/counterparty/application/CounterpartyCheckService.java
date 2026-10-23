package com.aleruan.dealos.counterparty.application;

import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.CheckResult;
import com.aleruan.dealos.counterparty.domain.CheckResultRepository;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.SnapshotAnalyzer;
import com.aleruan.dealos.counterparty.domain.Verdict;
import com.aleruan.dealos.counterparty.registry.RegistryClient;
import com.aleruan.dealos.platform.audit.AuditService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs every registry that covers the counterparty, stores evidence, sets the traffic light. */
@Service
public class CounterpartyCheckService {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    public record CheckRun(Verdict verdict, List<CheckResult> results) {
    }

    private final CounterpartyService counterparties;
    private final CheckResultRepository results;
    private final List<RegistryClient> registries;
    private final AuditService audit;
    private final Clock clock;

    CounterpartyCheckService(CounterpartyService counterparties, CheckResultRepository results,
                             List<RegistryClient> registries, AuditService audit, Clock clock) {
        this.counterparties = counterparties;
        this.results = results;
        this.registries = registries;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CheckRun check(UUID counterpartyId) {
        Counterparty c = counterparties.get(counterpartyId);
        LocalDate today = LocalDate.now(clock.withZone(PRAGUE));
        List<RegistryClient> applicable = registries.stream()
                .filter(r -> r.supports(c.getCountry(), c.getCompanyId(), c.getVatNumber()))
                .toList();

        Verdict verdict;
        List<CheckResult> saved = new ArrayList<>();
        if (applicable.isEmpty()) {
            verdict = Verdict.manualReviewRequired();
        } else {
            Set<CheckFinding> all = EnumSet.noneOf(CheckFinding.class);
            for (RegistryClient registry : applicable) {
                RegistrySnapshot snapshot = registry.lookup(c.getCountry(), c.getCompanyId(), c.getVatNumber());
                Set<CheckFinding> findings = SnapshotAnalyzer.findings(snapshot, today);
                all.addAll(findings);
                saved.add(results.save(new CheckResult(c.getId(), snapshot, findings)));
            }
            verdict = Verdict.of(all);
        }
        c.applyVerdict(verdict.status(), clock.instant());
        audit.record(CounterpartyService.AUDIT_TYPE, c.getId().toString(), "CHECKED", Map.of(
                "status", verdict.status(),
                "findings", verdict.findings(),
                "sources", applicable.stream().map(r -> r.source().name()).toList()));
        return new CheckRun(verdict, saved);
    }

    @Transactional(readOnly = true)
    public List<CheckResult> history(UUID counterpartyId) {
        counterparties.get(counterpartyId);
        return results.findByCounterpartyIdOrderByCheckedAtDesc(counterpartyId);
    }

    /** Pre-fill a new counterparty form from the first registry that knows this identifier. */
    public Optional<RegistrySnapshot> lookup(String country, String companyId, String vatNumber) {
        return registries.stream()
                .filter(r -> r.supports(country, companyId, vatNumber))
                .map(r -> r.lookup(country, companyId, vatNumber))
                .filter(s -> s.available() && !Boolean.FALSE.equals(s.found()) && s.legalName() != null)
                .findFirst();
    }
}
