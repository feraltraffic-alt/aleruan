package com.aleruan.dealos.site.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of the site diary (stavební deník). The author may fix it for a day; after that it is
 * locked and mistakes are corrected by a new entry that points to the old one, like in a paper diary.
 */
@Entity
@Table(name = "diary_entry")
public class DiaryEntry {

    public static final Duration EDIT_WINDOW = Duration.ofHours(24);

    @Id
    private UUID id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    private String weather;
    private Integer workers;

    @Column(nullable = false)
    private String text;

    @Column(name = "corrects_id", updatable = false)
    private UUID correctsId;

    @Column(updatable = false)
    private String author;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Version
    private long version;

    protected DiaryEntry() {
    }

    public DiaryEntry(UUID siteId, LocalDate entryDate, String weather, Integer workers, String text, UUID correctsId,
                      String author, Instant createdAt, LocalDate today) {
        this.id = UUID.randomUUID();
        this.siteId = siteId;
        this.correctsId = correctsId;
        this.author = author;
        this.createdAt = createdAt;
        apply(entryDate, weather, workers, text, today);
    }

    public boolean editableBy(String actor, Instant now) {
        return author != null && author.equals(actor) && now.isBefore(createdAt.plus(EDIT_WINDOW));
    }

    public void edit(LocalDate entryDate, String weather, Integer workers, String text, String actor, Instant now,
                     LocalDate today) {
        if (!editableBy(actor, now)) {
            throw new IllegalStateException("Diary entries can be changed by their author for 24 hours; add a correction instead");
        }
        apply(entryDate, weather, workers, text, today);
        this.editedAt = now;
    }

    private void apply(LocalDate entryDate, String weather, Integer workers, String text, LocalDate today) {
        if (entryDate == null || entryDate.isAfter(today)) {
            throw new IllegalArgumentException("A diary entry is about today or an earlier day");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Write what happened on site");
        }
        if (workers != null && (workers < 0 || workers > 1000)) {
            throw new IllegalArgumentException("Number of workers is 0 to 1000");
        }
        this.entryDate = entryDate;
        this.weather = weather == null || weather.isBlank() ? null : weather.strip();
        this.workers = workers;
        this.text = text.strip();
    }

    public UUID getId() { return id; }
    public UUID getSiteId() { return siteId; }
    public LocalDate getEntryDate() { return entryDate; }
    public String getWeather() { return weather; }
    public Integer getWorkers() { return workers; }
    public String getText() { return text; }
    public UUID getCorrectsId() { return correctsId; }
    public String getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
}
