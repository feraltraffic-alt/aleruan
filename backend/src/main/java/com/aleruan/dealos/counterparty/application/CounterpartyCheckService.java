package com.aleruan.dealos.counterparty.application;

import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.CheckResult;
import com.aleruan.dealos.counterparty.domain.CheckResultRepository;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.SnapshotAnalyzer;
import com.aleruan.dealos.counterparty.domain.Verdict;
import com.aleruan.dealos.counterparty.registry.LookupRequest;
import com.aleruan.dealos.counterparty.registry.RegistryClient;
import java.util.Comparator;
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
        this.registries = registries.stream().sorted(Comparator.comparingInt(RegistryClient::order)).toList();
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CheckRun check(UUID counterpartyId) {
        Counterparty c = counterparties.get(counterpartyId);
        LocalDate today = LocalDate.now(clock.withZone(PRAGUE));
        LookupRequest request = LookupRequest.of(c.getCountry(), c.getCompanyId(), c.getVatNumber(), c.getLegalName());
        List<String> used = new ArrayList<>();
        List<CheckResult> saved = new ArrayList<>();
        Set<CheckFinding> all = EnumSet.noneOf(CheckFinding.class);
        boolean identityChecked = false; // a registry that knows the company, not only the name screening
        for (RegistryClient registry : registries) {
            if (!registry.supports(request)) {
                continue;
            }
            Optional<RegistrySnapshot> result = registry.lookup(request);
            if (result.isEmpty()) {
                continue;
            }
            RegistrySnapshot snapshot = result.get();
            request = request.with(snapshot);
            used.add(registry.source().name());
            identityChecked |= registry.source() != com.aleruan.dealos.counterparty.domain.CheckSource.SANCTIONS_EU;
            Set<CheckFinding> findings = SnapshotAnalyzer.findings(snapshot, today);
            all.addAll(findings);
            saved.add(results.save(new CheckResult(c.getId(), snapshot, findings)));
        }
        if (!identityChecked) {
            all.add(CheckFinding.MANUAL_REVIEW_REQUIRED); // e.g. Moldova: no connected registry
        }
        Verdict verdict = Verdict.of(all);
        c.applyVerdict(verdict.status(), clock.instant());
        audit.record(CounterpartyService.AUDIT_TYPE, c.getId().toString(), "CHECKED", Map.of(
                "status", verdict.status(),
                "findings", verdict.findings(),
                "sources", used));
        return new CheckRun(verdict, saved);
    }

    @Transactional(readOnly = true)
    public List<CheckResult> history(UUID counterpartyId) {
        counterparties.get(counterpartyId);
        return results.findByCounterpartyIdOrderByCheckedAtDesc(counterpartyId);
    }

    /** Pre-fill a new counterparty form from the first registry that knows this identifier. */
    public Optional<RegistrySnapshot> lookup(String country, String companyId, String vatNumber) {
        LookupRequest request = LookupRequest.of(country, companyId, vatNumber, null);
        for (RegistryClient r : registries) {
            if (r.source() == com.aleruan.dealos.counterparty.domain.CheckSource.SANCTIONS_EU || !r.supports(request)) {
                continue;
            }
            Optional<RegistrySnapshot> s = r.lookup(request);
            if (s.isPresent() && s.get().available() && !Boolean.FALSE.equals(s.get().found()) && s.get().legalName() != null) {
                return s;
            }
            if (s.isPresent()) {
                request = request.with(s.get());
            }
        }
        return Optional.empty();
    }
}
