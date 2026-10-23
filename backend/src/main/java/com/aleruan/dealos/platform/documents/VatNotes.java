package com.aleruan.dealos.platform.documents;

/** VAT notes printed on customer documents (quotes, invoices) when Czech VAT is not charged normally. */
public final class VatNotes {

    private VatNotes() {
    }

    /** The legal note printed under the totals, or null when Czech VAT is charged normally. */
    public static String note(DocLanguage lang, VatMode mode, VatAdvisor.Reason reason) {
        boolean siteAbroad = reason == VatAdvisor.Reason.SITE_IN_OTHER_EU_STATE;
        return switch (mode) {
            case STANDARD -> null;
            case REVERSE_CHARGE_CZ -> switch (lang) {
                case CS -> "Daň odvede zákazník — přenesení daňové povinnosti podle § 92e zákona o DPH.";
                case EN -> "VAT to be accounted for by the customer — domestic reverse charge under Section 92e of the Czech VAT Act.";
                case PL -> "Podatek rozlicza klient — odwrotne obciążenie zgodnie z § 92e czeskiej ustawy o VAT.";
                case DE -> "Die Steuer schuldet der Kunde — Umkehr der Steuerschuldnerschaft nach § 92e des tschechischen MwSt-Gesetzes.";
            };
            case REVERSE_CHARGE_EU -> switch (lang) {
                case CS -> "Daň odvede zákazník (reverse charge), čl. " + (siteAbroad ? "194" : "196") + " směrnice 2006/112/ES.";
                case EN -> "Reverse charge: VAT to be accounted for by the customer, Art. " + (siteAbroad ? "194" : "196") + " of Directive 2006/112/EC.";
                case PL -> "Odwrotne obciążenie: podatek rozlicza nabywca, art. " + (siteAbroad ? "194" : "196") + " dyrektywy 2006/112/WE.";
                case DE -> "Steuerschuldnerschaft des Leistungsempfängers (Reverse Charge), Art. " + (siteAbroad ? "194" : "196") + " der Richtlinie 2006/112/EG.";
            };
            case OUTSIDE_SCOPE -> switch (lang) {
                case CS -> "Plnění nepodléhá DPH v ČR — místo plnění je mimo Evropskou unii.";
                case EN -> "Not subject to Czech VAT — the place of supply is outside the European Union.";
                case PL -> "Usługa nie podlega czeskiemu VAT — miejsce świadczenia poza Unią Europejską.";
                case DE -> "Nicht der tschechischen MwSt. unterliegend — Leistungsort außerhalb der Europäischen Union.";
            };
        };
    }
}
