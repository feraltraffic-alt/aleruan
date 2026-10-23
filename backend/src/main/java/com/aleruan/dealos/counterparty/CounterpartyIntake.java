package com.aleruan.dealos.counterparty;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API for bringing counterparties in from documents (import): find an existing one by its
 * identifiers, create a new one and check it in the registries, check a bank account before paying.
 */
public interface CounterpartyIntake {

    /** Role is CUSTOMER, SUBCONTRACTOR, SUPPLIER or CARRIER. */
    record NewParty(String legalName, String country, String companyId, String vatNumber, String address, String role) {
    }

    /** By company ID within the country, then by VAT number. */
    Optional<CounterpartySummary> match(String country, String companyId, String vatNumber);

    /** Creates the counterparty (joins the caller's transaction); run {@link #runChecks} after it commits. */
    CounterpartySummary create(NewParty party);

    /**
     * Checks the counterparty in the registries. Call outside a transaction: registries may be slow or down,
     * and a failed check leaves the counterparty unchecked (it then shows up as a risk) instead of failing.
     */
    void runChecks(UUID id);

    /** Gives an existing counterparty one more role (a customer who also supplies, say). */
    void ensureRole(UUID id, String role);

    /**
     * Checks the account in the VAT registry; returns GREEN, YELLOW or RED, or null when it could not be
     * checked. Call outside a transaction, for the same reason as {@link #runChecks}.
     */
    String checkAccount(UUID id, String account);
}
