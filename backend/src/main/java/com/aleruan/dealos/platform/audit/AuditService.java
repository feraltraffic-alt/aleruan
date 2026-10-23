package com.aleruan.dealos.platform.audit;

import com.aleruan.dealos.platform.security.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the audit log. Every business change calls {@link #record} inside its own
 * transaction, so the audit entry is committed together with the change or not at all.
 */
@Service
public class AuditService {

    /** Arbitrary constant key for the Postgres advisory lock that serialises chain appends. */
    private static final long CHAIN_LOCK_KEY = 7_245_001L;

    private final AuditEventRepository repository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final CurrentActor currentActor;
    private final Clock clock;

    AuditService(AuditEventRepository repository, EntityManager entityManager, ObjectMapper objectMapper,
                 CurrentActor currentActor, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public AuditRecord record(String entityType, String entityId, String action, Object payload) {
        // One writer at a time: the next link needs the hash of the latest one.
        entityManager.createNativeQuery("select 1 from (select pg_advisory_xact_lock(:key)) l")
                .setParameter("key", CHAIN_LOCK_KEY)
                .getSingleResult();

        AuditRecord previous = repository.findTopByOrderBySeqDesc().map(AuditEvent::toRecord).orElse(null);
        AuditRecord next = AuditRecord.next(previous,
                clock.instant().truncatedTo(ChronoUnit.MICROS), // Postgres keeps microseconds
                currentActor.name(), entityType, entityId, action, toJson(payload));
        repository.save(AuditEvent.from(next));
        return next;
    }

    @Transactional(readOnly = true)
    public ChainVerification verifyChain() {
        return AuditHasher.verify(repository.findAllByOrderBySeqAsc().stream().map(AuditEvent::toRecord).toList());
    }

    @Transactional(readOnly = true)
    public List<AuditRecord> history(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityIdOrderBySeqAsc(entityType, entityId).stream()
                .map(AuditEvent::toRecord).toList();
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit payload is not serialisable", e);
        }
    }
}
