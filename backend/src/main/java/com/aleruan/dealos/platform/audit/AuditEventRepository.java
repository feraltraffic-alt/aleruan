package com.aleruan.dealos.platform.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderBySeqDesc();

    List<AuditEvent> findAllByOrderBySeqAsc();

    List<AuditEvent> findByEntityTypeAndEntityIdOrderBySeqAsc(String entityType, String entityId);
}
