package com.aleruan.dealos.platform.rates;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses the ČNB daily fixing text file:
 * <pre>
 * 23.10.2026 #206
 * země|měna|množství|kód|kurz
 * EMU|euro|1|EUR|24,320
 * Maďarsko|forint|100|HUF|6,214
 * </pre>
 */
public final class CnbRatesParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    private CnbRatesParser() {
    }

    public static Optional<ExchangeRates.Rate> find(String text, String currency) {
        String[] lines = text.strip().split("\\R");
        if (lines.length < 3) {
            throw new IllegalArgumentException("Unexpected ČNB rates format");
        }
        LocalDate validFor = LocalDate.parse(lines[0].trim().split("\\s+")[0], DATE);
        String code = currency.toUpperCase(Locale.ROOT);
        for (int i = 2; i < lines.length; i++) {
            String[] f = lines[i].split("\\|");
            if (f.length == 5 && f[3].trim().equals(code)) {
                BigDecimal amount = new BigDecimal(f[2].trim());
                BigDecimal rate = new BigDecimal(f[4].trim().replace(',', '.'));
                return Optional.of(new ExchangeRates.Rate(code, rate.divide(amount, MathContext.DECIMAL64).stripTrailingZeros(),
                        validFor, "ČNB"));
            }
        }
        return Optional.empty();
    }
}
