package com.aleruan.dealos.site.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A part of a site handed over to the customer, with the share of the contract value it unlocks. */
@Entity
@Table(name = "acceptance_stage")
public class AcceptanceStage {

    @Id
    private UUID id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageStatus status = StageStatus.PLANNED;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    private String remarks;

    /** Deadline for fixing the remarks of an acceptance with remarks. */
    @Column(name = "fix_until")
    private LocalDate fixUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "acceptance_stage_task", joinColumns = @JoinColumn(name = "stage_id"))
    @Column(name = "task_id")
    private Set<UUID> taskIds = new HashSet<>();

    protected AcceptanceStage() {
    }

    public AcceptanceStage(UUID siteId, int seq, Instant createdAt, String createdBy) {
        this.id = UUID.randomUUID();
        this.siteId = siteId;
        this.seq = seq;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void edit(String title, String description, BigDecimal amount, LocalDate plannedDate, Set<UUID> taskIds) {
        if (status != StageStatus.PLANNED && status != StageStatus.REJECTED) {
            throw new IllegalStateException("Stage " + seq + " was handed over; withdraw it to change it");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A stage needs a title");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("The stage amount is zero or more");
        }
        this.title = title.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
        this.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        this.plannedDate = plannedDate;
        this.taskIds = new HashSet<>(taskIds == null ? Set.of() : taskIds);
    }

    public void submit(Instant at) {
        move(StageStatus.SUBMITTED);
        this.submittedAt = at;
        this.decidedAt = null;
        this.decidedBy = null;
    }

    public void withdraw() {
        move(StageStatus.PLANNED);
        this.submittedAt = null;
    }

    /** The customer's decision; remarks are required unless the stage is accepted without any. */
    public void decide(StageStatus decision, String by, String remarks, LocalDate fixUntil, Instant at) {
        if (decision != StageStatus.ACCEPTED && decision != StageStatus.ACCEPTED_WITH_REMARKS
                && decision != StageStatus.REJECTED) {
            throw new IllegalArgumentException("A decision accepts or rejects the stage");
        }
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException("Record who decided on the customer's side");
        }
        String r = remarks == null || remarks.isBlank() ? null : remarks.strip();
        if (decision != StageStatus.ACCEPTED && r == null) {
            throw new IllegalArgumentException("Write down the customer's remarks or reasons");
        }
        move(decision);
        this.decidedBy = by.strip();
        this.remarks = r;
        this.fixUntil = decision == StageStatus.ACCEPTED_WITH_REMARKS ? fixUntil : null;
        this.decidedAt = at;
    }

    private void move(StageStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Stage " + seq + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    public String protocolNumber(String dealNumber) {
        return dealNumber + "-P" + seq;
    }

    public UUID getId() { return id; }
    public UUID getSiteId() { return siteId; }
    public int getSeq() { return seq; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public StageStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public String getRemarks() { return remarks; }
    public LocalDate getFixUntil() { return fixUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Set<UUID> getTaskIds() { return Set.copyOf(taskIds); }
}
