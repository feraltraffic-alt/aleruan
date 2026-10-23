package com.aleruan.dealos.counterparty.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Turns a registry snapshot into traffic-light findings. Rules from spec section 7.1. */
public final class SnapshotAnalyzer {

    /** Companies registered less than this many months ago are flagged yellow. */
    public static final int YOUNG_COMPANY_MONTHS = 12;

    private SnapshotAnalyzer() {
    }

    public static Set<CheckFinding> findings(RegistrySnapshot s, LocalDate today) {
        EnumSet<CheckFinding> result = EnumSet.noneOf(CheckFinding.class);
        if (!s.available()) {
            result.add(CheckFinding.SOURCE_UNAVAILABLE);
            return result;
        }
        if (Boolean.FALSE.equals(s.found())) {
            result.add(CheckFinding.NOT_FOUND_IN_REGISTRY);
            return result;
        }
        if (Boolean.FALSE.equals(s.vatValid())) {
            result.add(CheckFinding.VAT_INVALID);
        }
        if (Boolean.TRUE.equals(s.unreliableVatPayer())) {
            result.add(CheckFinding.UNRELIABLE_VAT_PAYER);
        }
        if (s.source() == CheckSource.BIALA_LISTA && s.vatStatus() != null
                && !"Czynny".equalsIgnoreCase(s.vatStatus())) {
            result.add(CheckFinding.VAT_NOT_ACTIVE);
        }
        if (Boolean.TRUE.equals(s.insolvency())) {
            result.add(CheckFinding.INSOLVENCY);
        }
        if (s.ceasedOn() != null && !s.ceasedOn().isAfter(today)) {
            result.add(CheckFinding.LIQUIDATION_OR_CEASED);
        }
        if (s.registeredOn() != null && s.registeredOn().isAfter(today.minusMonths(YOUNG_COMPANY_MONTHS))) {
            result.add(CheckFinding.YOUNG_COMPANY);
        }
        return result;
    }

    /**
     * Payment check: paying to an account the registry does not publish is a VAT liability risk
     * (CZ § 109 ZDPH, PL white list). Only applies when the registry publishes accounts at all.
     */
    public static Set<CheckFinding> accountFindings(RegistrySnapshot s, String account) {
        if (!s.available()) {
            return EnumSet.of(CheckFinding.SOURCE_UNAVAILABLE);
        }
        String wanted = TaxIdentifiers.normalizeAccount(account);
        boolean published = s.publishedAccounts().stream()
                .map(TaxIdentifiers::normalizeAccount)
                .anyMatch(wanted::equals);
        return published ? EnumSet.noneOf(CheckFinding.class) : EnumSet.of(CheckFinding.ACCOUNT_NOT_PUBLISHED);
    }
}
