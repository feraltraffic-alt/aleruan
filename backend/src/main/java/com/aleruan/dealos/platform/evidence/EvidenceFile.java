package com.aleruan.dealos.platform.evidence;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A file that goes into the package. A stored file (issued quote or invoice, an upload) carries the hash
 * recorded when it was stored, and export checks the bytes against it; a stored file without a recorded
 * hash is reported. A generated file (protocol, change order sheet, JSON extract) is rendered on export
 * from the recorded data and has no stored hash. {@code content} is called only on export.
 */
public record EvidenceFile(String fileName, String contentType, String sha256, boolean generated,
                           Supplier<byte[]> content) {

    public EvidenceFile {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        if (generated && sha256 != null) {
            throw new IllegalArgumentException("A generated file has no stored hash");
        }
    }

    public static EvidenceFile stored(String fileName, String contentType, String sha256, Supplier<byte[]> content) {
        return new EvidenceFile(fileName, contentType, sha256, false, content);
    }

    public static EvidenceFile generated(String fileName, String contentType, Supplier<byte[]> content) {
        return new EvidenceFile(fileName, contentType, null, true, content);
    }

    public static EvidenceFile storedPdf(String fileName, String sha256, Supplier<byte[]> content) {
        return stored(fileName, "application/pdf", sha256, content);
    }

    public static EvidenceFile generatedPdf(String fileName, Supplier<byte[]> content) {
        return generated(fileName, "application/pdf", content);
    }
}
