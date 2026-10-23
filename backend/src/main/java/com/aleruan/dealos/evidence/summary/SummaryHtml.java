package com.aleruan.dealos.evidence.summary;

import static com.aleruan.dealos.evidence.summary.SummaryTexts.t;
import static com.aleruan.dealos.platform.documents.DocFormat.cssText;
import static com.aleruan.dealos.platform.documents.DocFormat.esc;
import static com.aleruan.dealos.platform.documents.DocFormat.escLines;

import com.aleruan.dealos.platform.documents.DocFormat;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** The evidence package summary as strict XHTML + CSS 2.1 for the PDF renderer. */
public final class SummaryHtml {

    private SummaryHtml() {
    }

    public static String render(SummaryModel m, DocLanguage lang, String logoSrc, String font) {
        String family = (font == null ? "" : "'" + font + "', ") + "sans-serif";
        StringBuilder h = new StringBuilder(20_000);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">")
                .append("<head><meta charset=\"UTF-8\"/><title>").append(esc(t(lang, "title") + " " + m.dealNumber()))
                .append("</title><style>").append(css(m, lang, family)).append("</style></head><body>");

        h.append("<table class=\"head\"><tr><td class=\"logo\">");
        if (logoSrc != null) {
            h.append("<img src=\"").append(esc(logoSrc)).append("\" alt=\"\"/>");
        }
        h.append("</td><td class=\"doc\"><div class=\"title\">").append(esc(t(lang, "title"))).append("</div>")
                .append("<div class=\"no\">").append(esc(m.dealNumber())).append("</div>")
                .append("<div class=\"k\">").append(esc(t(lang, "generated"))).append(" ")
                .append(esc(DocFormat.date(lang, m.generatedAt().toLocalDate()) + " "
                        + m.generatedAt().format(DateTimeFormatter.ofPattern("HH:mm"))))
                .append(m.generatedBy() == null ? "" : " " + esc(t(lang, "generatedBy")) + " " + esc(m.generatedBy()))
                .append("</div></td></tr></table><div class=\"rule\"></div>");

        h.append("<table class=\"facts\">")
                .append(row(t(lang, "deal"), "<b>" + esc(m.dealNumber()) + "</b> " + esc(m.dealTitle())))
                .append(row(t(lang, "customer"), esc(m.customer())))
                .append(row(t(lang, "contractor"), esc(m.contractor())));
        if (m.contractValue() != null) {
            h.append(row(t(lang, "contractValue"), esc(DocFormat.money(lang, m.contractValue()) + " " + nz(m.currency()))));
        }
        h.append("</table>");

        completeness(h, m, lang);
        for (EvidenceSection section : EvidenceSection.values()) {
            if (section == EvidenceSection.AUDIT) {
                audit(h, m, lang);
            } else {
                section(h, m, lang, section);
            }
        }
        files(h, m, lang);

        h.append("<h2>").append(esc(t(lang, "verify"))).append("</h2><p class=\"note\">")
                .append(esc(t(lang, "verify.text"))).append("</p>");
        return h.append("</body></html>").toString();
    }

    private static void completeness(StringBuilder h, SummaryModel m, DocLanguage lang) {
        long ok = m.checks().stream().filter(c -> c.level() == EvidenceCheck.Level.OK).count();
        h.append("<h2>").append(esc(t(lang, "completeness"))).append(" <span class=\"score\">")
                .append(esc(t(lang, "completenessScore", Map.of("ok", Long.toString(ok),
                        "total", Integer.toString(m.checks().size())))))
                .append("</span></h2><table class=\"checks\">");
        for (EvidenceCheck c : m.checks()) {
            h.append("<tr><td class=\"lvl\"><span class=\"lvl ").append(c.level().name().toLowerCase())
                    .append("\">").append(esc(t(lang, "level." + c.level().name()))).append("</span></td><td>")
                    .append(esc(SummaryTexts.check(lang, c))).append("</td></tr>");
        }
        h.append("</table>");
    }

    private static void section(StringBuilder h, SummaryModel m, DocLanguage lang, EvidenceSection section) {
        List<SummaryModel.Item> items = m.items().stream().filter(i -> i.entry().section() == section).toList();
        h.append("<h2>").append(esc(t(lang, "section." + section.name()))).append("</h2>");
        if (items.isEmpty()) {
            h.append("<p class=\"none\">").append(esc(t(lang, "none"))).append("</p>");
            return;
        }
        h.append("<table class=\"list\"><thead><tr><th class=\"date\">").append(esc(t(lang, "col.date")))
                .append("</th><th>").append(esc(t(lang, "col.document")))
                .append("</th><th class=\"st\">").append(esc(t(lang, "col.status")))
                .append("</th><th class=\"num\">").append(esc(t(lang, "col.amount")))
                .append("</th><th class=\"by\">").append(esc(t(lang, "col.by"))).append("</th></tr></thead><tbody>");
        for (SummaryModel.Item item : items) {
            EvidenceEntry e = item.entry();
            h.append("<tr><td class=\"date\">").append(esc(DocFormat.date(lang, e.date()))).append("</td><td>");
            if (!e.title().isBlank()) {
                h.append("<b>").append(esc(e.title())).append("</b>");
            }
            if (e.reference() != null && !e.title().contains(e.reference())) {
                h.append(" <span class=\"k\">").append(esc(e.reference())).append("</span>");
            }
            if (e.text() != null) {
                h.append("<div class=\"text\">").append(escLines(e.text())).append("</div>");
            }
            for (SummaryModel.PackedFile f : item.files()) {
                h.append("<div class=\"file\">").append(esc(f.path())).append(f.generated() ? " *" : "").append("</div>");
            }
            h.append("</td><td class=\"st\">").append(esc(SummaryTexts.status(lang, e.status())))
                    .append("</td><td class=\"num\">")
                    .append(e.amount() == null ? "" : esc(DocFormat.money(lang, e.amount()) + " " + nz(e.currency())))
                    .append("</td><td class=\"by\">").append(esc(nz(e.by()))).append("</td></tr>");
        }
        h.append("</tbody></table>");
    }

    private static void audit(StringBuilder h, SummaryModel m, DocLanguage lang) {
        SummaryModel.Audit a = m.audit();
        h.append("<h2>").append(esc(t(lang, "section.AUDIT"))).append("</h2>");
        if (a.records() > 0) {
            h.append("<p>").append(esc(t(lang, "audit.records", Map.of("count", Integer.toString(a.records()),
                    "first", String.valueOf(a.firstSeq()), "last", String.valueOf(a.lastSeq()))))).append("</p>");
        }
        h.append("<p>").append(esc(a.chainValid()
                ? t(lang, "audit.intact", Map.of("verified", Long.toString(a.verifiedRecords())))
                : t(lang, "audit.broken", Map.of("seq", String.valueOf(a.brokenAtSeq()))))).append("</p>");
        if (a.lastHash() != null) {
            h.append("<p><span class=\"k\">").append(esc(t(lang, "audit.lastHash"))).append("</span><br/><span class=\"hash\">")
                    .append(esc(a.lastHash())).append("</span></p>");
        }
        m.items().stream().filter(i -> i.entry().section() == EvidenceSection.AUDIT)
                .flatMap(i -> i.files().stream())
                .forEach(f -> h.append("<div class=\"file\">").append(esc(f.path())).append(" *</div>"));
    }

    private static void files(StringBuilder h, SummaryModel m, DocLanguage lang) {
        h.append("<h2 class=\"break\">").append(esc(t(lang, "files"))).append("</h2><p class=\"note\">")
                .append(esc(t(lang, "files.note"))).append("</p><table class=\"hashes\">");
        for (SummaryModel.PackedFile f : m.files()) {
            h.append("<tr><td class=\"path\">").append(esc(f.path())).append(f.generated() ? " *" : "")
                    .append("</td><td class=\"hash\">").append(esc(f.sha256())).append("</td></tr>");
        }
        h.append("</table>");
    }

    private static String row(String k, String vHtml) {
        return "<tr><td class=\"k\">" + esc(k) + "</td><td>" + vHtml + "</td></tr>";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String css(SummaryModel m, DocLanguage lang, String family) {
        return """
            @page { size: A4; margin: 16mm 14mm 18mm 14mm;
              @bottom-left { content: "%s"; font-family: %s; font-size: 7.5pt; color: #645e6e; }
              @bottom-right { content: "%s " counter(page) " / " counter(pages); font-family: %s; font-size: 7.5pt; color: #645e6e; } }
            body { font-family: %s; font-size: 9pt; line-height: 1.38; color: #1e1b24; margin: 0; }
            table { border-collapse: collapse; width: 100%%; }
            td, th { vertical-align: top; padding: 0; text-align: left; }
            .head img { width: 52mm; }
            .head td.doc { text-align: right; }
            .title { font-size: 18pt; font-weight: 600; color: #2a1145; }
            .no { font-size: 11pt; font-weight: 600; margin: 1mm 0; }
            .k { color: #645e6e; font-size: 8pt; }
            .rule { border-top: 1.4pt solid #d9a21b; margin: 5mm 0; }
            .facts td { padding: 1.4mm 0; border-bottom: 0.5pt solid #e3e0e8; }
            .facts td.k { width: 44mm; font-size: 8.5pt; }
            h2 { font-size: 10.5pt; font-weight: 600; color: #2a1145; margin: 7mm 0 2mm 0; page-break-after: avoid; }
            h2.break { page-break-before: always; margin-top: 0; }
            .score { font-weight: 400; color: #645e6e; font-size: 9pt; }
            .checks td { padding: 1.2mm 0; border-bottom: 0.5pt solid #eeebf2; }
            .checks td.lvl { width: 28mm; }
            span.lvl { font-size: 7.5pt; font-weight: 600; padding: 0.3mm 1.6mm; }
            span.ok { background-color: #e3f1e6; color: #1d5e2c; }
            span.warn { background-color: #fbf0d4; color: #7a5300; }
            span.missing { background-color: #f8dede; color: #8a1f1f; }
            .list th { font-size: 7.5pt; font-weight: 600; color: #645e6e; border-bottom: 0.8pt solid #2a1145; padding: 1mm 1.5mm 1mm 0; }
            .list td { padding: 1.4mm 1.5mm 1.4mm 0; border-bottom: 0.5pt solid #e3e0e8; }
            .list tr { page-break-inside: avoid; }
            .date { width: 21mm; white-space: nowrap; }
            .st { width: 26mm; }
            .by { width: 26mm; font-size: 8pt; }
            .num { width: 30mm; text-align: right; white-space: nowrap; }
            .list td.num, .list th.num { padding-right: 4mm; }
            .text { margin-top: 0.8mm; font-size: 8.5pt; }
            .file { font-size: 7.5pt; color: #645e6e; margin-top: 0.6mm; }
            .none { color: #645e6e; font-size: 8.5pt; margin: 0; }
            .note { font-size: 8.5pt; color: #45404e; }
            .hashes td { padding: 0.9mm 0; border-bottom: 0.4pt solid #eeebf2; font-size: 7pt; }
            .hashes td.path { width: 72mm; padding-right: 3mm; }
            .hash { font-size: 7pt; color: #45404e; }
            """.formatted(cssText(m.contractor() + " · " + m.dealNumber()), family, cssText(t(lang, "page")), family, family);
    }
}
