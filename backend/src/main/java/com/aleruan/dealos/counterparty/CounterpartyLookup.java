package com.aleruan.dealos.counterparty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the counterparty module for other modules (deals, subcontracting, invoicing). */
public interface CounterpartyLookup {

    Optional<CounterpartySummary> find(UUID id);

    /** Counterparties with the given role, optionally filtered by work type and country. */
    List<CounterpartySummary> withRole(String role, String workType, String country);
}
