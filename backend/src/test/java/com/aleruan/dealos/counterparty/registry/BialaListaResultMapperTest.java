package com.aleruan.dealos.counterparty.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.SnapshotAnalyzer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BialaListaResultMapperTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-23");

    private static Map<String, Object> search(Map<String, Object> subject) {
        Map<String, Object> result = new HashMap<>();
        result.put("subject", subject);
        result.put("requestId", "Hx5Zt-8c9hd00");
        return Map.of("result", result);
    }

    @Test
    void activePayerWithKrsAndAccounts() {
        RegistrySnapshot s = BialaListaResultMapper.mapSearch(search(Map.of(
                "name", "POŁUDNIE BUDOWA SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ",
                "nip", "2815901396", "statusVat", "Czynny", "krs", "0000123456",
                "workingAddress", "DŁUGA 5, 31-147 KRAKÓW", "registrationLegalDate", "2018-03-01",
                "accountNumbers", List.of("23102010130000020204812139"))), "{}", AT);
        assertEquals("Hx5Zt-8c9hd00", s.requestId());
        assertEquals("PL2815901396", s.vatNumber());
        assertEquals("0000123456", s.krsNumber());
        assertEquals(List.of("23102010130000020204812139"), s.publishedAccounts());
        assertTrue(SnapshotAnalyzer.findings(s, TODAY).isEmpty());
    }

    @Test
    void exemptIsYellowNotListedIsRed() {
        RegistrySnapshot exempt = BialaListaResultMapper.mapSearch(search(Map.of("name", "X", "statusVat", "Zwolniony")), "{}", AT);
        assertEquals(Set.of(CheckFinding.NOT_VAT_PAYER), SnapshotAnalyzer.findings(exempt, TODAY));
        RegistrySnapshot missing = BialaListaResultMapper.mapSearch(search(null), "{}", AT);
        assertEquals(BialaListaResultMapper.NOT_REGISTERED, missing.vatStatus());
        assertEquals(Set.of(CheckFinding.VAT_NOT_ACTIVE), SnapshotAnalyzer.findings(missing, TODAY));
    }

    @Test
    void errorsMakeTheSourceUnavailable() {
        assertFalse(BialaListaResultMapper.mapSearch(
                Map.of("exception", Map.of("code", 400, "message", "Nieprawidłowy NIP")), "{}", AT).available());
        assertFalse(BialaListaResultMapper.mapSearch(Map.of("code", "WL-115", "message", "bad"), "{}", AT).available());
    }

    @Test
    void accountCheck() {
        String nrb = "23102010130000020204812139";
        RegistrySnapshot ok = BialaListaResultMapper.mapAccountCheck(
                Map.of("result", Map.of("accountAssigned", "TAK", "requestId", "r1")), "{}", AT, nrb);
        assertTrue(SnapshotAnalyzer.accountFindings(ok, "PL" + nrb).isEmpty());
        RegistrySnapshot no = BialaListaResultMapper.mapAccountCheck(
                Map.of("result", Map.of("accountAssigned", "NIE", "requestId", "r2")), "{}", AT, nrb);
        assertEquals(Set.of(CheckFinding.ACCOUNT_NOT_PUBLISHED), SnapshotAnalyzer.accountFindings(no, nrb));
    }
}
