package com.aleruan.dealos.platform.evidence;

/** Parts of the package in the order they appear in the summary and in the archive. */
public enum EvidenceSection {
    CONTRACT("01-contract"),
    QUOTES("02-quotes"),
    CHANGE_ORDERS("03-change-orders"),
    ACCEPTANCE("04-acceptance"),
    INVOICES("05-invoices"),
    DIARY("06-site-diary"),
    DEFECTS("07-defects"),
    CHECKS("08-checks"),
    AUDIT("09-audit");

    private final String folder;

    EvidenceSection(String folder) {
        this.folder = folder;
    }

    /** Folder of the section's files inside the ZIP. */
    public String folder() {
        return folder;
    }
}
