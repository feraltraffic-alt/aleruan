package com.aleruan.dealos.platform.files;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_file")
class StoredFile {

    @Id
    private UUID id;

    @Column(name = "owner_type", nullable = false, updatable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, updatable = false)
    private byte[] data;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by", updatable = false)
    private String uploadedBy;

    protected StoredFile() {
    }

    StoredFile(String ownerType, UUID ownerId, String fileName, FileKind kind, byte[] data, String sha256,
               Instant uploadedAt, String uploadedBy) {
        this.id = UUID.randomUUID();
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.contentType = kind.contentType();
        this.sizeBytes = data.length;
        this.sha256 = sha256;
        this.data = data;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }

    FileInfo info() {
        return new FileInfo(id, ownerType, ownerId, fileName, contentType, sizeBytes, sha256, uploadedAt, uploadedBy);
    }

    byte[] data() {
        return data;
    }
}
