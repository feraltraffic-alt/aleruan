package com.aleruan.dealos.quote.domain;

/** How VAT is shown on a quote. The accountant has the last word; the system only suggests. */
public enum VatMode {
    /** Czech VAT charged by Aleruan at the given rate. */
    STANDARD,
    /** EU reverse charge: the customer accounts for VAT in its own member state. */
    REVERSE_CHARGE_EU,
    /** Czech domestic reverse charge for construction works, Section 92e of the VAT Act. */
    REVERSE_CHARGE_CZ,
    /** Supply outside the EU VAT area (customer outside the EU). */
    OUTSIDE_SCOPE;

    public boolean chargesVat() {
        return this == STANDARD;
    }
}
