package com.aleruan.dealos.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/** Persistent form of {@link AuditRecord}. The table is append-only (enforced by a DB trigger). */
@Entity
@Table(name = "audit_event")
class AuditEvent implements Persistable<Long> {

    @Id
    private Long seq;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private String actor;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    private String action;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    protected AuditEvent() {
    }

    static AuditEvent from(AuditRecord r) {
        AuditEvent e = new AuditEvent();
        e.seq = r.seq();
        e.occurredAt = r.occurredAt();
        e.actor = r.actor();
        e.entityType = r.entityType();
        e.entityId = r.entityId();
        e.action = r.action();
        e.payload = r.payload();
        e.prevHash = r.prevHash();
        e.hash = r.hash();
        return e;
    }

    AuditRecord toRecord() {
        return new AuditRecord(seq, occurredAt, actor, entityType, entityId, action, payload, prevHash, hash);
    }

    @Override
    public Long getId() {
        return seq;
    }

    /** Always new: audit events are only ever inserted. */
    @Override
    public boolean isNew() {
        return true;
    }
}
