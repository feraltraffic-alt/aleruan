package com.aleruan.dealos.site.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Open → fixed (the subcontractor says so) → closed (Aleruan checked it). A fix that does not hold
 * reopens the defect; a report that is not a defect is dismissed with a reason.
 */
public enum DefectStatus {
    OPEN, FIXED, CLOSED, DISMISSED;

    private static final Map<DefectStatus, Set<DefectStatus>> NEXT = Map.of(
            OPEN, EnumSet.of(FIXED, DISMISSED),
            FIXED, EnumSet.of(CLOSED, OPEN),
            CLOSED, EnumSet.of(OPEN),
            DISMISSED, EnumSet.of(OPEN));

    public boolean canMoveTo(DefectStatus target) {
        return NEXT.get(this).contains(target);
    }

    public Set<DefectStatus> next() {
        return EnumSet.copyOf(NEXT.get(this));
    }

    public boolean isOpen() {
        return this == OPEN || this == FIXED;
    }
}
