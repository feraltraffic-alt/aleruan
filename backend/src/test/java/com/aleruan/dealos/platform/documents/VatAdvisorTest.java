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
        expect(VatMode.OUTSIDE_SCOPE, Reason.CUSTOMER_OUTSIDE_EU, VatAdvisor.suggest("ch", "CHE-1", null, true));
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
    void aCzechSiteMeansCzechVatWhoeverTheCustomerIs() {
        // a Ukrainian or Swiss customer building in Prague pays Czech VAT
        expect(VatMode.STANDARD, Reason.CZ_SITE, VatAdvisor.suggest("UA", null, "CZ", true));
        expect(VatMode.STANDARD, Reason.CZ_SITE, VatAdvisor.suggest("CH", "CHE-123.456.789", "cz", true));
        // a German company without a Czech VAT number: Czech VAT, not EU reverse charge
        expect(VatMode.STANDARD, Reason.CZ_SITE, VatAdvisor.suggest("DE", "DE811234567", "CZ", true));
        // registered for Czech VAT: domestic reverse charge applies
        expect(VatMode.REVERSE_CHARGE_CZ, Reason.CZ_CONSTRUCTION_FOR_VAT_PAYER, VatAdvisor.suggest("DE", "CZ684123456", "CZ", true));
        // a non-EU customer with a site inside the EU: taxed in that state
        expect(VatMode.REVERSE_CHARGE_EU, Reason.SITE_IN_OTHER_EU_STATE, VatAdvisor.suggest("UA", "UA123", "PL", true));
        expect(VatMode.STANDARD, Reason.CHECK_WITH_ACCOUNTANT, VatAdvisor.suggest("UA", null, "PL", true));
        expect(VatMode.OUTSIDE_SCOPE, Reason.SITE_OUTSIDE_EU, VatAdvisor.suggest("CZ", "CZ68599528", "MD", true));
        // not construction: the customer decides, the site does not matter
        expect(VatMode.REVERSE_CHARGE_EU, Reason.EU_BUSINESS_CUSTOMER, VatAdvisor.suggest("DE", "DE811234567", "CZ", false));
        // a foreign VAT number alone is not a Czech registration
        expect(VatMode.STANDARD, Reason.DOMESTIC_STANDARD, VatAdvisor.suggest("CZ", "SK2020123456", null, true));
    }

    @Test
    void domesticDefaultIsStandardRate() {
        VatAdvisor.Suggestion s = VatAdvisor.suggest("CZ", "CZ68599528", null, false);
        expect(VatMode.STANDARD, Reason.DOMESTIC_STANDARD, s);
        assertEquals(new BigDecimal("21"), s.rate());
        expect(VatMode.STANDARD, Reason.DOMESTIC_STANDARD, VatAdvisor.suggest("CZ", null, "CZ", true));
    }
}
