package com.aleruan.dealos.site.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.site.application.SiteRecordsService;
import com.aleruan.dealos.site.application.SiteService;
import com.aleruan.dealos.site.domain.Defect;
import com.aleruan.dealos.site.domain.DefectSeverity;
import com.aleruan.dealos.site.domain.DefectStatus;
import com.aleruan.dealos.site.domain.DiaryEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
class SiteRecordsController {

    record FileView(UUID id, String fileName, String contentType, int sizeBytes, Instant uploadedAt, String uploadedBy) {
        static FileView of(FileInfo f) {
            return new FileView(f.id(), f.fileName(), f.contentType(), f.sizeBytes(), f.uploadedAt(), f.uploadedBy());
        }
    }

    record DiaryBody(@NotNull LocalDate entryDate, @Size(max = 200) String weather, @Min(0) @Max(1000) Integer workers,
                     @NotBlank @Size(max = 10000) String text, UUID correctsId) {
        SiteRecordsService.DiaryData data() {
            return new SiteRecordsService.DiaryData(entryDate, weather, workers, text, correctsId);
        }
    }

    record DiaryView(UUID id, LocalDate entryDate, String weather, Integer workers, String text, UUID correctsId,
                     List<UUID> correctedBy, String author, Instant createdAt, Instant editedAt, boolean editable,
                     List<FileView> photos) {
    }

    record DefectBody(@NotBlank @Size(max = 300) String title, @Size(max = 5000) String description,
                      @Size(max = 300) String location, DefectSeverity severity, UUID subcontractorId, UUID stageId,
                      @NotNull LocalDate foundOn, LocalDate dueDate) {
        Defect.Details details() {
            return new Defect.Details(title, description, location, severity, subcontractorId, stageId, foundOn, dueDate);
        }
    }

    record MoveBody(@NotNull DefectStatus status, @Size(max = 2000) String note) {
    }

    record PartyView(UUID id, String legalName, String checkStatus) {
    }

    record DefectView(UUID id, UUID siteId, String siteName, int seq, String title, String description, String location,
                      DefectSeverity severity, PartyView subcontractor, UUID stageId, LocalDate foundOn,
                      LocalDate dueDate, boolean warranty, DefectStatus status, Set<DefectStatus> nextStatuses,
                      boolean overdue, Instant fixedAt, String fixNote, Instant closedAt, String closedBy,
                      Instant createdAt, String createdBy, List<FileView> photos) {
    }

    private final SiteRecordsService records;
    private final SiteService sites;
    private final CounterpartyLookup counterparties;

    SiteRecordsController(SiteRecordsService records, SiteService sites, CounterpartyLookup counterparties) {
        this.records = records;
        this.sites = sites;
        this.counterparties = counterparties;
    }

    // ---- diary ----

    @GetMapping("/sites/{siteId}/diary")
    List<DiaryView> diary(@PathVariable UUID siteId) {
        List<DiaryEntry> entries = records.diary(siteId);
        Map<UUID, List<FileInfo>> photos = records.photos(SiteRecordsService.DIARY, entries.stream().map(DiaryEntry::getId).toList());
        Map<UUID, List<UUID>> corrections = entries.stream().filter(e -> e.getCorrectsId() != null)
                .collect(Collectors.groupingBy(DiaryEntry::getCorrectsId, Collectors.mapping(DiaryEntry::getId, Collectors.toList())));
        return entries.stream().map(e -> view(e, corrections.getOrDefault(e.getId(), List.of()),
                photos.getOrDefault(e.getId(), List.of()))).toList();
    }

    @PostMapping("/sites/{siteId}/diary")
    DiaryView addEntry(@PathVariable UUID siteId, @Validated @RequestBody DiaryBody body) {
        return view(records.addEntry(siteId, body.data()), List.of(), List.of());
    }

    @PutMapping("/diary/{id}")
    DiaryView editEntry(@PathVariable UUID id, @Validated @RequestBody DiaryBody body) {
        DiaryEntry e = records.editEntry(id, body.data());
        return view(e, List.of(), records.photos(SiteRecordsService.DIARY, List.of(id)).getOrDefault(id, List.of()));
    }

    @PostMapping("/diary/{id}/photos")
    FileView diaryPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        return FileView.of(records.attach(SiteRecordsService.DIARY, id, file.getOriginalFilename(), file.getBytes()));
    }

    @DeleteMapping("/diary/{id}/photos/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeDiaryPhoto(@PathVariable UUID id, @PathVariable UUID fileId) {
        records.detach(SiteRecordsService.DIARY, id, fileId);
    }

    // ---- defects ----

    @GetMapping("/sites/{siteId}/defects")
    List<DefectView> defects(@PathVariable UUID siteId) {
        return views(records.defects(siteId));
    }

    /** Unresolved defects on all sites, for the defects overview. */
    @GetMapping("/defects")
    List<DefectView> unresolved() {
        return views(records.unresolved());
    }

    @PostMapping("/sites/{siteId}/defects")
    DefectView report(@PathVariable UUID siteId, @Validated @RequestBody DefectBody body) {
        return views(List.of(records.report(siteId, body.details()))).get(0);
    }

    @PutMapping("/defects/{id}")
    DefectView edit(@PathVariable UUID id, @Validated @RequestBody DefectBody body) {
        return views(List.of(records.edit(id, body.details()))).get(0);
    }

    @PostMapping("/defects/{id}/status")
    DefectView move(@PathVariable UUID id, @Validated @RequestBody MoveBody body) {
        return views(List.of(records.move(id, body.status(), body.note()))).get(0);
    }

    @PostMapping("/defects/{id}/photos")
    FileView defectPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        return FileView.of(records.attach(SiteRecordsService.DEFECT, id, file.getOriginalFilename(), file.getBytes()));
    }

    @DeleteMapping("/defects/{id}/photos/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeDefectPhoto(@PathVariable UUID id, @PathVariable UUID fileId) {
        records.detach(SiteRecordsService.DEFECT, id, fileId);
    }

    private DiaryView view(DiaryEntry e, List<UUID> correctedBy, List<FileInfo> photos) {
        return new DiaryView(e.getId(), e.getEntryDate(), e.getWeather(), e.getWorkers(), e.getText(), e.getCorrectsId(),
                correctedBy, e.getAuthor(), e.getCreatedAt(), e.getEditedAt(), records.editable(e),
                photos.stream().map(FileView::of).toList());
    }

    private List<DefectView> views(List<Defect> list) {
        Map<UUID, List<FileInfo>> photos = records.photos(SiteRecordsService.DEFECT, list.stream().map(Defect::getId).toList());
        Map<UUID, String> siteNames = new HashMap<>();
        Map<UUID, CounterpartySummary> parties = new HashMap<>();
        LocalDate today = records.today();
        return list.stream().map(d -> {
            String siteName = siteNames.computeIfAbsent(d.getSiteId(), id -> sites.get(id).getName());
            CounterpartySummary sub = d.getSubcontractorId() == null ? null
                    : parties.computeIfAbsent(d.getSubcontractorId(), id -> counterparties.find(id).orElse(null));
            return new DefectView(d.getId(), d.getSiteId(), siteName, d.getSeq(), d.getTitle(), d.getDescription(),
                    d.getLocation(), d.getSeverity(),
                    sub == null ? null : new PartyView(sub.id(), sub.legalName(), sub.checkStatus()), d.getStageId(),
                    d.getFoundOn(), d.getDueDate(), d.isWarranty(), d.getStatus(), d.getStatus().next(),
                    d.isOverdue(today), d.getFixedAt(), d.getFixNote(), d.getClosedAt(), d.getClosedBy(),
                    d.getCreatedAt(), d.getCreatedBy(),
                    photos.getOrDefault(d.getId(), List.of()).stream().map(FileView::of).toList());
        }).toList();
    }
}
