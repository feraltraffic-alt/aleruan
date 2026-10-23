package com.aleruan.dealos.invoice.domain;

import static com.aleruan.dealos.platform.documents.DocFormat.cssText;
import static com.aleruan.dealos.platform.documents.DocFormat.date;
import static com.aleruan.dealos.platform.documents.DocFormat.esc;
import static com.aleruan.dealos.platform.documents.DocFormat.escLines;
import static com.aleruan.dealos.platform.documents.DocFormat.iban;
import static com.aleruan.dealos.platform.documents.DocFormat.money;
import static com.aleruan.dealos.platform.documents.DocFormat.qty;

import com.aleruan.dealos.invoice.domain.InvoiceTexts.Key;
import com.aleruan.dealos.platform.documents.DocLanguage;

/** Invoice as strict XHTML with CSS 2.1, for the PDF renderer and browser previews. */
public final class InvoiceHtml {

    private InvoiceHtml() {
    }

    public static String render(InvoiceDocument d, String logoSrc, String qrSrc, String fontFamily) {
        DocLanguage lang = d.language();
        boolean credit = d.kind() == InvoiceKind.CREDIT_NOTE;
        String title = InvoiceTexts.both(lang, credit ? Key.CREDIT_TITLE : Key.TITLE);
        StringBuilder h = new StringBuilder(10_000);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"cs\">")
                .append("<head><meta charset=\"UTF-8\"/><title>").append(esc(title + " " + d.number())).append("</title><style>")
                .append(css(d, fontFamily)).append("</style></head><body>");

        h.append("<table class=\"head\"><tr><td class=\"logo\">");
        h.append(logoSrc == null ? "<div class=\"wordmark\">" + esc(d.supplier().name()) + "</div>"
                : "<img src=\"" + esc(logoSrc) + "\" alt=\"" + esc(d.supplier().name()) + "\"/>");
        h.append("</td><td class=\"doc\"><div class=\"title\">").append(esc(title)).append("</div>")
                .append("<div class=\"no\">").append(esc(d.number())).append("</div><table class=\"dates\">")
                .append(row(InvoiceTexts.both(lang, Key.ISSUED), date(lang, d.issueDate())))
                .append(row(InvoiceTexts.both(lang, Key.TAXABLE), date(lang, d.taxableDate())));
        if (!credit) {
            h.append("<tr class=\"due\"><td class=\"k\">").append(esc(InvoiceTexts.both(lang, Key.DUE)))
                    .append("</td><td class=\"v\">").append(esc(date(lang, d.dueDate()))).append("</td></tr>");
        }
        if (d.variableSymbol() != null) {
            h.append(row(InvoiceTexts.both(lang, Key.SYMBOL), d.variableSymbol()));
        }
        h.append("</table></td></tr></table><div class=\"rule\"></div>");

        h.append("<table class=\"parties\"><tr><td>").append(party(lang, InvoiceTexts.both(lang, Key.SUPPLIER), d.supplier()))
                .append("</td><td>").append(party(lang, InvoiceTexts.both(lang, Key.CUSTOMER), d.customer()))
                .append("</td></tr></table>");

        h.append("<table class=\"subject\">");
        if (d.creditOfNumber() != null) {
            h.append(row(InvoiceTexts.both(lang, Key.CREDIT_OF), d.creditOfNumber()));
        }
        h.append(row(InvoiceTexts.both(lang, Key.DEAL), d.dealNumber() + " — " + d.subject())).append("</table>");

        h.append("<table class=\"lines\"><thead><tr><th>").append(esc(InvoiceTexts.both(lang, Key.DESCRIPTION)))
                .append("</th><th class=\"n\">").append(esc(InvoiceTexts.one(lang, Key.QUANTITY)))
                .append("</th><th class=\"u\">").append(esc(InvoiceTexts.one(lang, Key.UNIT)))
                .append("</th><th class=\"n\">").append(esc(InvoiceTexts.one(lang, Key.UNIT_PRICE)))
                .append("</th><th class=\"n\">").append(esc(InvoiceTexts.one(lang, Key.LINE_TOTAL))).append("</th></tr></thead><tbody>");
        for (InvoiceDocument.Line l : d.lines()) {
            h.append("<tr><td>").append(esc(l.description())).append("</td><td class=\"n\">").append(qty(lang, l.quantity()))
                    .append("</td><td class=\"u\">").append(esc(l.unit())).append("</td><td class=\"n\">")
                    .append(money(lang, l.unitPrice())).append("</td><td class=\"n\">").append(money(lang, l.total()))
                    .append("</td></tr>");
        }
        h.append("</tbody></table>");

        String cur = " " + d.currency();
        h.append("<table class=\"totals\">").append(row(InvoiceTexts.both(lang, Key.SUBTOTAL), money(lang, d.subtotal()) + cur));
        h.append(d.vatMode().chargesVat()
                ? row(InvoiceTexts.both(lang, Key.VAT).replace("{rate}", qty(lang, d.vatRate())), money(lang, d.vatAmount()) + cur)
                : row(InvoiceTexts.both(lang, Key.NO_VAT), "—"));
        h.append("<tr class=\"grand\"><td>").append(esc(InvoiceTexts.both(lang, Key.TOTAL))).append("</td><td class=\"v\">")
                .append(money(lang, d.total())).append(esc(cur)).append("</td></tr></table>");

        if (d.czkPerUnit() != null && !"CZK".equals(d.currency())) {
            h.append("<table class=\"czk\"><tr><td colspan=\"2\" class=\"ratehead\">")
                    .append(esc(InvoiceTexts.one(DocLanguage.CS, Key.RATE).replace("{date}", date(DocLanguage.CS, d.rateDate()))
                            .replace("{cur}", d.currency()).replace("{rate}", qty(DocLanguage.CS, d.czkPerUnit()))))
                    .append("</td></tr>")
                    .append(row(InvoiceTexts.one(DocLanguage.CS, Key.SUBTOTAL), money(DocLanguage.CS, d.subtotalCzk()) + " CZK"));
            if (d.vatMode().chargesVat()) {
                h.append(row(InvoiceTexts.one(DocLanguage.CS, Key.VAT).replace("{rate}", qty(DocLanguage.CS, d.vatRate())),
                        money(DocLanguage.CS, d.vatAmountCzk()) + " CZK"));
            }
            h.append("</table>");
        }
        if (d.vatNote() != null) {
            h.append("<p class=\"vatnote\">").append(esc(d.vatNote())).append("</p>");
        }
        if (d.note() != null) {
            h.append("<p class=\"note\"><b>").append(esc(InvoiceTexts.both(lang, Key.NOTE))).append(":</b> ")
                    .append(escLines(d.note())).append("</p>");
        }

        if (!credit && d.bank() != null) {
            h.append("<table class=\"pay\"><tr><td class=\"bank\"><h2>").append(esc(InvoiceTexts.both(lang, Key.BANK))).append("</h2><p>")
                    .append(d.bank().bankName() == null ? "" : esc(d.bank().bankName()) + "<br/>")
                    .append("IBAN ").append(esc(iban(d.bank().iban())))
                    .append(d.bank().swift() == null ? "" : "<br/>SWIFT/BIC " + esc(d.bank().swift()));
            if (d.variableSymbol() != null) {
                h.append("<br/>").append(esc(InvoiceTexts.one(lang, Key.SYMBOL))).append(" ").append(esc(d.variableSymbol()));
            }
            h.append("</p><p class=\"amount\">").append(money(lang, d.total())).append(esc(cur)).append("</p></td>");
            if (qrSrc != null) {
                h.append("<td class=\"qr\"><img src=\"").append(esc(qrSrc)).append("\" alt=\"QR\"/><div>")
                        .append(esc(InvoiceTexts.both(lang, Key.PAY_QR))).append("</div></td>");
            }
            h.append("</tr></table>");
        }
        if (d.issuedBy() != null) {
            h.append("<p class=\"small\">").append(esc(InvoiceTexts.both(lang, Key.ISSUED_BY))).append(": ")
                    .append(esc(d.issuedBy())).append("</p>");
        }
        return h.append("</body></html>").toString();
    }

    private static String css(InvoiceDocument d, String font) {
        String family = (font == null ? "" : "'" + font + "', ") + "sans-serif";
        String footer = cssText(d.supplier().name() + (d.supplier().companyId() == null ? ""
                : "   IČO " + d.supplier().companyId()) + (d.supplier().vatNumber() == null ? "" : "   DIČ " + d.supplier().vatNumber()));
        return """
            @page { size: A4; margin: 15mm 15mm 18mm 15mm;
              @bottom-left { content: "%s"; font-family: %s; font-size: 7.5pt; color: #645e6e; }
              @bottom-right { content: "%s " counter(page) " / " counter(pages); font-family: %s; font-size: 7.5pt; color: #645e6e; } }
            body { font-family: %s; font-size: 9pt; line-height: 1.4; color: #1e1b24; margin: 0; }
            table { border-collapse: collapse; width: 100%%; }
            td, th { vertical-align: top; padding: 0; }
            .head td.logo { width: 45%%; }
            .head img { width: 52mm; }
            .wordmark { font-size: 16pt; font-weight: 600; color: #2a1145; }
            .head td.doc { text-align: right; }
            .title { font-size: 15pt; font-weight: 600; color: #2a1145; line-height: 1.2; }
            .no { font-size: 13pt; font-weight: 600; margin: 1mm 0 2mm 0; }
            .dates { width: auto; margin-left: auto; }
            .dates td { padding: 0 0 0 5mm; text-align: right; }
            .dates td.k { color: #645e6e; }
            .dates tr.due td { font-weight: 600; color: #1e1b24; }
            .rule { border-top: 1.4pt solid #d9a21b; margin: 5mm 0; }
            .parties td { width: 50%%; padding-right: 8mm; }
            .label { color: #645e6e; font-size: 8pt; margin-bottom: 1mm; }
            .pname { font-size: 10.5pt; font-weight: 600; margin-bottom: 1mm; }
            .subject { margin: 5mm 0 4mm 0; }
            .subject td.k { width: 45mm; color: #645e6e; }
            .lines th { font-weight: 600; font-size: 8pt; color: #2a1145; text-align: left; padding: 2mm 2mm 2mm 0; border-bottom: 1pt solid #2a1145; }
            .lines td { padding: 2mm 2mm 2mm 0; border-bottom: 0.5pt solid #e3e0e8; }
            .lines tr { page-break-inside: avoid; }
            .n { text-align: right; white-space: nowrap; }
            .lines th.n, .lines td.n { padding-right: 0; padding-left: 3mm; }
            .lines th.u, .lines td.u { padding-left: 1.5mm; }
            .totals { width: 95mm; margin: 4mm 0 0 auto; }
            .totals td { padding: 1.1mm 0; }
            .totals td.v { text-align: right; white-space: nowrap; }
            .totals tr.grand td { border-top: 1pt solid #2a1145; padding-top: 2mm; font-size: 12pt; font-weight: 600; color: #2a1145; }
            .czk { width: 95mm; margin: 3mm 0 0 auto; background-color: #f6f2fb; }
            .czk td { padding: 1mm 3mm; }
            .czk td.v { text-align: right; white-space: nowrap; }
            .czk td.ratehead { color: #645e6e; font-size: 8pt; padding-top: 2mm; }
            .vatnote { margin: 4mm 0 0 0; padding: 2.5mm 3mm; border: 0.5pt solid #e3e0e8; }
            .note { margin: 3mm 0 0 0; }
            .pay { margin-top: 6mm; border: 1pt solid #2a1145; page-break-inside: avoid; }
            .pay td.bank { padding: 3mm 4mm; }
            .pay td.qr { width: 36mm; padding: 3mm; text-align: center; font-size: 7.5pt; color: #645e6e; }
            .pay td.qr img { width: 30mm; height: 30mm; }
            .pay h2 { font-size: 9pt; font-weight: 600; color: #2a1145; margin: 0 0 1.5mm 0; }
            .pay p { margin: 0; }
            .pay p.amount { font-size: 13pt; font-weight: 600; margin-top: 2mm; color: #2a1145; }
            .small { color: #645e6e; font-size: 8pt; margin: 4mm 0 0 0; }
            """.formatted(footer, family, cssText(InvoiceTexts.one(d.language(), Key.PAGE)), family, family);
    }

    private static String party(DocLanguage lang, String label, InvoiceDocument.Party p) {
        StringBuilder s = new StringBuilder("<div class=\"label\">").append(esc(label)).append("</div><div class=\"pname\">")
                .append(esc(p.name())).append("</div>");
        if (p.address() != null) {
            s.append(esc(p.address())).append("<br/>");
        }
        if (p.companyId() != null) {
            s.append(esc(InvoiceTexts.companyIdLabel(lang, p.country()))).append(" ").append(esc(p.companyId())).append("<br/>");
        }
        if (p.vatNumber() != null) {
            s.append(esc("CZ".equalsIgnoreCase(p.country()) ? "DIČ" : InvoiceTexts.both(lang, Key.VAT_ID))).append(" ")
                    .append(esc(p.vatNumber())).append("<br/>");
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

}
