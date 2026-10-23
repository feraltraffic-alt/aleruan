package com.aleruan.dealos.platform.documents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Escaping and number/date formatting for XHTML documents rendered to PDF. */
public final class DocFormat {

    private DocFormat() {
    }

    public static String money(DocLanguage lang, BigDecimal v) {
        return format(lang, v.setScale(2, RoundingMode.HALF_UP), "#,##0.00");
    }

    public static String qty(DocLanguage lang, BigDecimal v) {
        return format(lang, v, "#,##0.####");
    }

    public static String date(DocLanguage lang, LocalDate day) {
        if (day == null) {
            return "";
        }
        DateTimeFormatter f = lang == DocLanguage.EN
                ? DateTimeFormatter.ofPattern("d MMM yyyy", lang.locale())
                : DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return day.format(f);
    }

    private static String format(DocLanguage lang, BigDecimal v, String pattern) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(lang.locale());
        if (lang == DocLanguage.CS || lang == DocLanguage.PL) {
            sym.setGroupingSeparator(' '); // narrow no-break space is missing in many fonts
        }
        return new DecimalFormat(pattern, sym).format(v);
    }

    /** Text for an XHTML element or attribute; drops control characters XML does not allow. */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> {
                    if (c < 0x20 && c != '\n' && c != '\t') {
                        continue;
                    }
                    b.append(c);
                }
            }
        }
        return b.toString();
    }

    /** Multi-line text with line breaks kept. */
    public static String escLines(String s) {
        return esc(s == null ? null : s.strip()).replace("\n", "<br/>");
    }

    /** Text for a CSS string inside an XHTML style element (page footers). */
    public static String cssText(String s) {
        return esc(s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "));
    }

    /** IBAN in groups of four. */
    public static String iban(String iban) {
        return iban.replaceAll("\\s", "").replaceAll("(.{4})(?!$)", "$1 ");
    }
}
