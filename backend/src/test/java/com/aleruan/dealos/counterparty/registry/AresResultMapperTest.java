package com.aleruan.dealos.counterparty.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AresResultMapperTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");

    @Test
    void mapsCompany() {
        Map<String, Object> json = Map.of(
                "ico", "14220997",
                "obchodniJmeno", "Aleruan-industry s.r.o.",
                "sidlo", Map.of("textovaAdresa", "Malešická 2855/2b, Žižkov, 130 00 Praha 3"),
                "dic", "CZ14220997",
                "datumVzniku", "2022-01-01",
                "seznamRegistraci", Map.of("stavZdrojeIr", "NEEXISTUJICI"));
        RegistrySnapshot s = AresResultMapper.map(json, "{raw}", AT);
        assertEquals("Aleruan-industry s.r.o.", s.legalName());
        assertEquals("Malešická 2855/2b, Žižkov, 130 00 Praha 3", s.address());
        assertEquals("CZ14220997", s.vatNumber());
        assertEquals(LocalDate.parse("2022-01-01"), s.registeredOn());
        assertEquals(Boolean.FALSE, s.insolvency());
        assertNull(s.ceasedOn());
    }

    @Test
    void activeInsolvencyRecord() {
        RegistrySnapshot s = AresResultMapper.map(Map.of("obchodniJmeno", "X",
                "seznamRegistraci", Map.of("stavZdrojeIr", "AKTIVNI")), "{}", AT);
        assertEquals(Boolean.TRUE, s.insolvency());
    }

    @Test
    void notFound() {
        RegistrySnapshot s = AresResultMapper.map(Map.of("kod", "NENALEZENO", "popis", "Subjekt nenalezen"),
                "{}", AT);
        assertEquals(Boolean.FALSE, s.found());
    }

    @Test
    void otherErrorMeansUnavailable() {
        RegistrySnapshot s = AresResultMapper.map(Map.of("kod", "CHYBA_VSTUPU", "popis", "x"), "{}", AT);
        assertFalse(s.available());
    }
}
