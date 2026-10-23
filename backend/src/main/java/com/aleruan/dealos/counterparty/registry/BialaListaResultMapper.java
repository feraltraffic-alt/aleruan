package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polish VAT white list (wl-api.mf.gov.pl), per its OpenAPI spec:
 * search:  {result: {subject: {name, nip, statusVat, regon, krs, workingAddress, residenceAddress,
 *           registrationLegalDate, accountNumbers[]}, requestId}}; subject is null when the NIP is not listed.
 * check:   {result: {accountAssigned: "TAK"|"NIE", requestId}}
 * errors:  {exception: {code, message}} (older responses: {code: "WL-1xx", message}).
 */
public final class BialaListaResultMapper {

    /** vatStatus used when the NIP is not on the white list at all. */
    public static final String NOT_REGISTERED = "Niezarejestrowany";

    private BialaListaResultMapper() {
    }

    public static RegistrySnapshot mapSearch(Map<String, Object> json, String raw, Instant at) {
        String error = error(json);
        if (error != null) {
            return RegistrySnapshot.unavailable(CheckSource.BIALA_LISTA, at, error, raw);
        }
        String requestId = JsonPaths.text(json, "result", "requestId");
        Object subject = JsonPaths.get(json, "result", "subject");
        if (!(subject instanceof Map<?, ?>)) {
            return RegistrySnapshot.builder(CheckSource.BIALA_LISTA, at)
                    .found(true).vatStatus(NOT_REGISTERED).requestId(requestId).rawResponse(raw).build();
        }
        String address = JsonPaths.text(json, "result", "subject", "workingAddress");
        if (address == null) {
            address = JsonPaths.text(json, "result", "subject", "residenceAddress");
        }
        Object accounts = JsonPaths.get(json, "result", "subject", "accountNumbers");
        String nip = JsonPaths.text(json, "result", "subject", "nip");
        return RegistrySnapshot.builder(CheckSource.BIALA_LISTA, at)
                .found(true)
                .requestId(requestId)
                .legalName(JsonPaths.text(json, "result", "subject", "name"))
                .address(address)
                .vatNumber(nip == null ? null : "PL" + nip)
                .krsNumber(JsonPaths.text(json, "result", "subject", "krs"))
                .vatStatus(JsonPaths.text(json, "result", "subject", "statusVat"))
                .registeredOn(JsonPaths.date(json, "result", "subject", "registrationLegalDate"))
                .publishedAccounts(accounts instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of())
                .rawResponse(raw)
                .build();
    }

    /** Result of /check/nip/{nip}/bank-account/{nrb}: published accounts = the account itself when assigned. */
    public static RegistrySnapshot mapAccountCheck(Map<String, Object> json, String raw, Instant at, String nrb) {
        String error = error(json);
        if (error != null) {
            return RegistrySnapshot.unavailable(CheckSource.BIALA_LISTA, at, error, raw);
        }
        boolean assigned = "TAK".equalsIgnoreCase(JsonPaths.text(json, "result", "accountAssigned"));
        return RegistrySnapshot.builder(CheckSource.BIALA_LISTA, at)
                .found(true)
                .requestId(JsonPaths.text(json, "result", "requestId"))
                .publishedAccounts(assigned ? List.of(nrb) : List.of())
                .rawResponse(raw)
                .build();
    }

    private static String error(Map<String, Object> json) {
        String code = JsonPaths.text(json, "exception", "code");
        if (code != null) {
            return code + ": " + JsonPaths.text(json, "exception", "message");
        }
        String legacy = JsonPaths.text(json, "code");
        return legacy != null && json.get("result") == null ? legacy + ": " + JsonPaths.text(json, "message") : null;
    }
}
