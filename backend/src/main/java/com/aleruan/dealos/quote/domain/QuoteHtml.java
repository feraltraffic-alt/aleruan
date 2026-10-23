package com.aleruan.dealos.quote.domain;

import com.aleruan.dealos.quote.domain.QuoteTexts.Key;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;

/**
 * Renders a quote as strict XHTML with CSS 2.1 only, so the same markup works in the PDF renderer
 * and in a browser preview.
 */
public final class QuoteHtml {

    private QuoteHtml() {
    }

    /**
     * @param logoSrc  image URL or data URI for the supplier logo, or null for none
     * @param fontFamily font family registered with the renderer (falls back to sans-serif)
     */
    public static String render(QuoteDocument d, String logoSrc, String fontFamily) {
        QuoteLanguage lang = d.language();
        String title = l(lang, d.isChangeOrder() ? Key.CO_TITLE : Key.TITLE);
        StringBuilder h = new StringBuilder(8_000);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"").append(lang.name().toLowerCase()).append("\">")
                .append("<head><meta charset=\"UTF-8\"/><title>").append(esc(title + " " + d.displayNumber()))
                .append("</title><style>").append(css(d, fontFamily)).append("</style></head><body>");

        // header: logo, title, number and dates
        h.append("<table class=\"head\"><tr><td class=\"logo\">");
        if (logoSrc != null) {
            h.append("<img src=\"").append(esc(logoSrc)).append("\" alt=\"").append(esc(d.supplier().name())).append("\"/>");
        } else {
            h.append("<div class=\"wordmark\">").append(esc(d.supplier().name())).append("</div>");
        }
        h.append("</td><td class=\"doc\"><div class=\"title\">").append(esc(title)).append("</div>")
                .append("<div class=\"no\">").append(esc(d.displayNumber())).append("</div>")
                .append("<table class=\"dates\">")
                .append(row(l(lang, Key.ISSUED), date(d, d.issuedOn())))
                .append(row(l(lang, Key.VALID_UNTIL), date(d, d.validUntil())))
                .append("</table></td></tr></table>");
        h.append("<div class=\"rule\"></div>");

        // parties
        h.append("<table class=\"parties\"><tr>")
                .append("<td>").append(party(lang, l(lang, Key.SUPPLIER), d.supplier())).append("</td>")
                .append("<td>").append(party(lang, l(lang, Key.CUSTOMER), d.customer())).append("</td>")
                .append("</tr></table>");

        // subject
        h.append("<table class=\"subject\"><tr><td class=\"k\">").append(esc(l(lang, Key.SUBJECT))).append("</td><td class=\"v\">")
                .append(esc(d.subject())).append("</td></tr>");
        if (d.siteCountry() != null) {
            h.append("<tr><td class=\"k\">").append(esc(l(lang, Key.SITE))).append("</td><td>").append(esc(d.siteCountry()))
                    .append("</td></tr>");
        }
        if (d.isChangeOrder()) {
            if (d.changeOrder().reason() != null) {
                h.append("<tr><td class=\"k\">").append(esc(l(lang, Key.CO_REASON))).append("</td><td>")
                        .append(esc(d.changeOrder().reason()).replace("\n", "<br/>")).append("</td></tr>");
            }
            int days = d.changeOrder().extraDays();
            h.append("<tr><td class=\"k\">").append(esc(l(lang, Key.CO_DAYS))).append("</td><td>")
                    .append(esc(days == 0 ? l(lang, Key.CO_NO_CHANGE)
                            : (days > 0 ? "+" : "") + l(lang, Key.CO_DAYS_VALUE).replace("{n}", String.valueOf(days))))
                    .append("</td></tr>");
        }
        h.append("</table>");

        // lines
        h.append("<table class=\"lines\"><thead><tr>")
                .append("<th class=\"pos\">").append(esc(l(lang, Key.POS))).append("</th>")
                .append("<th>").append(esc(l(lang, Key.DESCRIPTION))).append("</th>")
                .append("<th class=\"n\">").append(esc(l(lang, Key.QUANTITY))).append("</th>")
                .append("<th class=\"u\">").append(esc(l(lang, Key.UNIT))).append("</th>")
                .append("<th class=\"n\">").append(esc(l(lang, Key.UNIT_PRICE))).append("</th>")
                .append("<th class=\"n\">").append(esc(l(lang, Key.LINE_TOTAL))).append("</th>")
                .append("</tr></thead><tbody>");
        int i = 1;
        for (QuoteDocument.Line line : d.lines()) {
            h.append("<tr><td class=\"pos\">").append(i++).append("</td><td>").append(esc(line.description())).append("</td>")
                    .append("<td class=\"n\">").append(qty(lang, line.quantity())).append("</td>")
                    .append("<td class=\"u\">").append(esc(line.unit() == null ? "" : line.unit())).append("</td>")
                    .append("<td class=\"n\">").append(money(lang, line.unitPrice())).append("</td>")
                    .append("<td class=\"n\">").append(money(lang, line.total())).append("</td></tr>");
        }
        h.append("</tbody></table>");

        // totals
        h.append("<table class=\"totals\">")
                .append(row(l(lang, Key.SUBTOTAL), money(lang, d.subtotal()) + " " + d.currency()));
        String vatLabel = d.vatMode().chargesVat()
                ? l(lang, Key.VAT).replace("{rate}", qty(lang, d.vatRate()))
                : l(lang, Key.NO_VAT);
        h.append(row(vatLabel, d.vatMode().chargesVat() ? money(lang, d.vatAmount()) + " " + d.currency() : "—"))
                .append("<tr class=\"grand\"><td>").append(esc(l(lang, Key.TOTAL))).append("</td><td class=\"n\">")
                .append(money(lang, d.total())).append(" ").append(esc(d.currency())).append("</td></tr></table>");
        if (d.vatNote() != null) {
            h.append("<p class=\"vatnote\">").append(esc(d.vatNote())).append("</p>");
        }
        h.append("<p class=\"small\">").append(esc(l(lang, Key.PRICES_IN).replace("{cur}", d.currency()))).append("</p>");

        if (d.terms() != null && !d.terms().isBlank()) {
            h.append("<h2>").append(esc(l(lang, Key.TERMS))).append("</h2><p class=\"terms\">")
                    .append(esc(d.terms().strip()).replace("\n", "<br/>")).append("</p>");
        }

        h.append("<table class=\"foot\"><tr><td>");
        if (d.bank() != null && d.bank().iban() != null) {
            h.append("<h2>").append(esc(l(lang, Key.BANK))).append("</h2><p>")
                    .append(d.bank().bankName() == null ? "" : esc(d.bank().bankName()) + "<br/>")
                    .append("IBAN ").append(esc(grouped(d.bank().iban())))
                    .append(d.bank().swift() == null ? "" : "<br/>SWIFT " + esc(d.bank().swift()))
                    .append("</p>");
        }
        h.append("</td><td class=\"sign\">");
        if (d.issuedBy() != null) {
            h.append("<h2>").append(esc(l(lang, Key.ISSUED_BY))).append("</h2><p>").append(esc(d.issuedBy())).append("</p>");
        }
        h.append("</td></tr></table>");
        if (d.isChangeOrder()) {
            h.append("<div class=\"approval\"><h2>").append(esc(l(lang, Key.CO_APPROVAL))).append("</h2><table class=\"signs\"><tr>")
                    .append("<td>").append(esc(l(lang, Key.SIGN_NAME))).append("</td>")
                    .append("<td>").append(esc(l(lang, Key.SIGN_DATE))).append("</td>")
                    .append("<td>").append(esc(l(lang, Key.SIGN_SIGNATURE))).append("</td>")
                    .append("</tr></table></div>");
        }
        return h.append("</body></html>").toString();
    }

    private static String css(QuoteDocument d, String font) {
        String family = (font == null ? "" : "'" + font + "', ") + "sans-serif";
        String footer = cssText(d.supplier().name()) + (d.supplier().companyId() == null ? ""
                : "   " + cssText(QuoteTexts.companyIdLabel(d.language(), d.supplier().country()) + " " + d.supplier().companyId()));
        return """
            @page { size: A4; margin: 16mm 16mm 20mm 16mm;
              @bottom-left { content: "%s"; font-family: %s; font-size: 7.5pt; color: #645e6e; }
              @bottom-right { content: "%s " counter(page) " / " counter(pages); font-family: %s; font-size: 7.5pt; color: #645e6e; } }
            body { font-family: %s; font-size: 9.5pt; line-height: 1.4; color: #1e1b24; margin: 0; }
            table { border-collapse: collapse; width: 100%%; }
            td, th { vertical-align: top; padding: 0; }
            .head td.logo { width: 50%%; }
            .head img { width: 58mm; }
            .wordmark { font-size: 18pt; font-weight: 600; color: #2a1145; }
            .head td.doc { text-align: right; }
            .title { font-size: 19pt; font-weight: 600; color: #2a1145; line-height: 1.15; }
            .no { font-size: 11pt; font-weight: 600; margin: 1mm 0 2mm 0; }
            .dates { width: auto; margin-left: auto; }
            .dates td { padding: 0 0 0 5mm; text-align: right; }
            .dates td.k { color: #645e6e; }
            .rule { border-top: 1.4pt solid #d9a21b; margin: 6mm 0 6mm 0; }
            .parties td { width: 50%%; padding-right: 8mm; }
            .label { color: #645e6e; font-size: 8pt; margin-bottom: 1mm; }
            .pname { font-size: 11pt; font-weight: 600; margin-bottom: 1mm; }
            .subject { margin: 7mm 0 5mm 0; }
            .subject td.k { width: 32mm; color: #645e6e; }
            .subject td.v { font-weight: 600; }
            .lines th { font-weight: 600; font-size: 8.5pt; color: #2a1145; text-align: left; padding: 2mm 2mm 2mm 0;
              border-bottom: 1pt solid #2a1145; }
            .lines td { padding: 2.2mm 2mm 2.2mm 0; border-bottom: 0.5pt solid #e3e0e8; }
            .lines tr { page-break-inside: avoid; }
            .lines .pos { width: 8mm; color: #645e6e; }
            .n { text-align: right; white-space: nowrap; }
            .lines th.n, .lines td.n { padding-right: 0; padding-left: 3mm; }
            .lines th.u, .lines td.u { padding-left: 1.5mm; }
            .totals { width: 82mm; margin: 4mm 0 0 auto; }
            .totals td { padding: 1.2mm 0; }
            .totals td.v, .totals td.n { text-align: right; white-space: nowrap; }
            .totals tr.grand td { border-top: 1pt solid #2a1145; padding-top: 2mm; font-size: 12pt; font-weight: 600; color: #2a1145; }
            .vatnote { margin: 4mm 0 0 0; padding: 2.5mm 3mm; background-color: #f6f2fb; }
            .small { color: #645e6e; font-size: 8pt; margin: 2mm 0 0 0; }
            h2 { font-size: 9.5pt; font-weight: 600; color: #2a1145; margin: 7mm 0 1.5mm 0; }
            .terms { margin: 0; }
            .foot { margin-top: 4mm; page-break-inside: avoid; }
            .foot td { width: 50%%; }
            .foot p { margin: 0; }
            .foot td.sign { padding-left: 8mm; }
            .approval { margin-top: 10mm; page-break-inside: avoid; }
            .signs td { width: 33%%; padding: 14mm 6mm 1.5mm 0; border-bottom: 0.6pt solid #1e1b24; color: #645e6e;
              font-size: 8pt; vertical-align: bottom; }
            .signs td + td { padding-left: 6mm; }
            """.formatted(footer, family, cssText(l(d.language(), Key.PAGE)), family, family);
    }

    private static String party(QuoteLanguage lang, String label, QuoteDocument.Party p) {
        StringBuilder s = new StringBuilder("<div class=\"label\">").append(esc(label)).append("</div>")
                .append("<div class=\"pname\">").append(esc(p.name())).append("</div>");
        if (p.address() != null) {
            s.append(esc(p.address())).append("<br/>");
        }
        if (p.companyId() != null) {
            s.append(esc(QuoteTexts.companyIdLabel(lang, p.country()))).append(" ").append(esc(p.companyId())).append("<br/>");
        }
        if (p.vatNumber() != null) {
            s.append(esc(l(lang, Key.VAT_ID))).append(" ").append(esc(p.vatNumber())).append("<br/>");
        }
        if (p.email() != null) {
            s.append(esc(p.email())).append("<br/>");
        }
        if (p.phone() != null) {
            s.append(esc(p.phone())).append("<br/>");
        }
        return s.toString();
    }

    private static String row(String k, String v) {
        return "<tr><td class=\"k\">" + esc(k) + "</td><td class=\"v\">" + esc(v) + "</td></tr>";
    }

    private static String l(QuoteLanguage lang, Key key) {
        return QuoteTexts.label(lang, key);
    }

    private static String date(QuoteDocument d, java.time.LocalDate day) {
        DateTimeFormatter f = d.language() == QuoteLanguage.EN
                ? DateTimeFormatter.ofPattern("d MMM yyyy", d.language().locale())
                : DateTimeFormatter.ofPattern("dd.MM.yyyy");
        return day.format(f);
    }

    static String money(QuoteLanguage lang, BigDecimal v) {
        return format(lang, v.setScale(2, RoundingMode.HALF_UP), "#,##0.00");
    }

    static String qty(QuoteLanguage lang, BigDecimal v) {
        return format(lang, v, "#,##0.####");
    }

    private static String format(QuoteLanguage lang, BigDecimal v, String pattern) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(lang.locale());
        if (lang == QuoteLanguage.CS || lang == QuoteLanguage.PL) {
            sym.setGroupingSeparator(' '); // narrow no-break space is missing in many fonts
        }
        return new DecimalFormat(pattern, sym).format(v);
    }

    /** Text for a CSS string inside an XHTML style element. */
    private static String cssText(String s) {
        return esc(s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "));
    }

    private static String grouped(String iban) {
        return iban.replaceAll("\\s", "").replaceAll("(.{4})(?!$)", "$1 ");
    }

    static String esc(String s) {
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
                        continue; // control characters are not allowed in XML
                    }
                    b.append(c);
                }
            }
        }
        return b.toString();
    }
}
