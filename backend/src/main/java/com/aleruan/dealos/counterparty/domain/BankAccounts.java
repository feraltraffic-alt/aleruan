package com.aleruan.dealos.counterparty.domain;

import java.util.Locale;

/**
 * Makes bank accounts comparable across the forms people and registries use:
 * Czech domestic "19-2000145399/0800", Czech IBAN "CZ65 0800 0000 1920 0014 5399",
 * Polish NRB "61 1090 1014 0000 0712 1981 2874" or IBAN "PL61…".
 */
public final class BankAccounts {

    private BankAccounts() {
    }

    /** Canonical form: Czech accounts as "prefix-number/bank" without leading zeros, others as compact IBAN/NRB. */
    public static String comparable(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s]", "");
        if (compact.matches("CZ\\d{22}")) {
            return czFromIban(compact);
        }
        if (compact.matches("(\\d{1,6}-)?\\d{2,10}/\\d{4}")) {
            return czDomestic(compact);
        }
        if (compact.matches("PL\\d{26}")) {
            return compact.substring(2);
        }
        return compact.replace("-", "");
    }

    /** Polish NRB (26 digits) as the white-list API expects it, or null if this is not a Polish account. */
    public static String polishNrb(String raw) {
        String c = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
        if (c.startsWith("PL")) {
            c = c.substring(2);
        }
        return c.matches("\\d{26}") ? c : null;
    }

    /** CZkk BBBB PPPPPP NNNNNNNNNN → prefix-number/bank. */
    private static String czFromIban(String iban) {
        String bank = iban.substring(4, 8);
        String prefix = stripZeros(iban.substring(8, 14));
        String number = stripZeros(iban.substring(14, 24));
        return (prefix.equals("0") ? "" : prefix + "-") + number + "/" + bank;
    }

    private static String czDomestic(String domestic) {
        String[] accountAndBank = domestic.split("/");
        String[] parts = accountAndBank[0].split("-");
        String prefix = parts.length == 2 ? stripZeros(parts[0]) : "0";
        String number = stripZeros(parts[parts.length - 1]);
        return (prefix.equals("0") ? "" : prefix + "-") + number + "/" + accountAndBank[1];
    }

    private static String stripZeros(String digits) {
        String s = digits.replaceFirst("^0+", "");
        return s.isEmpty() ? "0" : s;
    }
}
