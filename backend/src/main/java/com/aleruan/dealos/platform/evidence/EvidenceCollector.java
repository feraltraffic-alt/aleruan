package com.aleruan.dealos.platform.evidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gathers what the contributors hand in for one deal. Not thread-safe; one per package. */
public final class EvidenceCollector {

    /** An entity whose audit history belongs in the package's audit extract. */
    public record AuditRef(String entityType, String entityId) {
    }

    private final List<EvidenceEntry> entries = new ArrayList<>();
    private final List<EvidenceCheck> checks = new ArrayList<>();
    private final Set<AuditRef> auditRefs = new LinkedHashSet<>();

    public EvidenceCollector entry(EvidenceEntry entry) {
        entries.add(entry);
        return this;
    }

    public EvidenceCollector check(EvidenceCheck check) {
        checks.add(check);
        return this;
    }

    public EvidenceCollector ok(String code, Map<String, String> params) {
        return check(EvidenceCheck.ok(code, params));
    }

    public EvidenceCollector warn(String code, Map<String, String> params) {
        return check(EvidenceCheck.warn(code, params));
    }

    public EvidenceCollector missing(String code, Map<String, String> params) {
        return check(EvidenceCheck.missing(code, params));
    }

    public EvidenceCollector audit(String entityType, UUID entityId) {
        auditRefs.add(new AuditRef(entityType, entityId.toString()));
        return this;
    }

    public EvidenceCollector audit(String entityType, String entityId) {
        auditRefs.add(new AuditRef(entityType, entityId));
        return this;
    }

    public List<EvidenceEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<EvidenceCheck> checks() {
        return Collections.unmodifiableList(checks);
    }

    public Set<AuditRef> auditRefs() {
        return Collections.unmodifiableSet(auditRefs);
    }
}
