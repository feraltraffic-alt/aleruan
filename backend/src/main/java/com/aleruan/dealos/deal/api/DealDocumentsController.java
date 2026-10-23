package com.aleruan.dealos.deal.api;

import com.aleruan.dealos.deal.application.DealDocuments;
import com.aleruan.dealos.platform.files.FileInfo;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Contract and other papers of a deal. Served here (not via /api/files) so only deal users can open them. */
@RestController
@RequestMapping("/api/deals/{dealId}/documents")
class DealDocumentsController {

    record DocumentView(UUID id, String fileName, String contentType, int sizeBytes, String sha256, Instant uploadedAt,
                        String uploadedBy) {
        static DocumentView of(FileInfo f) {
            return new DocumentView(f.id(), f.fileName(), f.contentType(), f.sizeBytes(), f.sha256(), f.uploadedAt(),
                    f.uploadedBy());
        }
    }

    private final DealDocuments documents;

    DealDocumentsController(DealDocuments documents) {
        this.documents = documents;
    }

    @GetMapping
    List<DocumentView> list(@PathVariable UUID dealId) {
        return documents.list(dealId).stream().map(DocumentView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DocumentView upload(@PathVariable UUID dealId, @RequestParam("file") MultipartFile file) throws IOException {
        return DocumentView.of(documents.attach(dealId, file.getOriginalFilename(), file.getBytes()));
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID dealId, @PathVariable UUID fileId) {
        documents.remove(dealId, fileId);
    }

    @GetMapping("/{fileId}")
    ResponseEntity<byte[]> get(@PathVariable UUID dealId, @PathVariable UUID fileId) {
        FileStore.Content c = documents.get(dealId, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(c.info().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
                .body(c.data());
    }
}
