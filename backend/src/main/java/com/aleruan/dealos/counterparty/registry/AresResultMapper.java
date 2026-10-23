package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.time.Instant;
import java.util.Map;

/**
 * Maps the ARES REST v3 response (GET /ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/{ico}).
 * Fields used: ico, obchodniJmeno, sidlo.textovaAdresa, dic, datumVzniku, datumZaniku,
 * seznamRegistraci.stavZdrojeIr (insolvency register state).
 * Not found / error: {kod: "NENALEZENO", popis: "..."}.
 * Field names to be re-checked against https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/v3/api-docs.
 */
public final class AresResultMapper {

    private static final String ACTIVE = "AKTIVNI";

    private AresResultMapper() {
    }

    public static RegistrySnapshot map(Map<String, Object> json, String raw, Instant at) {
        String errorCode = JsonPaths.text(json, "kod");
        if (errorCode != null) {
            if ("NENALEZENO".equalsIgnoreCase(errorCode)) {
                return RegistrySnapshot.builder(CheckSource.ARES, at).found(false).rawResponse(raw).build();
            }
            return RegistrySnapshot.unavailable(CheckSource.ARES, at,
                    errorCode + ": " + JsonPaths.text(json, "popis"), raw);
        }
        String irState = JsonPaths.text(json, "seznamRegistraci", "stavZdrojeIr");
        return RegistrySnapshot.builder(CheckSource.ARES, at)
                .found(true)
                .legalName(JsonPaths.text(json, "obchodniJmeno"))
                .address(JsonPaths.text(json, "sidlo", "textovaAdresa"))
                .vatNumber(JsonPaths.text(json, "dic"))
                .registeredOn(JsonPaths.date(json, "datumVzniku"))
                .ceasedOn(JsonPaths.date(json, "datumZaniku"))
                .insolvency(irState == null ? null : ACTIVE.equalsIgnoreCase(irState))
                .rawResponse(raw)
                .build();
    }
}
