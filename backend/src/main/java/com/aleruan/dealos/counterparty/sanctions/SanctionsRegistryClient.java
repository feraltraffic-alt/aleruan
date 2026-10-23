package com.aleruan.dealos.counterparty.sanctions;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.registry.LookupRequest;
import com.aleruan.dealos.counterparty.registry.RegistryClient;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Screens every counterparty name against the last imported EU list (only once a list exists). */
@Component
class SanctionsRegistryClient implements RegistryClient {

    private final SanctionsService sanctions;
    private final Clock clock;

    SanctionsRegistryClient(SanctionsService sanctions, Clock clock) {
        this.sanctions = sanctions;
        this.clock = clock;
    }

    @Override
    public CheckSource source() {
        return CheckSource.SANCTIONS_EU;
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean supports(LookupRequest request) {
        return request.legalName() != null && sanctions.lastImport().isPresent();
    }

    @Override
    public Optional<RegistrySnapshot> lookup(LookupRequest request) {
        List<SanctionsService.Match> matches = sanctions.findByName(request.legalName());
        String listVersion = sanctions.lastImport().map(i -> "EU list " + i.getImportedAt() + " sha256 "
                + i.getFileSha256().substring(0, 12)).orElse(null);
        String reference = matches.isEmpty() ? listVersion
                : matches.stream().map(SanctionsService.Match::logicalId).distinct().collect(Collectors.joining(","))
                + " (" + listVersion + ")";
        return Optional.of(RegistrySnapshot.builder(CheckSource.SANCTIONS_EU, clock.instant())
                .found(true)
                .sanctionsMatch(!matches.isEmpty())
                .requestId(reference)
                .rawResponse(matches.stream().map(m -> m.logicalId() + ";" + m.subjectType() + ";" + m.listedName())
                        .collect(Collectors.joining("\n")))
                .build());
    }
}
