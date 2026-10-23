package com.aleruan.dealos.counterparty.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaxIdentifiersTest {

    @Test
    void validCzechIco() {
        assertTrue(TaxIdentifiers.isValidCzIco("14220997"));    // Aleruan-industry s.r.o.
        assertTrue(TaxIdentifiers.isValidCzIco("142 20 997"));
        assertFalse(TaxIdentifiers.isValidCzIco("14220998"));
        assertFalse(TaxIdentifiers.isValidCzIco("1422099"));
    }

    @Test
    void validPolishNip() {
        assertTrue(TaxIdentifiers.isValidPlNip("5223057790"));    // INTRANS Sp. z o.o. (CMR)
        assertTrue(TaxIdentifiers.isValidPlNip("897-180-69-51")); // AAA-Print (contract)
        assertTrue(TaxIdentifiers.isValidPlNip("5833500290"));    // TAWEBUD (invoice)
        assertFalse(TaxIdentifiers.isValidPlNip("5223057791"));
    }

    @Test
    void parsesVat() {
        TaxIdentifiers.VatId vat = TaxIdentifiers.parseVat("cz 142 20 997");
        assertEquals("CZ", vat.country());
        assertEquals("14220997", vat.number());
        assertEquals("EL", TaxIdentifiers.parseVat("GR123456789").country());
        assertThrows(IllegalArgumentException.class, () -> TaxIdentifiers.parseVat("14220997"));
    }

    @Test
    void viesCountries() {
        assertTrue(TaxIdentifiers.isViesCountry("CZ"));
        assertTrue(TaxIdentifiers.isViesCountry("GR"));
        assertFalse(TaxIdentifiers.isViesCountry("MD")); // Moldova: manual check
    }

    @Test
    void rejectsInvalidCompanyIdForKnownCountries() {
        assertThrows(IllegalArgumentException.class, () -> TaxIdentifiers.requireValidCompanyId("CZ", "12345678"));
        TaxIdentifiers.requireValidCompanyId("MD", "1023600005748"); // accepted as typed
    }
}
