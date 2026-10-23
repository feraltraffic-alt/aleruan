package com.aleruan.dealos.platform.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aleruan.dealos.platform.documents.VatAdvisor.Reason;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VatAdvisorTest {

    private static void expect(VatMode mode, Reason reason, VatAdvisor.Suggestion s) {
        assertEquals(mode, s.mode());
        assertEquals(reason, s.reason());
    }

    @Test
    void customerOutsideEuIsOutsideScope() {
        expect(VatMode.OUTSIDE_SCOPE, Reason.CUSTOMER_OUTSIDE_EU, VatAdvisor.suggest("MD", null, null, false));
        expect(VatMode.OUTSIDE_SCOPE, Reason.CUSTOMER_OUTSIDE_EU, VatAdvisor.suggest("ch", "CHE-1", "CZ", true));
    }

    @Test
    void euBusinessCustomerGetsReverseCharge() {
        expect(VatMode.REVERSE_CHARGE_EU, Reason.EU_BUSINESS_CUSTOMER, VatAdvisor.suggest("DE", "DE811234567", null, false));
        expect(VatMode.STANDARD, Reason.CHECK_WITH_ACCOUNTANT, VatAdvisor.suggest("DE", null, null, false));
    }

    @Test
    void constructionFollowsTheSite() {
        expect(VatMode.REVERSE_CHARGE_EU, Reason.SITE_IN_OTHER_EU_STATE, VatAdvisor.suggest("PL", "PL2815901396", "pl", true));
        // a Czech customer with a site in Germany: VAT is due in Germany
        expect(VatMode.REVERSE_CHARGE_EU, Reason.SITE_IN_OTHER_EU_STATE, VatAdvisor.suggest("CZ", "CZ68599528", "DE", true));
        expect(VatMode.STANDARD, Reason.CHECK_WITH_ACCOUNTANT, VatAdvisor.suggest("CZ", null, "DE", true));
        expect(VatMode.REVERSE_CHARGE_CZ, Reason.CZ_CONSTRUCTION_FOR_VAT_PAYER, VatAdvisor.suggest("CZ", "CZ68599528", "CZ", true));
        expect(VatMode.REVERSE_CHARGE_CZ, Reason.CZ_CONSTRUCTION_FOR_VAT_PAYER, VatAdvisor.suggest("CZ", "CZ68599528", null, true));
    }

    @Test
    void domesticDefaultIsStandardRate() {
        VatAdvisor.Suggestion s = VatAdvisor.suggest("CZ", "CZ68599528", null, false);
        expect(VatMode.STANDARD, Reason.DOMESTIC_STANDARD, s);
        assertEquals(new BigDecimal("21"), s.rate());
        expect(VatMode.STANDARD, Reason.DOMESTIC_STANDARD, VatAdvisor.suggest("CZ", null, "CZ", true));
    }
}
