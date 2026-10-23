package com.aleruan.dealos.site.domain;

/** Minor: cosmetic. Major: affects use, fix soon. Critical: safety or prevents use, fix immediately. */
public enum DefectSeverity {
    MINOR(14), MAJOR(7), CRITICAL(2);

    private final int defaultDays;

    DefectSeverity(int defaultDays) {
        this.defaultDays = defaultDays;
    }

    /** Usual time to fix when no date is agreed. */
    public int defaultDays() {
        return defaultDays;
    }
}
