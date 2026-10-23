package com.aleruan.dealos.platform.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
class AuditController {

    private final AuditService auditService;

    AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/verify")
    ChainVerification verify() {
        return auditService.verifyChain();
    }

    @GetMapping("/{entityType}/{entityId}")
    List<AuditRecord> history(@PathVariable String entityType, @PathVariable String entityId) {
        return auditService.history(entityType, entityId);
    }
}
