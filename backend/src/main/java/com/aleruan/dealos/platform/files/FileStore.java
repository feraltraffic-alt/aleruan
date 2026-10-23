package com.aleruan.dealos.platform.files;

import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and serves files that belong to a record; the owning module checks the record exists. */
@Service
public class FileStore {

    public record Content(FileInfo info, byte[] data) {
    }

    private final StoredFileRepository files;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;
    private final int maxBytes;
    private final int maxPerOwner;

    FileStore(StoredFileRepository files, AuditService audit, CurrentActor actor, Clock clock,
              @Value("${dealos.files.max-bytes:12582912}") int maxBytes,
              @Value("${dealos.files.max-per-record:30}") int maxPerOwner) {
        this.files = files;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
        this.maxBytes = maxBytes;
        this.maxPerOwner = maxPerOwner;
    }

    @Transactional
    public FileInfo store(String ownerType, UUID ownerId, String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (data.length > maxBytes) {
            throw new IllegalArgumentException("The file is larger than " + maxBytes / (1024 * 1024) + " MB");
        }
        FileKind kind = FileKind.detect(data)
                .orElseThrow(() -> new IllegalArgumentException("Only photos (JPEG, PNG, WebP) and PDF files can be attached"));
        if (files.countFor(ownerType, ownerId) >= maxPerOwner) {
            throw new IllegalStateException("A record can hold at most " + maxPerOwner + " files");
        }
        StoredFile f = files.save(new StoredFile(ownerType, ownerId, FileKind.safeName(fileName, kind), kind, data,
                sha256(data), clock.instant(), actor.name()));
        FileInfo info = f.info();
        audit.record("File", info.id().toString(), "UPLOADED", Map.of("ownerType", ownerType, "ownerId", ownerId,
                "fileName", info.fileName(), "sha256", info.sha256(), "size", info.sizeBytes()));
        return info;
    }

    @Transactional(readOnly = true)
    public List<FileInfo> list(String ownerType, List<UUID> ownerIds) {
        return ownerIds.isEmpty() ? List.of() : files.infos(ownerType, ownerIds);
    }

    @Transactional(readOnly = true)
    public Content get(UUID id) {
        StoredFile f = files.findById(id).orElseThrow(() -> new NotFoundException("File " + id + " not found"));
        return new Content(f.info(), f.data());
    }

    /** The file's bytes without loading the entity, for exports that read many files in one go. */
    @Transactional(readOnly = true)
    public byte[] data(UUID id) {
        byte[] data = files.data(id);
        if (data == null) {
            throw new NotFoundException("File " + id + " not found");
        }
        return data;
    }

    /** Removes a file of the given owner; the audit log keeps its name and hash. */
    @Transactional
    public void delete(String ownerType, UUID ownerId, UUID fileId) {
        StoredFile f = files.findById(fileId).orElseThrow(() -> new NotFoundException("File " + fileId + " not found"));
        FileInfo info = f.info();
        if (!info.ownerType().equals(ownerType) || !info.ownerId().equals(ownerId)) {
            throw new NotFoundException("File " + fileId + " not found");
        }
        files.delete(f);
        audit.record("File", fileId.toString(), "DELETED", Map.of("ownerType", ownerType, "ownerId", ownerId,
                "fileName", info.fileName(), "sha256", info.sha256()));
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
