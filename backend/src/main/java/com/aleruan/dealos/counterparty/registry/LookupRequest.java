package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.util.Map;
import java.util.Optional;

/**
 * What a registry gets to work with: the counterparty's identifiers plus the snapshots of registries
 * that ran before it (e.g. KRS needs the KRS number that Biała lista returned).
 */
public record LookupRequest(String country, String companyId, String vatNumber, String legalName,
                            Map<CheckSource, RegistrySnapshot> earlier) {

    public LookupRequest {
        earlier = earlier == null ? Map.of() : Map.copyOf(earlier);
    }

    public static LookupRequest of(String country, String companyId, String vatNumber, String legalName) {
        return new LookupRequest(country, companyId, vatNumber, legalName, Map.of());
    }

    public Optional<RegistrySnapshot> earlier(CheckSource source) {
        return Optional.ofNullable(earlier.get(source));
    }

    public LookupRequest with(RegistrySnapshot snapshot) {
        java.util.Map<CheckSource, RegistrySnapshot> next = new java.util.EnumMap<>(CheckSource.class);
        next.putAll(earlier);
        next.put(snapshot.source(), snapshot);
        return new LookupRequest(country, companyId, vatNumber, legalName, next);
    }
}
