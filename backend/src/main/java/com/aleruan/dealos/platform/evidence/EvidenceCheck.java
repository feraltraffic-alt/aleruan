package com.aleruan.dealos.platform.evidence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One line of the completeness indicator. {@code code} names the rule (translated by the UI and the
 * summary), {@code params} fill its text, e.g. {@code INVOICE_WITHOUT_ACCEPTANCE {number=FV-2026-0007}}.
 */
public record EvidenceCheck(String code, Level level, Map<String, String> params) {

    public enum Level {
        /** In order. */
        OK,
        /** Worth a look, the package is still usable. */
        WARN,
        /** Something the package should have and does not. */
        MISSING
    }

    public EvidenceCheck {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(level, "level");
        params = params == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static EvidenceCheck ok(String code, Map<String, String> params) {
        return new EvidenceCheck(code, Level.OK, params);
    }

    public static EvidenceCheck warn(String code, Map<String, String> params) {
        return new EvidenceCheck(code, Level.WARN, params);
    }

    public static EvidenceCheck missing(String code, Map<String, String> params) {
        return new EvidenceCheck(code, Level.MISSING, params);
    }
}
