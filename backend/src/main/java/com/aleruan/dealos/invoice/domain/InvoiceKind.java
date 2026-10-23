package com.aleruan.dealos.invoice.domain;

/** A normal tax document, or a credit note (opravný daňový doklad) reversing one. */
public enum InvoiceKind {
    INVOICE("FV", ""), CREDIT_NOTE("OD", "9");

    private final String prefix;
    private final String symbolPrefix;

    InvoiceKind(String prefix, String symbolPrefix) {
        this.prefix = prefix;
        this.symbolPrefix = symbolPrefix;
    }

    /** FV-2026-0007; credit notes OD-2026-0001. */
    public String number(int year, int seq) {
        return "%s-%d-%04d".formatted(prefix, year, seq);
    }

    /** Variable symbol (payment reference for Czech banks): digits only, at most 10. */
    public String variableSymbol(int year, int seq) {
        return symbolPrefix + year + "%04d".formatted(seq);
    }
}
