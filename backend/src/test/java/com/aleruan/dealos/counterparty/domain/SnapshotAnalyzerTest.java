package com.aleruan.dealos.counterparty.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnapshotAnalyzerTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-23");

    @Test
    void healthyCompanyIsGreen() {
        RegistrySnapshot s = RegistrySnapshot.builder(CheckSource.ARES, AT).found(true)
                .registeredOn(LocalDate.parse("2022-03-01")).insolvency(false).build();
        assertTrue(SnapshotAnalyzer.findings(s, TODAY).isEmpty());
        assertEquals(CheckStatus.GREEN, Verdict.of(SnapshotAnalyzer.findings(s, TODAY)).status());
    }

    @Test
    void youngCompanyIsYellow() {
        RegistrySnapshot s = RegistrySnapshot.builder(CheckSource.ARES, AT).found(true)
                .registeredOn(LocalDate.parse("2026-02-01")).build();
        assertEquals(Set.of(CheckFinding.YOUNG_COMPANY), SnapshotAnalyzer.findings(s, TODAY));
    }

    @Test
    void insolvencyAndCeasedAreRed() {
        RegistrySnapshot s = RegistrySnapshot.builder(CheckSource.ARES, AT).found(true)
                .insolvency(true).ceasedOn(LocalDate.parse("2026-01-31")).build();
        Verdict v = Verdict.of(SnapshotAnalyzer.findings(s, TODAY));
        assertEquals(CheckStatus.RED, v.status());
        assertTrue(v.findings().contains(CheckFinding.INSOLVENCY));
        assertTrue(v.findings().contains(CheckFinding.LIQUIDATION_OR_CEASED));
    }

    @Test
    void invalidVatIsRed() {
        RegistrySnapshot s = RegistrySnapshot.builder(CheckSource.VIES, AT).found(false).vatValid(false).build();
        assertEquals(CheckStatus.RED, Verdict.of(SnapshotAnalyzer.findings(s, TODAY)).status());
    }

    @Test
    void polishVatStatusMustBeActive() {
        RegistrySnapshot s = RegistrySnapshot.builder(CheckSource.BIALA_LISTA, AT).found(true)
                .vatStatus("Zwolniony").build();
        assertEquals(Set.of(CheckFinding.VAT_NOT_ACTIVE), SnapshotAnalyzer.findings(s, TODAY));
    }

    @Test
    void unavailableSourceIsYellowNotError() {
        RegistrySnapshot s = RegistrySnapshot.unavailable(CheckSource.VIES, AT, "MS_UNAVAILABLE", null);
        Verdict v = Verdict.of(SnapshotAnalyzer.findings(s, TODAY));
        assertEquals(CheckStatus.YELLOW, v.status());
        assertEquals(List.of(CheckFinding.SOURCE_UNAVAILABLE), v.findings());
    }

    @Test
    void worstFindingWinsAndComesFirst() {
        Verdict v = Verdict.of(List.of(CheckFinding.YOUNG_COMPANY, CheckFinding.SANCTIONS_HIT));
        assertEquals(CheckStatus.RED, v.status());
        assertEquals(CheckFinding.SANCTIONS_HIT, v.findings().get(0));
    }

    @Test
    void accountMustBePublished() {
        RegistrySnapshot s = RegistrySnapshot.builder(CheckSource.REGISTR_DPH, AT).found(true)
                .publishedAccounts(List.of("PL23 1020 1013 0000 0202 0481 2139")).build();
        assertTrue(SnapshotAnalyzer.accountFindings(s, "PL23102010130000020204812139").isEmpty());
        assertEquals(Set.of(CheckFinding.ACCOUNT_NOT_PUBLISHED),
                SnapshotAnalyzer.accountFindings(s, "PL61109010140000071219812874"));
    }

    @Test
    void noRegistryMeansManualReview() {
        assertEquals(CheckStatus.YELLOW, Verdict.manualReviewRequired().status());
    }
}
