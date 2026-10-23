package com.aleruan.dealos.platform.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileKindTest {

    private static byte[] head(int... b) {
        byte[] out = new byte[16];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return out;
    }

    @Test
    void recognisesFilesByContentNotByName() {
        assertEquals(Optional.of(FileKind.JPEG), FileKind.detect(head(0xFF, 0xD8, 0xFF, 0xE0)));
        assertEquals(Optional.of(FileKind.PNG), FileKind.detect(head(0x89, 'P', 'N', 'G', 0x0D, 0x0A)));
        assertEquals(Optional.of(FileKind.WEBP), FileKind.detect(head('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
        assertEquals(Optional.of(FileKind.PDF), FileKind.detect("%PDF-1.7\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(FileKind.detect("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(FileKind.detect(head('M', 'Z', 0x90)).isEmpty());
        assertTrue(FileKind.detect(new byte[3]).isEmpty());
        assertTrue(FileKind.detect(null).isEmpty());
    }

    @Test
    void namesAreCleanedAndGetTheRealExtension() {
        assertEquals("Fasáda sever.jpg", FileKind.safeName("C:\\Users\\lena\\Fasáda sever.JPG", FileKind.JPEG));
        assertEquals("evil.png", FileKind.safeName("../../evil.html", FileKind.PNG));
        assertEquals("a_b.pdf", FileKind.safeName("a_b\".pdf\u0007", FileKind.PDF));
        assertEquals("file.webp", FileKind.safeName("   ", FileKind.WEBP));
        assertEquals("file.jpg", FileKind.safeName(null, FileKind.JPEG));
        assertEquals(124L, (long) FileKind.safeName("x".repeat(300) + ".jpg", FileKind.JPEG).length());
    }
}
