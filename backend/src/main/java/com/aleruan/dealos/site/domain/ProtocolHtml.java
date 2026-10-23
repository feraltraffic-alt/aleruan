package com.aleruan.dealos.site.domain;

import static com.aleruan.dealos.platform.documents.DocFormat.cssText;
import static com.aleruan.dealos.platform.documents.DocFormat.esc;
import static com.aleruan.dealos.platform.documents.DocFormat.escLines;

import com.aleruan.dealos.platform.documents.DocFormat;
import com.aleruan.dealos.platform.documents.DocLanguage;
import java.util.EnumMap;
import java.util.Map;

/** Handover protocol (předávací protokol) as strict XHTML + CSS 2.1 for the PDF renderer. */
public final class ProtocolHtml {

    enum Key {
        TITLE, NUMBER, DATE, CONTRACTOR, CUSTOMER, SITE, CONTRACT, SUBJECT, WORKS, VALUE, DECISION, ACCEPTED,
        WITH_REMARKS, REJECTED, REMARKS, FIX_UNTIL, HANDED_BY, TAKEN_BY, NAME, SIGNATURE, PAGE, VAT_ID
    }

    private static final Map<DocLanguage, String[]> TEXTS = new EnumMap<>(DocLanguage.class);

    static {
        TEXTS.put(DocLanguage.CS, new String[] {"Předávací protokol", "Číslo", "Datum", "Zhotovitel", "Objednatel",
                "Stavba", "Zakázka", "Předmět předání", "Provedené práce", "Hodnota etapy bez DPH", "Stanovisko objednatele",
                "přebírá bez výhrad", "přebírá s výhradami", "nepřebírá", "Vady, nedodělky a výhrady", "Termín odstranění",
                "Předal (zhotovitel)", "Převzal (objednatel)", "Jméno a funkce", "Podpis", "Strana", "DIČ"});
        TEXTS.put(DocLanguage.EN, new String[] {"Handover certificate", "Number", "Date", "Contractor", "Customer",
                "Site", "Contract", "Handed over", "Work performed", "Stage value excl. VAT", "Customer's decision",
                "accepted without remarks", "accepted with remarks", "not accepted", "Defects, outstanding items and remarks",
                "To be fixed by", "Handed over by (contractor)", "Taken over by (customer)", "Name and position", "Signature",
                "Page", "VAT ID"});
        TEXTS.put(DocLanguage.PL, new String[] {"Protokół odbioru", "Numer", "Data", "Wykonawca", "Zamawiający",
                "Obiekt", "Zlecenie", "Przedmiot odbioru", "Wykonane prace", "Wartość etapu netto", "Stanowisko zamawiającego",
                "odbiór bez zastrzeżeń", "odbiór z zastrzeżeniami", "odmowa odbioru", "Wady, usterki i zastrzeżenia",
                "Termin usunięcia", "Przekazał (wykonawca)", "Odebrał (zamawiający)", "Imię, nazwisko i stanowisko", "Podpis",
                "Strona", "NIP UE"});
        TEXTS.put(DocLanguage.DE, new String[] {"Abnahmeprotokoll", "Nummer", "Datum", "Auftragnehmer", "Auftraggeber",
                "Bauvorhaben", "Auftrag", "Gegenstand der Abnahme", "Ausgeführte Arbeiten", "Wert der Teilleistung netto",
                "Entscheidung des Auftraggebers", "abgenommen ohne Vorbehalt", "abgenommen mit Vorbehalt", "nicht abgenommen",
                "Mängel, Restarbeiten und Vorbehalte", "Beseitigung bis", "Übergeben (Auftragnehmer)",
                "Übernommen (Auftraggeber)", "Name und Funktion", "Unterschrift", "Seite", "USt-IdNr."});
        TEXTS.values().forEach(v -> {
            if (v.length != Key.values().length) {
                throw new IllegalStateException("Protocol texts are incomplete");
            }
        });
    }

    private ProtocolHtml() {
    }

    static String l(DocLanguage lang, Key key) {
        return TEXTS.get(lang)[key.ordinal()];
    }

    public static String render(ProtocolDocument d, String logoSrc, String font) {
        DocLanguage lang = d.language();
        String family = (font == null ? "" : "'" + font + "', ") + "sans-serif";
        StringBuilder h = new StringBuilder(6_000);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">")
                .append("<head><meta charset=\"UTF-8\"/><title>").append(esc(l(lang, Key.TITLE) + " " + d.number()))
                .append("</title><style>").append(css(d, family)).append("</style></head><body>");

        h.append("<table class=\"head\"><tr><td class=\"logo\">");
        if (logoSrc != null) {
            h.append("<img src=\"").append(esc(logoSrc)).append("\" alt=\"\"/>");
        }
        h.append("</td><td class=\"doc\"><div class=\"title\">").append(esc(l(lang, Key.TITLE))).append("</div>")
                .append("<div class=\"no\">").append(esc(d.number())).append("</div>")
                .append("<div class=\"k\">").append(esc(l(lang, Key.DATE))).append(" ")
                .append(esc(DocFormat.date(lang, d.date()))).append("</div></td></tr></table><div class=\"rule\"></div>");

        h.append("<table class=\"parties\"><tr><td>").append(party(lang, l(lang, Key.CONTRACTOR), d.contractor()))
                .append("</td><td>").append(party(lang, l(lang, Key.CUSTOMER), d.customer())).append("</td></tr></table>");

        h.append("<table class=\"facts\">")
                .append(row(l(lang, Key.SITE), esc(d.siteName()) + (d.siteAddress() == null ? "" : "<br/>" + esc(d.siteAddress()))))
                .append(row(l(lang, Key.CONTRACT), esc(d.dealNumber())))
                .append(row(l(lang, Key.SUBJECT), "<b>" + esc(d.stageTitle()) + "</b>"
                        + (d.stageDescription() == null ? "" : "<br/>" + escLines(d.stageDescription()))))
                .append(row(l(lang, Key.VALUE), esc(DocFormat.money(lang, d.amount()) + " " + d.currency())))
                .append("</table>");

        if (!d.works().isEmpty()) {
            h.append("<h2>").append(esc(l(lang, Key.WORKS))).append("</h2><ul class=\"works\">");
            d.works().forEach(w -> h.append("<li>").append(esc(w)).append("</li>"));
            h.append("</ul>");
        }

        h.append("<h2>").append(esc(l(lang, Key.DECISION))).append("</h2><table class=\"choice\"><tr>")
                .append(box(lang, Key.ACCEPTED, d.status() == StageStatus.ACCEPTED))
                .append(box(lang, Key.WITH_REMARKS, d.status() == StageStatus.ACCEPTED_WITH_REMARKS))
                .append(box(lang, Key.REJECTED, d.status() == StageStatus.REJECTED))
                .append("</tr></table>");

        h.append("<h2>").append(esc(l(lang, Key.REMARKS))).append("</h2>");
        if (d.remarks() != null) {
            h.append("<p class=\"remarks\">").append(escLines(d.remarks())).append("</p>");
        } else {
            h.append("<div class=\"blank\"></div><div class=\"blank\"></div><div class=\"blank\"></div>");
        }
        h.append("<p class=\"fix\">").append(esc(l(lang, Key.FIX_UNTIL))).append(": ")
                .append(d.fixUntil() == null ? "______________" : esc(DocFormat.date(lang, d.fixUntil()))).append("</p>");

        h.append("<table class=\"signs\"><tr><td><div class=\"who\">").append(esc(l(lang, Key.HANDED_BY))).append("</div>")
                .append(signLine(lang, d.handedOverBy(), null))
                .append("</td><td><div class=\"who\">").append(esc(l(lang, Key.TAKEN_BY))).append("</div>")
                .append(signLine(lang, d.decidedBy(), d.decidedOn()))
                .append("</td></tr></table>");
        return h.append("</body></html>").toString();
    }

    private static String signLine(DocLanguage lang, String name, java.time.LocalDate on) {
        return "<table class=\"sign\"><tr><td class=\"name\">" + esc(name == null ? "" : name) + "</td><td class=\"date\">"
                + esc(on == null ? "" : DocFormat.date(lang, on)) + "</td></tr><tr><td class=\"cap\">"
                + esc(l(lang, Key.NAME)) + "</td><td class=\"cap\">" + esc(l(lang, Key.DATE)) + "</td></tr></table>"
                + "<div class=\"sigline\"></div><div class=\"cap\">" + esc(l(lang, Key.SIGNATURE)) + "</div>";
    }

    private static String box(DocLanguage lang, Key key, boolean checked) {
        return "<td><span class=\"box\">" + (checked ? "X" : "&#160;") + "</span> " + esc(l(lang, key)) + "</td>";
    }

    private static String row(String k, String vHtml) {
        return "<tr><td class=\"k\">" + esc(k) + "</td><td>" + vHtml + "</td></tr>";
    }

    private static String party(DocLanguage lang, String label, ProtocolDocument.Party p) {
        StringBuilder s = new StringBuilder("<div class=\"k\">").append(esc(label)).append("</div><div class=\"pname\">")
                .append(esc(p.name())).append("</div>");
        if (p.address() != null) {
            s.append(esc(p.address())).append("<br/>");
        }
        if (p.companyId() != null) {
            String idLabel = "CZ".equalsIgnoreCase(p.country()) || "SK".equalsIgnoreCase(p.country()) ? "IČO"
                    : "PL".equalsIgnoreCase(p.country()) ? "NIP" : "ID";
            s.append(esc(idLabel)).append(" ").append(esc(p.companyId())).append("<br/>");
        }
        if (p.vatNumber() != null) {
            s.append(esc(l(lang, Key.VAT_ID))).append(" ").append(esc(p.vatNumber()));
        }
        return s.toString();
    }

    private static String css(ProtocolDocument d, String family) {
        return """
            @page { size: A4; margin: 16mm 16mm 18mm 16mm;
              @bottom-left { content: "%s"; font-family: %s; font-size: 7.5pt; color: #645e6e; }
              @bottom-right { content: "%s " counter(page) " / " counter(pages); font-family: %s; font-size: 7.5pt; color: #645e6e; } }
            body { font-family: %s; font-size: 9.5pt; line-height: 1.4; color: #1e1b24; margin: 0; }
            table { border-collapse: collapse; width: 100%%; }
            td { vertical-align: top; padding: 0; }
            .head img { width: 52mm; }
            .head td.doc { text-align: right; }
            .title { font-size: 18pt; font-weight: 600; color: #2a1145; }
            .no { font-size: 11pt; font-weight: 600; margin: 1mm 0; }
            .k { color: #645e6e; font-size: 8.5pt; }
            .rule { border-top: 1.4pt solid #d9a21b; margin: 5mm 0; }
            .parties td { width: 50%%; padding-right: 8mm; }
            .pname { font-size: 10.5pt; font-weight: 600; margin: 0.5mm 0 1mm 0; }
            .facts { margin-top: 6mm; }
            .facts td { padding: 1.6mm 0; border-bottom: 0.5pt solid #e3e0e8; }
            .facts td.k { width: 42mm; font-size: 9pt; }
            h2 { font-size: 10pt; font-weight: 600; color: #2a1145; margin: 6mm 0 2mm 0; }
            .works { margin: 0; padding-left: 5mm; }
            .choice td { width: 33%%; }
            .box { display: inline-block; width: 3.6mm; height: 3.6mm; border: 0.8pt solid #1e1b24; text-align: center;
              font-size: 8pt; line-height: 3.6mm; font-weight: 600; margin-right: 1.5mm; }
            .remarks { margin: 0; padding: 2.5mm 3mm; background-color: #f6f2fb; }
            .blank { border-bottom: 0.5pt solid #b9b3c2; height: 7mm; }
            .fix { margin: 3mm 0 0 0; }
            .signs { margin-top: 10mm; page-break-inside: avoid; }
            .signs > tbody > tr > td, .signs > tr > td { width: 50%%; padding-right: 10mm; }
            .who { font-weight: 600; margin-bottom: 3mm; }
            .sign td.name, .sign td.date { border-bottom: 0.5pt solid #b9b3c2; height: 6mm; vertical-align: bottom; }
            .sign td.date { width: 30mm; padding-left: 4mm; }
            .cap { color: #645e6e; font-size: 7.5pt; }
            .sigline { border-bottom: 0.5pt solid #1e1b24; height: 16mm; }
            """.formatted(cssText(d.contractor().name()), family, cssText(l(d.language(), Key.PAGE)), family, family);
    }
}
