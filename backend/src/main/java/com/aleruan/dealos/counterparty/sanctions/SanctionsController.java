package com.aleruan.dealos.counterparty.sanctions;

import java.time.Instant;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sanctions")
class SanctionsController {

    record Status(boolean configured, Instant lastImportAt, Integer entities, Integer aliases, String fileSha256) {
    }

    private final SanctionsService service;
    private final SanctionsProperties properties;

    SanctionsController(SanctionsService service, SanctionsProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/status")
    Status status() {
        Optional<SanctionImport> last = service.lastImport();
        return new Status(properties.enabled(), last.map(SanctionImport::getImportedAt).orElse(null),
                last.map(SanctionImport::getEntities).orElse(null), last.map(SanctionImport::getAliases).orElse(null),
                last.map(SanctionImport::getFileSha256).orElse(null));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Status> importNow() throws Exception {
        service.importFromUrl();
        return ResponseEntity.ok(status());
    }
}
