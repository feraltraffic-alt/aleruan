package com.aleruan.dealos.docimport.application;

import com.aleruan.dealos.docimport.reader.Extraction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses what was read and points out what does not add up, so the person reviewing knows where to look.
 * Pure: no database, no Spring.
 */
public final class ImportChecks {

    /** One thing worth a look, as a code the UI translates and the values that fill its text. */
    public record Warning(String code, Map<String, String> params) {
    }

    private static final BigDecimal CENTS = new BigDecimal("0.02");
    private static final Set<String> INVOICES = Set.of("SUPPLIER_INVOICE", "CUSTOMER_INVOICE", "CREDIT_NOTE");
    private static final DateTimeFormatter CZ = DateTimeFormatter.ofPattern("d.M.yyyy");

    private ImportChecks() {
    }

    /**
     * @param ours identifiers of Aleruan itself (IČO, DIČ), to tell which side of the document it is on
     */
    public static List<Warning> check(Extraction e, Set<String> ours) {
        List<Warning> w = new ArrayList<>();
        boolean invoice = INVOICES.contains(e.documentType());

        BigDecimal net = amount(e.netAmount(), "netAmount", w);
        BigDecimal vat = amount(e.vatAmount(), "vatAmount", w);
        BigDecimal total = amount(e.totalAmount(), "totalAmount", w);
        LocalDate issued = date(e.issueDate(), "issueDate", w);
        date(e.taxableDate(), "taxableDate", w);
        LocalDate due = date(e.dueDate(), "dueDate", w);

        if (invoice) {
            if (blank(e.documentNumber())) {
                w.add(new Warning("MISSING_NUMBER", Map.of()));
            }
            if (total == null && blank(e.totalAmount())) {
                w.add(new Warning("MISSING_TOTAL", Map.of()));
            }
            if (blank(e.currency())) {
                w.add(new Warning("MISSING_CURRENCY", Map.of()));
            }
        }
        if (net != null && vat != null && total != null && net.add(vat).subtract(total).abs().compareTo(CENTS) > 0) {
            w.add(new Warning("AMOUNTS_DONT_ADD_UP", Map.of("net", net.toPlainString(), "vat", vat.toPlainString(),
                    "total", total.toPlainString())));
        }
        if (net != null && !e.lines().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean complete = true;
            for (Extraction.Line l : e.lines()) {
                BigDecimal t = parseAmount(l.total());
                if (t == null) {
                    complete = false;
                    break;
                }
                sum = sum.add(t);
            }
            // a line sum within a crown of the net amount is rounding; more is a line read wrong or missing
            if (complete && sum.subtract(net).abs().compareTo(BigDecimal.ONE) > 0) {
                w.add(new Warning("LINES_DONT_ADD_UP", Map.of("lines", sum.toPlainString(), "net", net.toPlainString())));
            }
        }
        if (issued != null && due != null && due.isBefore(issued)) {
            w.add(new Warning("DUE_BEFORE_ISSUE", Map.of()));
        }

        boolean issuerIsUs = isUs(e.issuer(), ours);
        boolean recipientIsUs = isUs(e.recipient(), ours);
        if ("SUPPLIER_INVOICE".equals(e.documentType())) {
            if (issuerIsUs) {
                w.add(new Warning("WE_ARE_THE_ISSUER", Map.of()));
            } else if (!recipientIsUs && hasIds(e.recipient())) {
                w.add(new Warning("NOT_ADDRESSED_TO_US", Map.of("name", e.recipient().name())));
            }
        }
        if ("CUSTOMER_INVOICE".equals(e.documentType()) && !issuerIsUs && hasIds(e.issuer())) {
            w.add(new Warning("WE_ARE_NOT_THE_ISSUER", Map.of("name", e.issuer().name())));
        }
        for (String note : e.warnings()) {
            if (!blank(note)) {
                w.add(new Warning("READER_NOTE", Map.of("text", note.strip())));
            }
        }
        return w;
    }

    /** The party that is not Aleruan: the issuer of a received document, the recipient of one Aleruan issued. */
    public static Extraction.Party otherSide(Extraction e, Set<String> ours) {
        if (isUs(e.issuer(), ours)) {
            return e.recipient();
        }
        if (isUs(e.recipient(), ours)) {
            return e.issuer();
        }
        return "CUSTOMER_INVOICE".equals(e.documentType()) ? e.recipient() : e.issuer();
    }

    public static boolean isUs(Extraction.Party p, Set<String> ours) {
        return p != null && (ours.contains(id(p.companyId())) || ours.contains(id(p.vatNumber())));
    }

    /** Identifiers compared without spaces and case, so "CZ 142 20 997" matches "CZ14220997". */
    public static String id(String s) {
        return s == null ? "" : s.replaceAll("[\\s.\\-/]", "").toUpperCase(Locale.ROOT);
    }

    /** Plain decimals as the reader is told to write them; tolerates "12 345,50" and "12.345,50". */
    public static BigDecimal parseAmount(String s) {
        if (blank(s)) {
            return null;
        }
        String v = s.strip().replace(" ", "").replace(" ", "").replaceAll("[^0-9,.\\-]", "");
        int comma = v.lastIndexOf(',');
        int dot = v.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            v = comma > dot ? v.replace(".", "").replace(',', '.') : v.replace(",", "");
        } else if (comma >= 0) {
            v = v.replace(',', '.');
        }
        try {
            return new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate parseDate(String s) {
        if (blank(s)) {
            return null;
        }
        String v = s.strip();
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(v.replace(" ", ""), CZ);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static BigDecimal amount(String s, String field, List<Warning> w) {
        BigDecimal v = parseAmount(s);
        if (v == null && !blank(s)) {
            w.add(new Warning("BAD_AMOUNT", fields(field, s)));
        }
        return v;
    }

    private static LocalDate date(String s, String field, List<Warning> w) {
        LocalDate v = parseDate(s);
        if (v == null && !blank(s)) {
            w.add(new Warning("BAD_DATE", fields(field, s)));
        }
        return v;
    }

    private static Map<String, String> fields(String field, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("value", value);
        return m;
    }

    private static boolean hasIds(Extraction.Party p) {
        return !blank(p.companyId()) || !blank(p.vatNumber());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
