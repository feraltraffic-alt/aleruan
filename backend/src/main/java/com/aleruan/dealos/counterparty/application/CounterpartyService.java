package com.aleruan.dealos.counterparty.application;

import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRepository;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.counterparty.domain.TaxIdentifiers;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.web.ConflictException;
import com.aleruan.dealos.platform.web.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CounterpartyService {

    public static final String AUDIT_TYPE = "Counterparty";

    /** Input for creating a counterparty; identifiers are normalised and validated here. */
    public record NewCounterparty(String legalName, String country, String companyId, String vatNumber,
                                  String address, String email, String phone, Set<CounterpartyRole> roles,
                                  Set<String> workTypes) {
        public NewCounterparty(String legalName, String country, String companyId, String vatNumber, String address,
                               String email, String phone, Set<CounterpartyRole> roles) {
            this(legalName, country, companyId, vatNumber, address, email, phone, roles, Set.of());
        }
    }

    private final CounterpartyRepository repository;
    private final AuditService audit;
    private final Clock clock;

    CounterpartyService(CounterpartyRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Counterparty create(NewCounterparty input) {
        String country = input.country().toUpperCase(Locale.ROOT);
        String companyId = TaxIdentifiers.normalize(blankToNull(input.companyId()));
        String vat = TaxIdentifiers.normalize(blankToNull(input.vatNumber()));
        TaxIdentifiers.requireValidCompanyId(country, companyId);
        if (vat != null) {
            TaxIdentifiers.parseVat(vat); // throws on a malformed VAT number
        }
        if (companyId != null && repository.existsByCountryAndCompanyId(country, companyId)) {
            throw new ConflictException("Counterparty " + country + " " + companyId + " already exists");
        }
        Counterparty saved = repository.save(new Counterparty(input.legalName().trim(), country, companyId, vat,
                blankToNull(input.address()), blankToNull(input.email()), blankToNull(input.phone()),
                input.roles(), clock.instant()));
        saved.setWorkTypes(input.workTypes());
        audit.record(AUDIT_TYPE, saved.getId().toString(), "CREATED", input);
        return saved;
    }

    /** Replace the work types a subcontractor/supplier covers. */
    @Transactional
    public Counterparty setWorkTypes(UUID id, Set<String> workTypes) {
        Counterparty c = get(id);
        c.setWorkTypes(workTypes);
        audit.record(AUDIT_TYPE, id.toString(), "WORK_TYPES_SET", workTypes);
        return c;
    }

    @Transactional(readOnly = true)
    public List<Counterparty> search(String query, CounterpartyRole role) {
        return repository.search(blankToNull(query), role);
    }

    @Transactional(readOnly = true)
    public Counterparty get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Counterparty " + id + " not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
