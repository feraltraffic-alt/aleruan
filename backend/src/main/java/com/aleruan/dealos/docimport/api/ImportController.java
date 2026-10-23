package com.aleruan.dealos.docimport.api;

import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.docimport.application.ImportChecks;
import com.aleruan.dealos.docimport.application.ImportService;
import com.aleruan.dealos.docimport.domain.DocType;
import com.aleruan.dealos.docimport.domain.ImportDocument;
import com.aleruan.dealos.docimport.domain.ImportStatus;
import com.aleruan.dealos.docimport.reader.Extraction;
import com.aleruan.dealos.platform.files.FileStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
class ImportController {

    record DocumentView(UUID id, String fileName, String contentType, ImportStatus status, DocType docType,
                        String summary, String documentNumber, String partyName, String totalAmount, String currency,
                        String error, String model, Integer inputTokens, Integer outputTokens, int attempts,
                        UUID dealId, String resultType, UUID resultId, Instant uploadedAt, String uploadedBy,
                        Instant processedAt, Instant decidedAt, String decidedBy) {
    }

    record UploadView(DocumentView document, boolean duplicate) {
    }

    record SuggestionView(DocType type, Extraction.Party party, String role, CounterpartySummary match,
                          boolean duplicateInvoice) {
    }

    record DetailView(DocumentView document, Extraction read, ImportService.Review confirmed,
                      List<ImportChecks.Warning> warnings, SuggestionView suggestion) {
    }

    record RejectRequest(String reason) {
    }

    private final ImportService service;

    ImportController(ImportService service) {
        this.service = service;
    }

    @GetMapping("/queue")
    ImportService.Queue queue() {
        return service.queue();
    }

    @GetMapping
    List<DocumentView> list(@RequestParam(required = false) ImportStatus status) {
        return service.list(status).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UploadView upload(@RequestParam("file") MultipartFile file) throws IOException {
        ImportService.Upload u = service.upload(file.getOriginalFilename(), file.getBytes());
        return new UploadView(view(u.document()), u.duplicate());
    }

    @GetMapping("/{id}")
    DetailView detail(@PathVariable UUID id) {
        ImportService.Detail d = service.detail(id);
        ImportService.Suggestion s = d.suggestion();
        return new DetailView(view(d.document()), d.read(), d.confirmed(), d.warnings(),
                new SuggestionView(s.type(), s.party(), s.role(), s.match(), s.duplicateInvoice()));
    }

    @GetMapping("/{id}/file")
    ResponseEntity<byte[]> file(@PathVariable UUID id) {
        FileStore.Content c = service.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(c.info().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
                .body(c.data());
    }

    @PostMapping("/{id}/retry")
    DocumentView retry(@PathVariable UUID id) {
        return view(service.retry(id));
    }

    @PostMapping("/{id}/reject")
    DocumentView reject(@PathVariable UUID id, @RequestBody(required = false) RejectRequest r) {
        return view(service.reject(id, r == null ? null : r.reason()));
    }

    @PostMapping("/{id}/confirm")
    ImportService.Outcome confirm(@PathVariable UUID id, @RequestBody ImportService.Review review) {
        return service.confirm(id, review);
    }

    private DocumentView view(ImportDocument d) {
        Extraction e = service.readOf(d);
        String party = null;
        if (e != null) {
            Extraction.Party p = "CUSTOMER_INVOICE".equals(e.documentType()) ? e.recipient() : e.issuer();
            party = p.name().isBlank() ? null : p.name();
        }
        return new DocumentView(d.getId(), d.getFileName(), d.getContentType(), d.getStatus(), d.getDocType(),
                e == null ? null : blank(e.summary()), e == null ? null : blank(e.documentNumber()), party,
                e == null ? null : blank(e.totalAmount()), e == null ? null : blank(e.currency()), d.getError(),
                d.getModel(), d.getInputTokens(), d.getOutputTokens(), d.getAttempts(), d.getDealId(), d.getResultType(),
                d.getResultId(), d.getUploadedAt(), d.getUploadedBy(), d.getProcessedAt(), d.getDecidedAt(),
                d.getDecidedBy());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
