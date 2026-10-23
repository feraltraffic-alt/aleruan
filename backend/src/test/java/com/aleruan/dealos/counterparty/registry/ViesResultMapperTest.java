package com.aleruan.dealos.counterparty.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViesResultMapperTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");

    @Test
    void mapsValidNumberWithConsultationId() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("countryCode", "PL");
        json.put("vatNumber", "5223057790");
        json.put("valid", true);
        json.put("requestIdentifier", "WAPIAAAAZ1abc");
        json.put("name", "INTRANS SP. Z O.O.");
        json.put("address", "UL. POWSTAŃCÓW WARSZAWY 22/17\n05-500 PIASECZNO");
        RegistrySnapshot s = ViesResultMapper.map(json, "{raw}", AT);
        assertTrue(s.available());
        assertEquals(Boolean.TRUE, s.vatValid());
        assertEquals("WAPIAAAAZ1abc", s.requestId());
        assertEquals("PL5223057790", s.vatNumber());
        assertEquals("UL. POWSTAŃCÓW WARSZAWY 22/17, 05-500 PIASECZNO", s.address());
        assertEquals("{raw}", s.rawResponse());
    }

    @Test
    void hiddenNameBecomesNull() {
        Map<String, Object> json = Map.of("countryCode", "DE", "vatNumber", "123", "valid", true,
                "name", "---", "address", "---");
        RegistrySnapshot s = ViesResultMapper.map(json, "{}", AT);
        assertNull(s.legalName());
        assertNull(s.address());
    }

    @Test
    void invalidNumber() {
        RegistrySnapshot s = ViesResultMapper.map(Map.of("countryCode", "CZ", "vatNumber", "1", "valid", false),
                "{}", AT);
        assertEquals(Boolean.FALSE, s.vatValid());
        assertEquals(Boolean.FALSE, s.found());
    }

    @Test
    void memberStateUnavailable() {
        Map<String, Object> json = Map.of("actionSucceed", false,
                "errorWrappers", List.of(Map.of("error", "MS_UNAVAILABLE")));
        RegistrySnapshot s = ViesResultMapper.map(json, "{}", AT);
        assertFalse(s.available());
        assertEquals("MS_UNAVAILABLE", s.unavailableReason());
    }
}
