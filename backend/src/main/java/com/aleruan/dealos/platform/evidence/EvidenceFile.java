package com.aleruan.dealos.platform.evidence;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A file that goes into the package. {@code sha256} is the stored hash when the file is kept in the
 * system (issued quotes and invoices, uploads); it is null for documents rendered on export, whose hash
 * is computed then. {@code content} is called only on export.
 */
public record EvidenceFile(String fileName, String contentType, String sha256, Supplier<byte[]> content) {

    public EvidenceFile {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
    }

    public static EvidenceFile pdf(String fileName, String sha256, Supplier<byte[]> content) {
        return new EvidenceFile(fileName, "application/pdf", sha256, content);
    }

    /** True when the file is rendered at export time rather than kept with a stored hash. */
    public boolean generated() {
        return sha256 == null;
    }
}
