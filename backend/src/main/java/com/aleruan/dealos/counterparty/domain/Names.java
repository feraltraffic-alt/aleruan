package com.aleruan.dealos.counterparty.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Company-name normalisation for matching: no diacritics, no punctuation, no legal-form words. */
public final class Names {

    private static final Set<String> LEGAL_FORMS = Set.of(
            "SRO", "S", "R", "O", "AS", "A", "SPOL", "SP", "Z", "OO", "ZOO", "SA", "SPK", "SPJ", "SKA",
            "GMBH", "AG", "KG", "UG", "MBH", "LTD", "LLC", "LLP", "INC", "CORP", "CO", "PLC", "BV", "NV",
            "SRL", "SARL", "SAS", "SPA", "OOO", "AO", "PAO", "ZAO", "OAO", "JSC", "TOV", "PP", "LIMITED",
            "COMPANY", "CORPORATION",
            "ООО", "АО", "ПАО", "ЗАО", "ОАО", "ТОВ", "ПП", "ИП");

    private Names() {
    }

    /** Uppercase ASCII without diacritics (Polish ł handled explicitly, NFD does not decompose it). */
    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    /** "Aleruan-industry s.r.o." → "ALERUAN INDUSTRY"; "OOO «Стройинвест»" keeps its Cyrillic letters. */
    public static String forMatching(String name) {
        String cleaned = fold(name).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return Arrays.stream(cleaned.split(" "))
                .filter(w -> !w.isEmpty() && !LEGAL_FORMS.contains(w))
                .collect(Collectors.joining(" "));
    }
}
