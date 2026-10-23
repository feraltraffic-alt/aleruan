package com.aleruan.dealos.evidence.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceLayoutTest {

    @Test
    void namesArePlainAsciiWithoutPathTricks() {
        assertEquals("Smlouva_o_dilo_podepsana.pdf", EvidenceLayout.safeName("Smlouva o dílo – podepsaná.pdf"));
        assertEquals("etc_passwd", EvidenceLayout.safeName("../../etc/passwd"));
        assertEquals("pdf", EvidenceLayout.safeName(".pdf"));
        assertEquals("file", EvidenceLayout.safeName("///"));
        assertEquals("file", EvidenceLayout.safeName(null));
        String longName = EvidenceLayout.safeName("a".repeat(300) + ".jpeg");
        assertEquals(100, longName.length());
        assertTrue(longName.endsWith(".jpeg"));
    }

    @Test
    void sameNamesInOneFolderGetNumbers() {
        EvidenceFile a = file("photo.jpg");
        EvidenceFile b = file("photo.jpg");
        EvidenceFile c = file("PHOTO.jpg");
        EvidenceFile other = file("photo.jpg");
        Map<EvidenceFile, String> paths = EvidenceLayout.paths(List.of(
                EvidenceEntry.of(EvidenceSection.DIARY, "").files(List.of(a, b)).build(),
                EvidenceEntry.of(EvidenceSection.DIARY, "").file(c).build(),
                EvidenceEntry.of(EvidenceSection.DEFECTS, "").file(other).build()));
        assertEquals("06-site-diary/photo.jpg", paths.get(a));
        assertEquals("06-site-diary/photo-2.jpg", paths.get(b));
        assertEquals("06-site-diary/PHOTO-3.jpg", paths.get(c));
        assertEquals("07-defects/photo.jpg", paths.get(other));
    }

    private static EvidenceFile file(String name) {
        return new EvidenceFile(name, "image/jpeg", "abc", () -> new byte[0]);
    }
}
