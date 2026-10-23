package com.aleruan.dealos.quote.domain;

import java.util.Locale;

/** Languages a quote can be issued in. */
public enum QuoteLanguage {
    CS(Locale.forLanguageTag("cs-CZ")),
    EN(Locale.UK),
    PL(Locale.forLanguageTag("pl-PL")),
    DE(Locale.GERMANY);

    private final Locale locale;

    QuoteLanguage(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    /** The customer's own language when we have it, English otherwise. */
    public static QuoteLanguage forCountry(String country) {
        if (country == null) {
            return EN;
        }
        return switch (country.toUpperCase(Locale.ROOT)) {
            case "CZ", "SK" -> CS;
            case "PL" -> PL;
            case "DE", "AT", "LI" -> DE;
            default -> EN;
        };
    }
}
