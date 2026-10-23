package com.aleruan.dealos.counterparty.application;

import com.aleruan.dealos.counterparty.CounterpartyIntake;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRepository;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.platform.audit.AuditService;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CounterpartyIntakeImpl implements CounterpartyIntake {

    private static final Logger log = LoggerFactory.getLogger(CounterpartyIntakeImpl.class);

    private final CounterpartyRepository repository;
    private final CounterpartyService service;
    private final CounterpartyCheckService checks;
    private final AccountCheckService accounts;
    private final AuditService audit;

    CounterpartyIntakeImpl(CounterpartyRepository repository, CounterpartyService service,
                           CounterpartyCheckService checks, AccountCheckService accounts, AuditService audit) {
        this.repository = repository;
        this.service = service;
        this.checks = checks;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CounterpartySummary> match(String country, String companyId, String vatNumber) {
        String cid = normalize(companyId);
        if (cid != null && country != null) {
            Optional<Counterparty> c = repository.findFirstByCountryAndCompanyId(country.toUpperCase(Locale.ROOT), cid);
            if (c.isPresent()) {
                return c.map(CounterpartyLookupImpl::summary);
            }
        }
        String vat = normalize(vatNumber);
        return vat == null ? Optional.empty()
                : repository.findFirstByVatNumber(vat).map(CounterpartyLookupImpl::summary);
    }

    @Override
    @Transactional
    public CounterpartySummary create(NewParty p) {
        Counterparty created = service.create(new CounterpartyService.NewCounterparty(p.legalName(), p.country(),
                p.companyId(), p.vatNumber(), p.address(), null, null, Set.of(CounterpartyRole.valueOf(p.role()))));
        return CounterpartyLookupImpl.summary(created);
    }

    @Override
    public void runChecks(UUID id) {
        try {
            checks.check(id);
        } catch (RuntimeException e) {
            // registries down or the country has none: it stays unchecked and shows up as a risk
            log.warn("Registry check of counterparty {} failed: {}", id, e.toString());
        }
    }

    @Override
    @Transactional
    public void ensureRole(UUID id, String role) {
        Counterparty c = service.get(id);
        if (c.addRole(CounterpartyRole.valueOf(role))) {
            audit.record(CounterpartyService.AUDIT_TYPE, id.toString(), "ROLE_ADDED", Map.of("role", role));
        }
    }

    @Override
    public String checkAccount(UUID id, String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        try {
            return accounts.check(id, account).verdict().status().name();
        } catch (RuntimeException e) {
            log.warn("Account check for counterparty {} failed: {}", id, e.toString());
            return null;
        }
    }

    private static String normalize(String s) {
        return s == null || s.isBlank() ? null : TaxIdentifiers.normalize(s);
    }
}
