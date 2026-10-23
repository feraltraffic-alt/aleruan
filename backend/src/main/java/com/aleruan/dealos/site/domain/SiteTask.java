package com.aleruan.dealos.site.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** One bar on the site schedule: a piece of work, who does it and when. Dates are inclusive. */
@Entity
@Table(name = "site_task")
public class SiteTask {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(name = "work_type")
    private String workType;

    @Column(name = "deal_line_id")
    private UUID dealLineId;

    @Column(name = "subcontractor_id")
    private UUID subcontractorId;

    @Column(name = "start_date", nullable = false)
    private LocalDate start;

    @Column(name = "end_date", nullable = false)
    private LocalDate end;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct;

    protected SiteTask() {
    }

    SiteTask(Site site, int position, String title, String workType, UUID dealLineId, UUID subcontractorId,
             LocalDate start, LocalDate end, int progressPct) {
        this.id = UUID.randomUUID();
        this.site = site;
        this.position = position;
        this.dealLineId = dealLineId;
        update(title, workType, subcontractorId, start, end, progressPct);
    }

    void update(String title, String workType, UUID subcontractorId, LocalDate start, LocalDate end, int progressPct) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A task needs a title");
        }
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("A task ends on or after its start");
        }
        if (progressPct < 0 || progressPct > 100) {
            throw new IllegalArgumentException("Progress is 0 to 100 %");
        }
        this.title = title.strip();
        this.workType = workType == null || workType.isBlank() ? null : workType;
        this.subcontractorId = subcontractorId;
        this.start = start;
        this.end = end;
        this.progressPct = progressPct;
    }

    /** Calendar days the task spans, both ends included. */
    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Not finished although its end date has passed. */
    public boolean isLate(LocalDate today) {
        return progressPct < 100 && end.isBefore(today);
    }

    public UUID getId() { return id; }
    public Site getSite() { return site; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public String getWorkType() { return workType; }
    public UUID getDealLineId() { return dealLineId; }
    public UUID getSubcontractorId() { return subcontractorId; }
    public LocalDate getStart() { return start; }
    public LocalDate getEnd() { return end; }
    public int getProgressPct() { return progressPct; }

    @Override
    public boolean equals(Object o) {
        return o instanceof SiteTask t && id.equals(t.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
