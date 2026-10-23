package com.aleruan.dealos.platform.documents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Payment QR texts read by banking apps: SPAYD ("QR Platba", Czech banks, CZK) and the EPC
 * SEPA credit transfer code (EUR, most EU banking apps).
 */
public final class PaymentQr {

    private PaymentQr() {
    }

    /** Czech "QR Platba" (SPAYD 1.0). */
    public static String spayd(String iban, BigDecimal amount, String currency, String variableSymbol, String message) {
        StringBuilder s = new StringBuilder("SPD*1.0*ACC:").append(compact(iban))
                .append("*AM:").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append("*CC:").append(currency.toUpperCase(Locale.ROOT));
        if (variableSymbol != null && !variableSymbol.isBlank()) {
            s.append("*X-VS:").append(variableSymbol.replaceAll("\\D", ""));
        }
        if (message != null && !message.isBlank()) {
            // SPAYD values may not contain '*'; keep the message short and plain
            String m = ascii(message).replace("*", "").toUpperCase(Locale.ROOT);
            s.append("*MSG:").append(m, 0, Math.min(60, m.length()));
        }
        return s.toString();
    }

    /** EPC069-12 SEPA credit transfer, version 002, UTF-8. */
    public static String epc(String bic, String beneficiary, String iban, BigDecimal amount, String reference) {
        String name = beneficiary.length() > 70 ? beneficiary.substring(0, 70) : beneficiary;
        String text = reference == null ? "" : (reference.length() > 140 ? reference.substring(0, 140) : reference);
        return String.join("\n", "BCD", "002", "1", "SCT", bic == null ? "" : bic.strip(), name, compact(iban),
                "EUR" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString(), "", "", text);
    }

    private static String compact(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private static String ascii(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", "");
    }
}
