package com.aleruan.dealos.evidence.application;

import com.aleruan.dealos.platform.audit.AuditRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The audit records of a deal as they go into the package. A package may be handed to the customer or a
 * court, so Aleruan's own costs and margins are taken out of the payloads; such a record is marked
 * {@code redacted}, and its hash and link to the previous record stay, so the original can still be shown
 * and checked against the chain on request.
 */
public record AuditExtract(long seq, Instant occurredAt, String actor, String entityType, String entityId, String action,
                    JsonNode payload, boolean redacted, String prevHash, String hash) {

    /** Payload fields that reveal costs or margins. */
    static final Set<String> INTERNAL = Set.of("unitCost", "costTotal", "cost", "margin", "marginPct", "markupPct",
            "defaultMarkupPct");

    private static final ObjectMapper JSON = new ObjectMapper();

    public static List<AuditExtract> of(List<AuditRecord> records) {
        return records.stream().map(AuditExtract::of).toList();
    }

    static AuditExtract of(AuditRecord r) {
        JsonNode payload = parse(r.payload());
        boolean redacted = strip(payload);
        return new AuditExtract(r.seq(), r.occurredAt(), r.actor(), r.entityType(), r.entityId(), r.action(), payload,
                redacted, r.prevHash(), r.hash());
    }

    private static JsonNode parse(String payload) {
        if (payload == null) {
            return null;
        }
        try {
            return JSON.readTree(payload);
        } catch (JsonProcessingException e) {
            return JSON.getNodeFactory().textNode(payload);
        }
    }

    /** Removes internal fields at any depth; true when something was removed. */
    static boolean strip(JsonNode node) {
        boolean removed = false;
        if (node instanceof ObjectNode o) {
            for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> f = it.next();
                if (INTERNAL.contains(f.getKey())) {
                    it.remove();
                    removed = true;
                } else {
                    removed |= strip(f.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode child : node) {
                removed |= strip(child);
            }
        }
        return removed;
    }
}
