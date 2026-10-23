package com.aleruan.dealos.platform.files;

import java.util.Optional;

/**
 * Accepted file types, recognised by their first bytes rather than by name or the browser's claim,
 * so a renamed executable or HTML page cannot be stored as a "photo".
 */
public enum FileKind {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    FileKind(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return this != PDF;
    }

    public static Optional<FileKind> detect(byte[] b) {
        if (b == null || b.length < 12) {
            return Optional.empty();
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Optional.of(PNG);
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of(WEBP);
        }
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    /** A file name safe to show and to send back in Content-Disposition, keeping letters with diacritics. */
    public static String safeName(String original, FileKind kind) {
        String base = original == null ? "" : original.replaceAll(".*[/\\\\]", "");
        base = base.replaceAll("[\\p{Cntrl}\"<>:|?*]", "").strip();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        if (base.isBlank()) {
            base = "file";
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base + "." + kind.extension();
    }
}
