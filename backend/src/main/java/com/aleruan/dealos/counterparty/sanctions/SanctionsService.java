package com.aleruan.dealos.counterparty.sanctions;

import com.aleruan.dealos.counterparty.domain.Names;
import com.aleruan.dealos.platform.audit.AuditService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/** Imports the EU sanctions list daily and answers "does this name appear on it?". */
@Service
public class SanctionsService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsService.class);

    public record Match(String logicalId, String subjectType, String listedName) {
    }

    private final SanctionAliasRepository aliases;
    private final SanctionImportRepository imports;
    private final SanctionsProperties properties;
    private final RestClient.Builder http;
    private final AuditService audit;
    private final Clock clock;

    SanctionsService(SanctionAliasRepository aliases, SanctionImportRepository imports, SanctionsProperties properties,
                     RestClient.Builder http, AuditService audit, Clock clock) {
        this.aliases = aliases;
        this.imports = imports;
        this.properties = properties;
        this.http = http;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(cron = "${dealos.sanctions.cron:0 20 4 * * *}", zone = "Europe/Prague")
    public void scheduledImport() {
        if (!properties.enabled()) {
            return;
        }
        try {
            importFromUrl();
        } catch (Exception e) {
            log.error("EU sanctions import failed; the previous list stays in use", e);
        }
    }

    /** Downloads and imports the list; skips the import if the file did not change. */
    @Transactional
    public Optional<SanctionImport> importFromUrl() throws Exception {
        if (!properties.enabled()) {
            throw new IllegalStateException("dealos.sanctions.url is not configured");
        }
        byte[] file = http.clone().build().get().uri(java.net.URI.create(properties.url())).retrieve().body(byte[].class);
        return importFile(file);
    }

    @Transactional
    public Optional<SanctionImport> importFile(byte[] file) throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
        if (imports.findTopByOrderByImportedAtDesc().map(i -> i.getFileSha256().equals(sha)).orElse(false)) {
            return Optional.empty();
        }
        List<SanctionedName> names = SanctionsXmlParser.parse(new ByteArrayInputStream(file));
        if (names.isEmpty()) {
            throw new IllegalStateException("Sanctions file contains no names; refusing to replace the current list");
        }
        aliases.deleteAllInBulk();
        aliases.saveAll(names.stream()
                .map(n -> new SanctionAlias(n.logicalId(), n.subjectType(), n.wholeName(), Names.forMatching(n.wholeName())))
                .filter(a -> !Names.forMatching(a.getWholeName()).isBlank())
                .toList());
        long entities = names.stream().map(SanctionedName::logicalId).distinct().count();
        SanctionImport saved = imports.save(new SanctionImport(clock.instant(), (int) entities, names.size(), sha));
        audit.record("SanctionList", sha, "IMPORTED", Map.of("entities", entities, "aliases", names.size()));
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SanctionImport> lastImport() {
        return imports.findTopByOrderByImportedAtDesc();
    }

    /** Exact match on the normalised name (legal forms and punctuation ignored). */
    @Transactional(readOnly = true)
    public List<Match> findByName(String legalName) {
        String key = Names.forMatching(legalName);
        if (key.isBlank()) {
            return List.of();
        }
        return aliases.findByNormalizedName(key).stream()
                .map(a -> new Match(a.getLogicalId(), a.getSubjectType(), a.getWholeName()))
                .toList();
    }
}
