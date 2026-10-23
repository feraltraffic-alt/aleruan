package com.aleruan.dealos.counterparty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One registry check as evidence: what was asked where, when, the registry's request id and its raw answer.
 * Source, status and findings are plain strings to keep modules decoupled.
 */
public record CheckRecord(UUID id, UUID counterpartyId, String source, String status, List<String> findings,
                          String requestId, String unavailableReason, String rawResponse, Instant checkedAt,
                          String checkedAccount) {
}
