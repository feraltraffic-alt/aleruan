package com.aleruan.dealos.docimport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** One uploaded document on its way through reading and review. */
@Entity
@Table(name = "import_document")
public class ImportDocument {

    public static final int MAX_ATTEMPTS = 3;

    @Id
    private UUID id;

    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status = ImportStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type")
    private DocType docType;

    /** What Claude read, as JSON (the extraction schema); later replaced by what the person confirmed. */
    private String extracted;

    /** Problems found when checking the extraction, as a JSON array of codes with details. */
    private String warnings;

    private String error;

    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "result_type")
    private String resultType;

    @Column(name = "result_id")
    private UUID resultId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by", updatable = false)
    private String uploadedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Version
    private long version;

    protected ImportDocument() {
    }

    public ImportDocument(UUID id, UUID fileId, String fileName, String contentType, String sha256, Instant at,
                          String by) {
        this.id = id;
        this.fileId = fileId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.uploadedAt = at;
        this.uploadedBy = by;
    }

    /** The worker takes it; {@code processedAt} marks the last activity, so a stuck read can be found. */
    public void start(Instant at) {
        if (status != ImportStatus.QUEUED) {
            throw new IllegalStateException("Document " + fileName + " is " + status);
        }
        attempts++;
        processedAt = at;
        status = ImportStatus.PROCESSING;
    }

    public void extracted(DocType type, String json, String warningsJson, String model, Integer in, Integer out,
                          Instant at) {
        requireStatus(ImportStatus.PROCESSING);
        this.docType = type;
        this.extracted = json;
        this.warnings = warningsJson;
        this.model = model;
        this.inputTokens = in;
        this.outputTokens = out;
        this.error = null;
        this.processedAt = at;
        this.status = ImportStatus.EXTRACTED;
    }

    /**
     * A failure that may pass (service busy, network) is queued again until it has been tried
     * {@link #MAX_ATTEMPTS} times; one that will not (refusal, document too long, bad key) fails at once.
     */
    public void failed(String message, boolean retryable, Instant at) {
        requireStatus(ImportStatus.PROCESSING);
        this.error = message == null ? "Unknown error" : message.length() > 2000 ? message.substring(0, 2000) : message;
        this.processedAt = at;
        this.status = retryable && attempts < MAX_ATTEMPTS ? ImportStatus.QUEUED : ImportStatus.FAILED;
    }

    /** True while the worker has it; a person cannot reject or confirm it then. */
    public boolean isBeingRead() {
        return status == ImportStatus.PROCESSING;
    }

    /** Back in the queue: after a failure, to read again, or when the worker died mid-way. */
    public void requeue() {
        if (!status.isOpen()) {
            throw new IllegalStateException("Document " + fileName + " is already " + status);
        }
        this.status = ImportStatus.QUEUED;
        this.attempts = 0;
        this.error = null;
    }

    public void confirm(DocType type, String json, UUID dealId, String resultType, UUID resultId, Instant at, String by) {
        if (!status.isOpen() || status == ImportStatus.PROCESSING) {
            throw new IllegalStateException("Document " + fileName + " is " + status + " and cannot be confirmed");
        }
        this.docType = type;
        this.extracted = json;
        this.dealId = dealId;
        this.resultType = resultType;
        this.resultId = resultId;
        this.decidedAt = at;
        this.decidedBy = by;
        this.status = ImportStatus.CONFIRMED;
    }

    public void reject(String reason, Instant at, String by) {
        if (status == ImportStatus.PROCESSING) {
            throw new IllegalStateException("The document is being read right now; reject it when reading has finished");
        }
        if (!status.isOpen()) {
            throw new IllegalStateException("Document " + fileName + " is already " + status);
        }
        this.error = reason;
        this.decidedAt = at;
        this.decidedBy = by;
        this.status = ImportStatus.REJECTED;
    }

    private void requireStatus(ImportStatus s) {
        if (status != s) {
            throw new IllegalStateException("Document " + fileName + " is " + status + ", expected " + s);
        }
    }

    public UUID getId() { return id; }
    public UUID getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getSha256() { return sha256; }
    public ImportStatus getStatus() { return status; }
    public DocType getDocType() { return docType; }
    public String getExtracted() { return extracted; }
    public String getWarnings() { return warnings; }
    public String getError() { return error; }
    public String getModel() { return model; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public int getAttempts() { return attempts; }
    public UUID getDealId() { return dealId; }
    public String getResultType() { return resultType; }
    public UUID getResultId() { return resultId; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
}
