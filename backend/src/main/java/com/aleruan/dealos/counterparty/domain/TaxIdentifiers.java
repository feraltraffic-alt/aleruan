package com.aleruan.dealos.counterparty.domain;

import java.util.Locale;
import java.util.Set;

/** Normalisation and checksum validation of company and VAT identifiers. */
public final class TaxIdentifiers {

    /** EU member states as used by VIES (Greece is EL). */
    public static final Set<String> VIES_COUNTRIES = Set.of(
            "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "EL", "ES", "FI", "FR", "HR", "HU", "IE",
            "IT", "LT", "LU", "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK", "XI");

    private static final int[] NIP_WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};

    private TaxIdentifiers() {
    }

    /** VAT number split into VIES country code and the national part. */
    public record VatId(String country, String number) {
        public String full() {
            return country + number;
        }
    }

    /** Uppercase, strips spaces, dots, dashes and slashes. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-/]", "");
    }

    /** Bank account / IBAN in comparable form. */
    public static String normalizeAccount(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
    }

    /** "CZ 142 20 997" → (CZ, 14220997). Greece "GR" is mapped to VIES "EL". */
    public static VatId parseVat(String raw) {
        String v = normalize(raw);
        if (v == null || v.length() < 4 || !Character.isLetter(v.charAt(0)) || !Character.isLetter(v.charAt(1))) {
            throw new IllegalArgumentException("VAT number must start with a 2-letter country code: " + raw);
        }
        String country = v.substring(0, 2);
        if ("GR".equals(country)) {
            country = "EL";
        }
        return new VatId(country, v.substring(2));
    }

    public static boolean isViesCountry(String country) {
        return country != null && VIES_COUNTRIES.contains(toViesCountry(country));
    }

    public static String toViesCountry(String isoCountry) {
        return "GR".equalsIgnoreCase(isoCountry) ? "EL" : isoCountry.toUpperCase(Locale.ROOT);
    }

    /** Czech IČO: 8 digits, mod-11 check digit. */
    public static boolean isValidCzIco(String raw) {
        String ico = normalize(raw);
        if (ico == null || !ico.matches("\\d{8}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            sum += (ico.charAt(i) - '0') * (8 - i);
        }
        int remainder = sum % 11;
        int check = remainder == 0 ? 1 : remainder == 1 ? 0 : 11 - remainder;
        return check == ico.charAt(7) - '0';
    }

    /** Polish NIP: 10 digits, weighted mod-11 check digit (10 is never valid). */
    public static boolean isValidPlNip(String raw) {
        String nip = normalize(raw);
        if (nip == null || !nip.matches("\\d{10}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (nip.charAt(i) - '0') * NIP_WEIGHTS[i];
        }
        int check = sum % 11;
        return check != 10 && check == nip.charAt(9) - '0';
    }

    /**
     * Validates the company identifier when the country has a known checksum.
     * Other countries are accepted as typed (manual check mode).
     */
    public static void requireValidCompanyId(String country, String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return;
        }
        if ("CZ".equals(country) && !isValidCzIco(companyId)) {
            throw new IllegalArgumentException("Invalid Czech IČO: " + companyId);
        }
        if ("PL".equals(country) && !isValidPlNip(companyId)) {
            throw new IllegalArgumentException("Invalid Polish NIP: " + companyId);
        }
    }
}
