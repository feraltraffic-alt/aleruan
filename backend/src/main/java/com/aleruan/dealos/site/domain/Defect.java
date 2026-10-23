package com.aleruan.dealos.site.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A defect found on a site, during works, at acceptance or under warranty, and who has to fix it. */
@Entity
@Table(name = "defect")
public class Defect {

    @Id
    private UUID id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false)
    private String title;

    private String description;
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectSeverity severity;

    @Column(name = "subcontractor_id")
    private UUID subcontractorId;

    @Column(name = "stage_id")
    private UUID stageId;

    @Column(name = "found_on", nullable = false)
    private LocalDate foundOn;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean warranty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus status = DefectStatus.OPEN;

    @Column(name = "fixed_at")
    private Instant fixedAt;

    @Column(name = "fix_note")
    private String fixNote;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Version
    private long version;

    protected Defect() {
    }

    public record Details(String title, String description, String location, DefectSeverity severity,
                          UUID subcontractorId, UUID stageId, LocalDate foundOn, LocalDate dueDate) {
    }

    /**
     * @param handover the site's handover date: a defect found after it is a warranty claim
     */
    public Defect(UUID siteId, int seq, Details d, LocalDate handover, Instant createdAt, String createdBy) {
        this.id = UUID.randomUUID();
        this.siteId = siteId;
        this.seq = seq;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        edit(d, handover);
    }

    public void edit(Details d, LocalDate handover) {
        if (d.title() == null || d.title().isBlank()) {
            throw new IllegalArgumentException("Name the defect");
        }
        if (d.foundOn() == null) {
            throw new IllegalArgumentException("When was it found?");
        }
        if (d.dueDate() != null && d.dueDate().isBefore(d.foundOn())) {
            throw new IllegalArgumentException("The fix is due on or after the day the defect was found");
        }
        this.title = d.title().strip();
        this.description = blank(d.description());
        this.location = blank(d.location());
        this.severity = d.severity() == null ? DefectSeverity.MINOR : d.severity();
        this.subcontractorId = d.subcontractorId();
        this.stageId = d.stageId();
        this.foundOn = d.foundOn();
        this.dueDate = d.dueDate() != null ? d.dueDate() : d.foundOn().plusDays(this.severity.defaultDays());
        this.warranty = handover != null && d.foundOn().isAfter(handover);
    }

    public void moveTo(DefectStatus target, String note, String actor, Instant now) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Defect #" + seq + " cannot move from " + status + " to " + target);
        }
        if ((target == DefectStatus.DISMISSED || (status == DefectStatus.FIXED && target == DefectStatus.OPEN))
                && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Say why");
        }
        switch (target) {
            case FIXED -> {
                fixedAt = now;
                fixNote = blank(note);
            }
            case CLOSED, DISMISSED -> {
                closedAt = now;
                closedBy = actor;
                if (target == DefectStatus.DISMISSED) {
                    fixNote = blank(note);
                }
            }
            case OPEN -> {
                closedAt = null;
                closedBy = null;
                fixedAt = null;
                fixNote = blank(note);
            }
        }
        this.status = target;
    }

    public boolean isOverdue(LocalDate today) {
        return status == DefectStatus.OPEN && dueDate != null && dueDate.isBefore(today);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public UUID getId() { return id; }
    public UUID getSiteId() { return siteId; }
    public int getSeq() { return seq; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public DefectSeverity getSeverity() { return severity; }
    public UUID getSubcontractorId() { return subcontractorId; }
    public UUID getStageId() { return stageId; }
    public LocalDate getFoundOn() { return foundOn; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isWarranty() { return warranty; }
    public DefectStatus getStatus() { return status; }
    public Instant getFixedAt() { return fixedAt; }
    public String getFixNote() { return fixNote; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosedBy() { return closedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
}
