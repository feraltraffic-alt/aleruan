package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import com.aleruan.dealos.site.application.SiteRecordsService;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.Defect;
import com.aleruan.dealos.site.domain.DefectSeverity;
import com.aleruan.dealos.site.domain.DefectStatus;
import com.aleruan.dealos.site.domain.DiaryEntry;
import com.aleruan.dealos.site.domain.Site;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SiteRecordsIntegrationTest extends IntegrationTestBase {

    @Autowired CounterpartyService counterparties;
    @Autowired DealService deals;
    @Autowired SiteService sites;
    @Autowired SiteRecordsService records;
    @Autowired FileStore files;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0};

    @Test
    void diaryPhotosDefectsAndWarranty() {
        Counterparty customer = counterparties.create(new CounterpartyService.NewCounterparty("Bytové družstvo Test",
                "CZ", null, null, null, null, null, Set.of(CounterpartyRole.CUSTOMER), Set.of()));
        Counterparty crew = counterparties.create(new CounterpartyService.NewCounterparty("Omítky Kolín s.r.o.",
                "CZ", null, null, null, null, null, Set.of(CounterpartyRole.SUBCONTRACTOR), Set.of()));
        Deal deal = deals.create(new DealService.NewDeal("Byt Kolín", customer.getId(), Direction.CONSTRUCTION, "CZ",
                "CZK", new BigDecimal("20")));
        deals.addLine(deal.getId(), new DealService.LineData(LineKind.WORK, "Omítky", "FINISHING", "m2",
                new BigDecimal("100"), new BigDecimal("200"), null, null, crew.getId()));
        deals.moveTo(deal.getId(), DealStatus.QUOTED);
        deals.moveTo(deal.getId(), DealStatus.WON);
        Site site = sites.create(deal.getId());

        DiaryEntry e = records.addEntry(site.getId(), new SiteRecordsService.DiaryData(records.today(), "Oblačno, 8 °C", 4,
                "Zahájeny omítky ve 2. NP", null));
        assertTrue(records.editable(e));
        DiaryEntry fix = records.addEntry(site.getId(), new SiteRecordsService.DiaryData(records.today(), null, null,
                "Oprava: pracovalo 5 lidí", e.getId()));
        assertEquals(e.getId(), fix.getCorrectsId());
        assertEquals(2, records.diary(site.getId()).size());

        FileInfo photo = records.attach(SiteRecordsService.DIARY, e.getId(), "IMG_2041.HEIC.jpg", JPEG);
        assertEquals("image/jpeg", photo.contentType());
        assertEquals("IMG_2041.HEIC.jpg", photo.fileName());
        assertEquals(1, records.photos(SiteRecordsService.DIARY, java.util.List.of(e.getId())).get(e.getId()).size());
        assertThrows(IllegalArgumentException.class, () -> records.attach(SiteRecordsService.DIARY, e.getId(), "x.jpg",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(JPEG.length, files.get(photo.id()).data().length);

        Defect d = records.report(site.getId(), new Defect.Details("Prasklina v omítce", null, "2. NP, chodba",
                DefectSeverity.MAJOR, crew.getId(), null, records.today(), null));
        assertEquals(1, d.getSeq());
        assertFalse(d.isWarranty());
        assertEquals(records.today().plusDays(7), d.getDueDate());
        assertThrows(IllegalArgumentException.class, () -> records.report(site.getId(), new Defect.Details("x", null,
                null, null, customer.getId(), null, records.today(), null)));
        records.move(d.getId(), DefectStatus.FIXED, "Přetmeleno");
        assertEquals(1, records.unresolvedCount(site.getId()));
        records.move(d.getId(), DefectStatus.CLOSED, null);
        assertEquals(0, records.unresolvedCount(site.getId()));

        records.setHandover(site.getId(), records.today().minusDays(1), null);
        Site handed = sites.get(site.getId());
        assertEquals(24, handed.getWarrantyMonths());
        assertEquals(records.today().minusDays(1).plusMonths(24).minusDays(1), handed.warrantyUntil());
        Defect claim = records.report(site.getId(), new Defect.Details("Odlupuje se malba", null, null, null, crew.getId(),
                null, records.today(), null));
        assertTrue(claim.isWarranty());
        assertEquals(2, claim.getSeq());
        assertTrue(records.unresolved().stream().anyMatch(x -> x.getId().equals(claim.getId())));
        assertThrows(IllegalArgumentException.class, () -> records.setHandover(site.getId(), records.today().plusDays(1), 24));
    }
}
