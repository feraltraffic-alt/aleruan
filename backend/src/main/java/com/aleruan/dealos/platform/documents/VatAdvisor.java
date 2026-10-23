package com.aleruan.dealos.platform.documents;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * Suggests the VAT treatment of a quote issued by a Czech VAT payer. It is a starting point for the
 * manager, not tax advice: the chosen mode is always shown and can be changed before the quote is issued.
 */
public final class VatAdvisor {

    public static final BigDecimal CZ_STANDARD_RATE = new BigDecimal("21");

    static final Set<String> EU = Set.of("AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
            "EL", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE");

    /** Why a mode was suggested; shown to the manager next to the suggestion. */
    public enum Reason {
        /** Customer outside the EU. */
        CUSTOMER_OUTSIDE_EU,
        /** Construction works on a site in another member state: VAT is due where the property is. */
        SITE_IN_OTHER_EU_STATE,
        /** Business customer with a VAT number in another member state. */
        EU_BUSINESS_CUSTOMER,
        /** Construction works in the Czech Republic for a Czech VAT payer. */
        CZ_CONSTRUCTION_FOR_VAT_PAYER,
        /** Construction on a Czech site for a customer without a Czech VAT number: Czech VAT, whoever the customer is. */
        CZ_SITE,
        /** Construction on a site outside the EU: not subject to Czech or EU VAT. */
        SITE_OUTSIDE_EU,
        /** Everything else: Czech VAT. */
        DOMESTIC_STANDARD,
        /** Foreign site or customer without a VAT number: Czech VAT shown, but this needs the accountant. */
        CHECK_WITH_ACCOUNTANT
    }

    public record Suggestion(VatMode mode, BigDecimal rate, Reason reason) {
    }

    private VatAdvisor() {
    }

    public static boolean inEu(String country) {
        return country != null && EU.contains(country.toUpperCase(Locale.ROOT));
    }

    public static Suggestion suggest(String customerCountry, String customerVatNumber, String siteCountry,
                                     boolean construction) {
        String customer = customerCountry == null ? null : customerCountry.toUpperCase(Locale.ROOT);
        String site = siteCountry == null || siteCountry.isBlank() ? null : siteCountry.toUpperCase(Locale.ROOT);
        boolean vatPayer = customerVatNumber != null && !customerVatNumber.isBlank();

        boolean czVatPayer = vatPayer && customerVatNumber.strip().toUpperCase(Locale.ROOT).startsWith("CZ");

        // Construction work is taxed where the property is (§ 10 of the Czech VAT Act, Art. 47 of the
        // VAT Directive), so the site decides before the customer's country does.
        if (construction && site != null) {
            if ("CZ".equals(site)) {
                return czVatPayer
                        ? new Suggestion(VatMode.REVERSE_CHARGE_CZ, BigDecimal.ZERO, Reason.CZ_CONSTRUCTION_FOR_VAT_PAYER)
                        : new Suggestion(VatMode.STANDARD, CZ_STANDARD_RATE,
                                "CZ".equals(customer) ? Reason.DOMESTIC_STANDARD : Reason.CZ_SITE);
            }
            if (!inEu(site)) {
                return new Suggestion(VatMode.OUTSIDE_SCOPE, BigDecimal.ZERO, Reason.SITE_OUTSIDE_EU);
            }
            return vatPayer
                    ? new Suggestion(VatMode.REVERSE_CHARGE_EU, BigDecimal.ZERO, Reason.SITE_IN_OTHER_EU_STATE)
                    : new Suggestion(VatMode.STANDARD, CZ_STANDARD_RATE, Reason.CHECK_WITH_ACCOUNTANT);
        }
        if (customer != null && !inEu(customer)) {
            return new Suggestion(VatMode.OUTSIDE_SCOPE, BigDecimal.ZERO, Reason.CUSTOMER_OUTSIDE_EU);
        }
        if (customer != null && !"CZ".equals(customer)) {
            return vatPayer
                    ? new Suggestion(VatMode.REVERSE_CHARGE_EU, BigDecimal.ZERO, Reason.EU_BUSINESS_CUSTOMER)
                    : new Suggestion(VatMode.STANDARD, CZ_STANDARD_RATE, Reason.CHECK_WITH_ACCOUNTANT);
        }
        if (construction && czVatPayer) {
            return new Suggestion(VatMode.REVERSE_CHARGE_CZ, BigDecimal.ZERO, Reason.CZ_CONSTRUCTION_FOR_VAT_PAYER);
        }
        return new Suggestion(VatMode.STANDARD, CZ_STANDARD_RATE, Reason.DOMESTIC_STANDARD);
    }
}
