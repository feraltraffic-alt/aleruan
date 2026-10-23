package com.aleruan.dealos.platform.rates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CnbRatesParserTest {

    static final String SAMPLE = """
            23.10.2026 #206
            země|měna|množství|kód|kurz
            Austrálie|dolar|1|AUD|15,123
            EMU|euro|1|EUR|24,320
            Maďarsko|forint|100|HUF|6,214
            Polsko|zlotý|1|PLN|5,612
            """;

    @Test
    void readsRatePerUnitAndTheAnnouncementDate() {
        ExchangeRates.Rate eur = CnbRatesParser.find(SAMPLE, "eur").orElseThrow();
        assertEquals(new BigDecimal("24.32"), eur.czkPerUnit());
        assertEquals(LocalDate.of(2026, 10, 23), eur.validFor());
        assertEquals(new BigDecimal("0.06214"), CnbRatesParser.find(SAMPLE, "HUF").orElseThrow().czkPerUnit());
        assertEquals(new BigDecimal("12160.00"), eur.toCzk(new BigDecimal("500")));
        assertTrue(CnbRatesParser.find(SAMPLE, "XYZ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> CnbRatesParser.find("<html>error</html>", "EUR"));
    }
}
