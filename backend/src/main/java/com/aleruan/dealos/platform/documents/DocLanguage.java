package com.aleruan.dealos.platform.documents;

import java.util.Locale;

/** Languages customer documents are issued in. */
public enum DocLanguage {
    CS(Locale.forLanguageTag("cs-CZ")),
    EN(Locale.UK),
    PL(Locale.forLanguageTag("pl-PL")),
    DE(Locale.GERMANY);

    private final Locale locale;

    DocLanguage(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    /** The customer's own language when we have it, English otherwise. */
    public static DocLanguage forCountry(String country) {
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
