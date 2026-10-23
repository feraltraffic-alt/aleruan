package com.aleruan.dealos.evidence.api;

import com.aleruan.dealos.evidence.application.EvidenceService;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deals/{dealId}/evidence")
class EvidenceController {

    private final EvidenceService service;

    EvidenceController(EvidenceService service) {
        this.service = service;
    }

    /** What the package holds and what it is missing; reads no file contents. */
    @GetMapping
    EvidenceService.Overview overview(@PathVariable UUID dealId) {
        return service.overview(dealId);
    }

    /** Builds the package (recorded in the audit log) and sends it as a ZIP; the temporary file goes afterwards. */
    @PostMapping("/export")
    ResponseEntity<Resource> export(@PathVariable UUID dealId) throws IOException {
        EvidenceService.Export export = service.export(dealId);
        long size = Files.size(export.file());
        InputStream in = new DeletingInputStream(export.file());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(size)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Evidence-Sha256sums", export.sha256sums())
                .body(new InputStreamResource(in));
    }

    /** Removes the temporary archive once the response has been written. */
    private static final class DeletingInputStream extends FilterInputStream {
        private final Path file;

        DeletingInputStream(Path file) throws IOException {
            super(Files.newInputStream(file));
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }
}
