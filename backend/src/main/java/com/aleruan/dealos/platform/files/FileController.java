package com.aleruan.dealos.platform.files;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FileController {

    private final FileStore store;

    FileController(FileStore store) {
        this.store = store;
    }

    /** Serves a file inline; the type is the detected one and browsers must not guess another. */
    @GetMapping("/api/files/{id}")
    ResponseEntity<byte[]> get(@PathVariable UUID id) {
        FileStore.Content c = store.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(c.info().fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
                .eTag(c.info().sha256())
                .body(c.data());
    }
}
