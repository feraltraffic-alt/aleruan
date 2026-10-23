package com.aleruan.dealos.counterparty.registry;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Polish court register KRS (api-krs.ms.gov.pl /OdpisAktualny/{krs}?rejestr=P&format=json).
 * Liquidation and bankruptcy: Polish law requires the suffix "w likwidacji" / "w upadłości" /
 * "w restrukturyzacji" in the company name while the procedure runs; section 6 (dzial6) is also scanned.
 */
public final class KrsResultMapper {

    private static final DateTimeFormatter PL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private KrsResultMapper() {
    }

    public static RegistrySnapshot map(Map<String, Object> json, String raw, Instant at) {
        if (JsonPaths.get(json, "odpis") == null) {
            return RegistrySnapshot.builder(CheckSource.KRS, at).found(false).rawResponse(raw).build();
        }
        String name = JsonPaths.text(json, "odpis", "dane", "dzial1", "danePodmiotu", "nazwa");
        String folded = com.aleruan.dealos.counterparty.domain.Names.fold(name);
        // Section 6 is scanned by KEY names only: merger texts often say "bez przeprowadzenia likwidacji".
        String section6Keys = com.aleruan.dealos.counterparty.domain.Names.fold(
                keys(JsonPaths.get(json, "odpis", "dane", "dzial6"), new StringBuilder()).toString());
        boolean liquidation = folded.contains("W LIKWIDACJI") || section6Keys.contains("LIKWIDAC");
        boolean insolvency = folded.contains("W UPADLOSCI") || folded.contains("W RESTRUKTURYZACJI")
                || section6Keys.contains("UPADLOS") || section6Keys.contains("RESTRUKTURYZAC");
        String nip = JsonPaths.text(json, "odpis", "dane", "dzial1", "danePodmiotu", "identyfikatory", "nip");
        return RegistrySnapshot.builder(CheckSource.KRS, at)
                .found(true)
                .legalName(name)
                .vatNumber(nip == null ? null : "PL" + nip)
                .krsNumber(JsonPaths.text(json, "odpis", "naglowekA", "numerKRS"))
                .address(address(json))
                .registeredOn(plDate(JsonPaths.text(json, "odpis", "naglowekA", "dataRejestracjiWKRS")))
                .insolvency(insolvency)
                // no exact date in the extract: "today" marks it ceased for the analyzer
                .ceasedOn(liquidation ? LocalDate.ofInstant(at, java.time.ZoneOffset.UTC) : null)
                .rawResponse(raw)
                .build();
    }

    /** All non-empty keys of a nested JSON value, space separated. */
    private static StringBuilder keys(Object node, StringBuilder out) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                boolean empty = v == null || (v instanceof Map<?, ?> m && m.isEmpty())
                        || (v instanceof java.util.Collection<?> c && c.isEmpty());
                if (!empty) {
                    out.append(k).append(' ');
                    keys(v, out);
                }
            });
        } else if (node instanceof java.util.Collection<?> list) {
            list.forEach(item -> keys(item, out));
        }
        return out;
    }

    private static String address(Map<String, Object> json) {
        String[] base = {"odpis", "dane", "dzial1", "siedzibaIAdres", "adres"};
        String street = JsonPaths.text(json, append(base, "ulica"));
        String house = JsonPaths.text(json, append(base, "nrDomu"));
        String flat = JsonPaths.text(json, append(base, "nrLokalu"));
        String zip = JsonPaths.text(json, append(base, "kodPocztowy"));
        String city = JsonPaths.text(json, append(base, "miejscowosc"));
        if (street == null && city == null) {
            return null;
        }
        StringJoiner line = new StringJoiner(" ");
        if (street != null) line.add(street);
        if (house != null) line.add(flat == null ? house : house + "/" + flat);
        StringJoiner all = new StringJoiner(", ");
        if (line.length() > 0) all.add(line.toString());
        all.add(((zip == null ? "" : zip + " ") + (city == null ? "" : city)).trim());
        return all.toString();
    }

    private static String[] append(String[] base, String key) {
        String[] r = java.util.Arrays.copyOf(base, base.length + 1);
        r[base.length] = key;
        return r;
    }

    private static LocalDate plDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s, PL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

}
