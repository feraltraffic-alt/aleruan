package com.aleruan.dealos.counterparty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Evidence of one registry check: result, findings, the registry's request id and raw answer. Never updated. */
@Entity
@Table(name = "check_result")
public class CheckResult {

    @Id
    private UUID id;

    @Column(name = "counterparty_id", nullable = false)
    private UUID counterpartyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckStatus status;

    /** Comma-separated {@link CheckFinding} names. */
    private String findings;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "unavailable_reason")
    private String unavailableReason;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected CheckResult() {
    }

    public CheckResult(UUID counterpartyId, RegistrySnapshot snapshot, Collection<CheckFinding> findings) {
        this.id = UUID.randomUUID();
        this.counterpartyId = counterpartyId;
        this.source = snapshot.source();
        this.status = Verdict.of(findings).status();
        this.findings = findings.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.requestId = snapshot.requestId();
        this.unavailableReason = snapshot.unavailableReason();
        this.rawResponse = snapshot.rawResponse();
        this.checkedAt = snapshot.retrievedAt();
    }

    public UUID getId() { return id; }
    public UUID getCounterpartyId() { return counterpartyId; }
    public CheckSource getSource() { return source; }
    public CheckStatus getStatus() { return status; }
    public String getRequestId() { return requestId; }
    public String getUnavailableReason() { return unavailableReason; }
    public String getRawResponse() { return rawResponse; }
    public Instant getCheckedAt() { return checkedAt; }

    public List<CheckFinding> getFindings() {
        if (findings == null || findings.isBlank()) {
            return List.of();
        }
        return Arrays.stream(findings.split(",")).map(CheckFinding::valueOf).toList();
    }
}
