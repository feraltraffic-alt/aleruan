package com.aleruan.dealos.platform.files;

import java.time.Instant;
import java.util.UUID;

/** A stored file without its bytes. */
public record FileInfo(UUID id, String ownerType, UUID ownerId, String fileName, String contentType, int sizeBytes,
                       String sha256, Instant uploadedAt, String uploadedBy) {
}
