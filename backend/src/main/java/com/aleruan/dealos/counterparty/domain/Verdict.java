package com.aleruan.dealos.counterparty.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/** Overall traffic light plus the findings that caused it (most severe first). */
public record Verdict(CheckStatus status, List<CheckFinding> findings) {

    public Verdict {
        findings = List.copyOf(findings);
    }

    public static Verdict of(Collection<CheckFinding> findings) {
        EnumSet<CheckFinding> unique = findings.isEmpty() ? EnumSet.noneOf(CheckFinding.class) : EnumSet.copyOf(findings);
        CheckStatus status = CheckStatus.GREEN;
        for (CheckFinding f : unique) {
            status = status.worst(f.severity());
        }
        List<CheckFinding> ordered = unique.stream()
                .sorted((a, b) -> b.severity().compareTo(a.severity()))
                .toList();
        return new Verdict(status, ordered);
    }

    /** No registry covers this country: someone has to check by hand. */
    public static Verdict manualReviewRequired() {
        return of(List.of(CheckFinding.MANUAL_REVIEW_REQUIRED));
    }
}
