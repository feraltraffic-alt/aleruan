package com.aleruan.dealos.site.api;

import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.site.application.AcceptanceService;
import com.aleruan.dealos.site.domain.AcceptanceStage;
import com.aleruan.dealos.site.domain.StageStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api")
class AcceptanceController {

    record StageBody(@NotBlank String title, String description, @NotNull @DecimalMin("0") BigDecimal amount,
                     LocalDate plannedDate, Set<UUID> taskIds) {
        AcceptanceService.StageData data() {
            return new AcceptanceService.StageData(title, description, amount, plannedDate, taskIds);
        }
    }

    record DecisionBody(@NotNull StageStatus decision, @NotBlank String by, String remarks, LocalDate fixUntil) {
    }

    record StageView(UUID id, UUID siteId, int seq, String title, String description, BigDecimal amount,
                     LocalDate plannedDate, StageStatus status, Set<StageStatus> nextStatuses, Instant submittedAt,
                     Instant decidedAt, String decidedBy, String remarks, LocalDate fixUntil, Set<UUID> taskIds) {
        static StageView of(AcceptanceStage s) {
            return new StageView(s.getId(), s.getSiteId(), s.getSeq(), s.getTitle(), s.getDescription(), s.getAmount(),
                    s.getPlannedDate(), s.getStatus(), s.getStatus().next(), s.getSubmittedAt(), s.getDecidedAt(),
                    s.getDecidedBy(), s.getRemarks(), s.getFixUntil(), s.getTaskIds());
        }
    }

    record StagesView(List<StageView> stages, AcceptanceService.Coverage coverage) {
    }

    private final AcceptanceService service;

    AcceptanceController(AcceptanceService service) {
        this.service = service;
    }

    @GetMapping("/sites/{siteId}/stages")
    StagesView list(@PathVariable UUID siteId) {
        return new StagesView(service.list(siteId).stream().map(StageView::of).toList(), service.coverage(siteId));
    }

    @PostMapping("/sites/{siteId}/stages")
    @ResponseStatus(HttpStatus.CREATED)
    StageView create(@PathVariable UUID siteId, @Valid @RequestBody StageBody body) {
        return StageView.of(service.create(siteId, body.data()));
    }

    @PutMapping("/stages/{id}")
    StageView edit(@PathVariable UUID id, @Valid @RequestBody StageBody body) {
        return StageView.of(service.edit(id, body.data()));
    }

    @DeleteMapping("/stages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID id) {
        service.remove(id);
    }

    @PostMapping("/stages/{id}/submit")
    StageView submit(@PathVariable UUID id) {
        return StageView.of(service.submit(id));
    }

    @PostMapping("/stages/{id}/withdraw")
    StageView withdraw(@PathVariable UUID id) {
        return StageView.of(service.withdraw(id));
    }

    @PostMapping("/stages/{id}/decision")
    StageView decide(@PathVariable UUID id, @Valid @RequestBody DecisionBody body) {
        return StageView.of(service.decide(id, new AcceptanceService.Decision(body.decision(), body.by(), body.remarks(),
                body.fixUntil())));
    }

    @GetMapping("/stages/{id}/protocol")
    ResponseEntity<byte[]> protocol(@PathVariable UUID id, @RequestParam(required = false) DocLanguage language) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("protocol.pdf").build().toString())
                .body(service.protocol(id, language));
    }
}
