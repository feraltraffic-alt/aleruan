package com.aleruan.dealos.counterparty.application;

import com.aleruan.dealos.counterparty.domain.BankAccounts;
import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.CheckResult;
import com.aleruan.dealos.counterparty.domain.CheckResultRepository;
import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.SnapshotAnalyzer;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.counterparty.domain.Verdict;
import com.aleruan.dealos.counterparty.registry.BialaListaClient;
import com.aleruan.dealos.counterparty.registry.LookupRequest;
import com.aleruan.dealos.counterparty.registry.RegistrDphClient;
import com.aleruan.dealos.platform.audit.AuditService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Before paying a subcontractor: is the account on their invoice the one the tax registry publishes?
 * CZ: Registr DPH published accounts (§ 109 ZDPH liability). PL: Biała lista (payments over 15 000 PLN).
 */
@Service
public class AccountCheckService {

    public record AccountCheck(Verdict verdict, CheckResult result) {
    }

    private final CounterpartyService counterparties;
    private final CheckResultRepository results;
    private final ObjectProvider<RegistrDphClient> registrDph;
    private final ObjectProvider<BialaListaClient> bialaLista;
    private final AuditService audit;
    private final Clock clock;

    AccountCheckService(CounterpartyService counterparties, CheckResultRepository results,
                        ObjectProvider<RegistrDphClient> registrDph, ObjectProvider<BialaListaClient> bialaLista,
                        AuditService audit, Clock clock) {
        this.counterparties = counterparties;
        this.results = results;
        this.registrDph = registrDph;
        this.bialaLista = bialaLista;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AccountCheck check(UUID counterpartyId, String account) {
        if (account == null || account.isBlank()) {
            throw new IllegalArgumentException("Account number is required");
        }
        Counterparty c = counterparties.get(counterpartyId);
        Optional<RegistrySnapshot> snapshot = switch (c.getCountry()) {
            case "CZ" -> czech(c);
            case "PL" -> polish(c, account);
            default -> Optional.empty();
        };
        Set<CheckFinding> findings = snapshot
                .<Set<CheckFinding>>map(s -> SnapshotAnalyzer.accountFindings(s, account))
                .orElse(EnumSet.of(CheckFinding.MANUAL_REVIEW_REQUIRED));
        RegistrySnapshot evidence = snapshot.orElse(RegistrySnapshot.builder(CheckSource.MANUAL, clock.instant()).build());
        CheckResult saved = results.save(new CheckResult(c.getId(), evidence, findings, BankAccounts.comparable(account)));
        Verdict verdict = Verdict.of(findings);
        audit.record(CounterpartyService.AUDIT_TYPE, c.getId().toString(), "ACCOUNT_CHECKED", Map.of(
                "account", BankAccounts.comparable(account), "status", verdict.status(), "source", evidence.source()));
        return new AccountCheck(verdict, saved);
    }

    private Optional<RegistrySnapshot> czech(Counterparty c) {
        RegistrDphClient client = registrDph.getIfAvailable();
        String dic = client == null ? null : dicDigits(c);
        return dic == null ? Optional.empty() : Optional.of(client.status(dic));
    }

    private Optional<RegistrySnapshot> polish(Counterparty c, String account) {
        BialaListaClient client = bialaLista.getIfAvailable();
        String nrb = BankAccounts.polishNrb(account);
        if (client == null || nrb == null || !TaxIdentifiers.isValidPlNip(c.getCompanyId())) {
            return Optional.empty();
        }
        return Optional.of(client.checkAccount(c.getCompanyId(), nrb));
    }

    private static String dicDigits(Counterparty c) {
        LookupRequest r = LookupRequest.of(c.getCountry(), c.getCompanyId(), c.getVatNumber(), c.getLegalName());
        if (r.vatNumber() != null) {
            TaxIdentifiers.VatId vat = TaxIdentifiers.parseVat(r.vatNumber());
            if ("CZ".equals(vat.country()) && vat.number().matches("\\d{8,10}")) {
                return vat.number();
            }
        }
        return TaxIdentifiers.isValidCzIco(c.getCompanyId()) ? TaxIdentifiers.normalize(c.getCompanyId()) : null;
    }
}
