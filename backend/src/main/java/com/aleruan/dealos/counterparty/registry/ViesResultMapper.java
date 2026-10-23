package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Maps the VIES REST response (POST /taxation_customs/vies/rest-api/check-vat-number).
 * Success: {countryCode, vatNumber, requestDate, valid, requestIdentifier, name, address}.
 * Error:   {actionSucceed: false, errorWrappers: [{error: "MS_UNAVAILABLE", ...}]}.
 */
public final class ViesResultMapper {

    private ViesResultMapper() {
    }

    public static RegistrySnapshot map(Map<String, Object> json, String raw, Instant at) {
        Object wrappers = json.get("errorWrappers");
        if (Boolean.FALSE.equals(json.get("actionSucceed"))
                || (wrappers instanceof List<?> list && !list.isEmpty())) {
            return RegistrySnapshot.unavailable(CheckSource.VIES, at, firstError(wrappers), raw);
        }
        Boolean valid = JsonPaths.bool(json, "valid");
        if (valid == null) {
            return RegistrySnapshot.unavailable(CheckSource.VIES, at, "No 'valid' field in VIES response", raw);
        }
        String country = JsonPaths.text(json, "countryCode");
        String number = JsonPaths.text(json, "vatNumber");
        return RegistrySnapshot.builder(CheckSource.VIES, at)
                .found(valid)
                .vatValid(valid)
                .requestId(JsonPaths.text(json, "requestIdentifier"))
                .legalName(clean(JsonPaths.text(json, "name")))
                .address(clean(JsonPaths.text(json, "address")))
                .vatNumber(country != null && number != null ? country + number : null)
                .rawResponse(raw)
                .build();
    }

    /** VIES returns "---" when a member state does not disclose name/address (e.g. Germany). */
    private static String clean(String value) {
        if (value == null || value.equals("---")) {
            return null;
        }
        return value.replace("\n", ", ").replaceAll("\\s+", " ").trim();
    }

    private static String firstError(Object wrappers) {
        if (wrappers instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object error = first.get("error");
            return error == null ? "VIES error" : error.toString();
        }
        return "VIES error";
    }
}
