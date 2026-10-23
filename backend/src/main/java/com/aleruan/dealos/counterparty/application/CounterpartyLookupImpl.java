package com.aleruan.dealos.counterparty.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRepository;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CounterpartyLookupImpl implements CounterpartyLookup {

    private final CounterpartyRepository repository;

    CounterpartyLookupImpl(CounterpartyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CounterpartySummary> find(UUID id) {
        return repository.findById(id).map(CounterpartyLookupImpl::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CounterpartySummary> withRole(String role, String workType, String country) {
        return repository.search(null, CounterpartyRole.valueOf(role)).stream()
                .filter(c -> workType == null || c.getWorkTypes().contains(workType))
                .filter(c -> country == null || country.equalsIgnoreCase(c.getCountry()))
                .map(CounterpartyLookupImpl::summary)
                .toList();
    }

    static CounterpartySummary summary(Counterparty c) {
        return new CounterpartySummary(c.getId(), c.getLegalName(), c.getCountry(),
                c.getRoles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                c.getWorkTypes(), c.getCheckStatus() == null ? null : c.getCheckStatus().name());
    }
}
