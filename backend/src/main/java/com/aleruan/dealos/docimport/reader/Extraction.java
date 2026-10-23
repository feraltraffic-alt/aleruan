package com.aleruan.dealos.docimport.reader;

import java.util.List;

/**
 * What was read from a document, as printed: every value is text (empty when absent), so nothing is
 * invented by number parsing at this stage. {@link com.aleruan.dealos.docimport.application.ImportChecks}
 * parses and checks it.
 */
public record Extraction(String documentType, String summary, String documentNumber, String issueDate,
                         String taxableDate, String dueDate, String currency, String variableSymbol, Party issuer,
                         Party recipient, String netAmount, String vatAmount, String totalAmount, String vatNote,
                         String references, List<Line> lines, List<String> warnings) {

    public record Party(String name, String companyId, String vatNumber, String address, String country,
                        String bankAccount) {
        public static Party empty() {
            return new Party("", "", "", "", "", "");
        }
    }

    public record Line(String description, String quantity, String unit, String unitPrice, String total,
                       String vatRate) {
    }

    public Extraction {
        issuer = issuer == null ? Party.empty() : issuer;
        recipient = recipient == null ? Party.empty() : recipient;
        lines = lines == null ? List.of() : List.copyOf(lines);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** An empty form for a person to fill in by hand (no reader configured, or reading failed). */
    public static Extraction blank() {
        return new Extraction("OTHER", "", "", "", "", "", "", "", Party.empty(), Party.empty(), "", "", "", "", "",
                List.of(), List.of());
    }
}
