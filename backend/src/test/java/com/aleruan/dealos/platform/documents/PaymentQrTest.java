package com.aleruan.dealos.platform.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentQrTest {

    @Test
    void spaydForCzechBanks() {
        assertEquals("SPD*1.0*ACC:CZ6508000000192000145399*AM:12100.00*CC:CZK*X-VS:20260001*MSG:FAKTURA FV-2026-0001 STAVBA",
                PaymentQr.spayd("CZ65 0800 0000 1920 0014 5399", new BigDecimal("12100"), "czk", "2026-0001",
                        "Faktura FV-2026-0001 *stavba*"));
        assertEquals("SPD*1.0*ACC:CZ6508000000192000145399*AM:1.50*CC:CZK",
                PaymentQr.spayd("CZ6508000000192000145399", new BigDecimal("1.5"), "CZK", null, " "));
        String long_ = PaymentQr.spayd("CZ6508000000192000145399", BigDecimal.ONE, "CZK", null, "Ř".repeat(100));
        assertEquals(60, long_.substring(long_.indexOf("MSG:") + 4).length());
    }

    @Test
    void epcForSepaTransfers() {
        assertEquals("BCD\n002\n1\nSCT\nBPKOPLPW\nAleruan-industry s.r.o.\nPL23102010130000020204812139\nEUR16717.36\n\n\nFV-2026-0001",
                PaymentQr.epc("BPKOPLPW", "Aleruan-industry s.r.o.", "PL23 1020 1013 0000 0202 0481 2139",
                        new BigDecimal("16717.36"), "FV-2026-0001"));
    }
}
