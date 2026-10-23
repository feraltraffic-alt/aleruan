package com.aleruan.dealos.quote.domain;

import java.util.EnumMap;
import java.util.Map;

/** Printed labels and VAT notes in the quote languages. Notes are copied into the document at issue time. */
public final class QuoteTexts {

    public enum Key {
        TITLE, NUMBER, ISSUED, VALID_UNTIL, SUPPLIER, CUSTOMER, COMPANY_ID, VAT_ID, SUBJECT, SITE, POS, DESCRIPTION,
        QUANTITY, UNIT, UNIT_PRICE, LINE_TOTAL, SUBTOTAL, VAT, NO_VAT, TOTAL, BANK, TERMS, ISSUED_BY, PRICES_IN, PAGE,
        CO_TITLE, CO_REASON, CO_DAYS, CO_DAYS_VALUE, CO_APPROVAL, SIGN_NAME, SIGN_DATE, SIGN_SIGNATURE, CO_NO_CHANGE
    }

    private static final Map<QuoteLanguage, Map<Key, String>> LABELS = new EnumMap<>(QuoteLanguage.class);

    static {
        put(QuoteLanguage.CS, "Cenová nabídka", "Číslo", "Datum", "Platnost do", "Dodavatel", "Zákazník", "IČO", "DIČ",
                "Předmět", "Místo realizace", "#", "Popis", "Množství", "MJ", "Cena za MJ", "Celkem", "Celkem bez DPH",
                "DPH {rate} %", "DPH", "Cena celkem", "Bankovní spojení", "Podmínky", "Vystavil",
                "Ceny jsou uvedeny v {cur}.", "Strana",
                "Změnový list", "Důvod změny", "Vliv na termín", "{n} kalendářních dní", "Odsouhlasení objednatelem",
                "Jméno a funkce", "Datum", "Podpis", "beze změny");
        put(QuoteLanguage.EN, "Commercial offer", "Number", "Date", "Valid until", "Supplier", "Customer", "Reg. No.",
                "VAT ID", "Subject", "Site", "#", "Description", "Qty", "Unit", "Unit price", "Total", "Total excl. VAT",
                "VAT {rate} %", "VAT", "Total price", "Bank details", "Terms", "Issued by",
                "Prices are in {cur}.", "Page",
                "Change order", "Reason for the change", "Schedule impact", "{n} calendar days", "Customer approval",
                "Name and position", "Date", "Signature", "no change");
        put(QuoteLanguage.PL, "Oferta handlowa", "Numer", "Data", "Ważna do", "Dostawca", "Klient", "Nr rejestrowy",
                "NIP UE", "Przedmiot", "Miejsce realizacji", "Lp.", "Opis", "Ilość", "J.m.", "Cena jedn.", "Wartość",
                "Razem netto", "VAT {rate} %", "VAT", "Cena łączna", "Dane do przelewu", "Warunki", "Wystawił",
                "Ceny podano w {cur}.", "Strona",
                "Zlecenie zmiany", "Powód zmiany", "Wpływ na termin", "{n} dni kalendarzowych", "Akceptacja zamawiającego",
                "Imię, nazwisko i stanowisko", "Data", "Podpis", "bez zmian");
        put(QuoteLanguage.DE, "Angebot", "Nummer", "Datum", "Gültig bis", "Anbieter", "Kunde", "Reg.-Nr.", "USt-IdNr.",
                "Gegenstand", "Ausführungsort", "Pos.", "Beschreibung", "Menge", "Einheit", "Einzelpreis", "Gesamt",
                "Summe netto", "USt. {rate} %", "USt.", "Gesamtpreis", "Bankverbindung", "Bedingungen", "Erstellt von",
                "Preise in {cur}.", "Seite",
                "Nachtragsangebot", "Grund der Änderung", "Auswirkung auf den Termin", "{n} Kalendertage",
                "Freigabe durch den Auftraggeber", "Name und Funktion", "Datum", "Unterschrift", "keine");
    }

    private static void put(QuoteLanguage lang, String... values) {
        Map<Key, String> m = new EnumMap<>(Key.class);
        Key[] keys = Key.values();
        if (values.length != keys.length) {
            throw new IllegalStateException("Quote labels for " + lang + ": expected " + keys.length + ", got " + values.length);
        }
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], values[i]);
        }
        LABELS.put(lang, m);
    }

    private QuoteTexts() {
    }

    public static String label(QuoteLanguage lang, Key key) {
        return LABELS.get(lang).get(key);
    }

    /** Identifier names follow the company's country: a Czech IČO stays IČO in every language, a Polish one is NIP. */
    public static String companyIdLabel(QuoteLanguage lang, String country) {
        if ("CZ".equalsIgnoreCase(country) || "SK".equalsIgnoreCase(country)) {
            return "IČO";
        }
        if ("PL".equalsIgnoreCase(country)) {
            return "NIP";
        }
        return label(lang, Key.COMPANY_ID);
    }

    /** The legal note printed under the totals, or null when Czech VAT is charged normally. */
    public static String vatNote(QuoteLanguage lang, VatMode mode, VatAdvisor.Reason reason) {
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
