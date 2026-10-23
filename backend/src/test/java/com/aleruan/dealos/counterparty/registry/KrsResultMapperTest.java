package com.aleruan.dealos.counterparty.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.domain.SnapshotAnalyzer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KrsResultMapperTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-23");

    private static Map<String, Object> odpis(String name, Map<String, Object> dzial6) {
        return Map.of("odpis", Map.of(
                "naglowekA", Map.of("numerKRS", "0000123456", "dataRejestracjiWKRS", "06.04.2001"),
                "dane", Map.of(
                        "dzial1", Map.of(
                                "danePodmiotu", Map.of("nazwa", name, "identyfikatory", Map.of("nip", "2815901396")),
                                "siedzibaIAdres", Map.of("adres", Map.of("ulica", "UL. DŁUGA", "nrDomu", "5",
                                        "kodPocztowy", "31-147", "miejscowosc", "KRAKÓW"))),
                        "dzial6", dzial6)));
    }

    @Test
    void activeCompany() {
        RegistrySnapshot s = KrsResultMapper.map(odpis("POŁUDNIE BUDOWA SPÓŁKA Z O.O.", Map.of()), "{}", AT);
        assertEquals(LocalDate.parse("2001-04-06"), s.registeredOn());
        assertEquals("UL. DŁUGA 5, 31-147 KRAKÓW", s.address());
        assertEquals("PL2815901396", s.vatNumber());
        assertTrue(SnapshotAnalyzer.findings(s, TODAY).isEmpty());
    }

    @Test
    void mergerTextMentioningLiquidationIsNotLiquidation() {
        Map<String, Object> merger = Map.of("polaczeniePodzialPrzeksztalcenia", List.of(Map.of(
                "opisPolaczeniaPodzialuPrzeksztalcenia", "Połączenie bez przeprowadzenia likwidacji spółki przejmowanej")));
        assertTrue(SnapshotAnalyzer.findings(KrsResultMapper.map(odpis("X SP. Z O.O.", merger), "{}", AT), TODAY).isEmpty());
    }

    @Test
    void nameSuffixesSignalLiquidationAndBankruptcy() {
        assertEquals(Set.of(CheckFinding.LIQUIDATION_OR_CEASED), SnapshotAnalyzer.findings(
                KrsResultMapper.map(odpis("WARTATEX SPÓŁKA Z O.O. W LIKWIDACJI", Map.of()), "{}", AT), TODAY));
        assertEquals(Set.of(CheckFinding.INSOLVENCY), SnapshotAnalyzer.findings(
                KrsResultMapper.map(odpis("ABC SP. Z O.O. W UPADŁOŚCI", Map.of()), "{}", AT), TODAY));
    }

    @Test
    void section6KeysSignalProceedings() {
        Map<String, Object> d6 = Map.of("likwidacja", Map.of("likwidatorzy", List.of(Map.of("nazwisko", "K."))));
        assertTrue(SnapshotAnalyzer.findings(KrsResultMapper.map(odpis("X SP. Z O.O.", d6), "{}", AT), TODAY)
                .contains(CheckFinding.LIQUIDATION_OR_CEASED));
    }

    @Test
    void notFound() {
        assertEquals(Boolean.FALSE, KrsResultMapper.map(Map.of(), "", AT).found());
    }
}
