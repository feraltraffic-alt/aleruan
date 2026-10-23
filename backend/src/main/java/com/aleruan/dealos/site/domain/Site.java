package com.aleruan.dealos.site.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A construction or service site belonging to one deal. */
@Entity
@Table(name = "site")
public class Site {

    private static final BigDecimal LAT = BigDecimal.valueOf(90);
    private static final BigDecimal LNG = BigDecimal.valueOf(180);

    @Id
    private UUID id;

    @Column(name = "deal_id", nullable = false, updatable = false)
    private UUID dealId;

    @Column(nullable = false)
    private String name;

    private String address;
    private String city;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "project_manager")
    private String projectManager;

    private String foreman;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_end")
    private LocalDate plannedEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteStatus status = SiteStatus.PLANNED;

    private String notes;

    @Column(name = "handover_date")
    private LocalDate handoverDate;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<SiteTask> tasks = new ArrayList<>();

    protected Site() {
    }

    public Site(UUID dealId, String name, String country, Instant createdAt, String createdBy) {
        this.id = UUID.randomUUID();
        this.dealId = dealId;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.name = requireName(name);
        this.country = normaliseCountry(country);
    }

    public record Details(String name, String address, String city, String country, BigDecimal latitude,
                          BigDecimal longitude, String projectManager, String foreman, LocalDate plannedStart,
                          LocalDate plannedEnd, SiteStatus status, String notes) {
    }

    public void edit(Details d) {
        if ((d.latitude() == null) != (d.longitude() == null)) {
            throw new IllegalArgumentException("Give both latitude and longitude, or neither");
        }
        if (d.latitude() != null && (d.latitude().abs().compareTo(LAT) > 0 || d.longitude().abs().compareTo(LNG) > 0)) {
            throw new IllegalArgumentException("Coordinates are out of range");
        }
        if (d.plannedStart() != null && d.plannedEnd() != null && d.plannedEnd().isBefore(d.plannedStart())) {
            throw new IllegalArgumentException("The site ends on or after its start");
        }
        this.name = requireName(d.name());
        this.address = blank(d.address());
        this.city = blank(d.city());
        this.country = normaliseCountry(d.country());
        this.latitude = d.latitude();
        this.longitude = d.longitude();
        this.projectManager = blank(d.projectManager());
        this.foreman = blank(d.foreman());
        this.plannedStart = d.plannedStart();
        this.plannedEnd = d.plannedEnd();
        this.status = d.status() == null ? status : d.status();
        this.notes = blank(d.notes());
    }

    /** The day the whole site was handed over; warranty runs from then. */
    public void setHandover(LocalDate handoverDate, Integer warrantyMonths) {
        if (warrantyMonths != null && (warrantyMonths < 0 || warrantyMonths > 120)) {
            throw new IllegalArgumentException("Warranty is 0 to 120 months");
        }
        if (handoverDate != null && plannedStart != null && handoverDate.isBefore(plannedStart)) {
            throw new IllegalArgumentException("Handover cannot be before the site started");
        }
        this.handoverDate = handoverDate;
        this.warrantyMonths = handoverDate == null ? null : warrantyMonths;
    }

    /** Last day of the warranty, or null while the site is not handed over. */
    public LocalDate warrantyUntil() {
        return handoverDate == null || warrantyMonths == null ? null : handoverDate.plusMonths(warrantyMonths).minusDays(1);
    }

    public SiteTask addTask(String title, String workType, UUID dealLineId, UUID subcontractorId, LocalDate start,
                            LocalDate end, int progressPct) {
        int next = tasks.stream().mapToInt(SiteTask::getPosition).max().orElse(0) + 1;
        SiteTask task = new SiteTask(this, next, title, workType, dealLineId, subcontractorId, start, end, progressPct);
        tasks.add(task);
        return task;
    }

    public SiteTask updateTask(UUID taskId, String title, String workType, UUID subcontractorId, LocalDate start,
                               LocalDate end, int progressPct) {
        SiteTask task = task(taskId);
        task.update(title, workType, subcontractorId, start, end, progressPct);
        return task;
    }

    public void removeTask(UUID taskId) {
        tasks.remove(task(taskId));
    }

    public SiteTask task(UUID taskId) {
        return tasks.stream().filter(t -> t.getId().equals(taskId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " is not on site " + name));
    }

    /** Progress weighted by task length, so a two-day job does not count as much as a two-month one. */
    public int progressPct() {
        long days = tasks.stream().mapToLong(SiteTask::days).sum();
        if (days == 0) {
            return status == SiteStatus.DONE ? 100 : 0;
        }
        long done = tasks.stream().mapToLong(t -> t.days() * t.getProgressPct()).sum();
        return (int) Math.round((double) done / days);
    }

    public long lateTasks(LocalDate today) {
        return tasks.stream().filter(t -> t.isLate(today)).count();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A site needs a name");
        }
        return name.strip();
    }

    private static String normaliseCountry(String country) {
        String c = blank(country);
        if (c != null && !c.matches("[A-Za-z]{2}")) {
            throw new IllegalArgumentException("Country is a two-letter code");
        }
        return c == null ? null : c.toUpperCase(Locale.ROOT);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getProjectManager() { return projectManager; }
    public String getForeman() { return foreman; }
    public LocalDate getPlannedStart() { return plannedStart; }
    public LocalDate getPlannedEnd() { return plannedEnd; }
    public SiteStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public LocalDate getHandoverDate() { return handoverDate; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public List<SiteTask> getTasks() { return Collections.unmodifiableList(tasks); }
}
