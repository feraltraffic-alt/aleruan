package com.aleruan.dealos.evidence.summary;

import com.aleruan.dealos.platform.documents.DocFormat;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.evidence.EvidenceCheck;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Texts of the evidence summary in the four document languages (resources/evidence/summary_xx.properties). */
public final class SummaryTexts {

    private static final Map<DocLanguage, Properties> TEXTS = new EnumMap<>(DocLanguage.class);
    private static final Pattern PARAM = Pattern.compile("\\{([a-zA-Z]+)}");
    private static final Set<String> MONEY = Set.of("accepted", "contract", "amount");
    private static final Set<String> DATES = Set.of("date", "due");

    static {
        for (DocLanguage lang : DocLanguage.values()) {
            TEXTS.put(lang, load("evidence/summary_" + lang.name().toLowerCase(Locale.ROOT) + ".properties"));
        }
    }

    private SummaryTexts() {
    }

    public static Properties of(DocLanguage lang) {
        return TEXTS.get(lang);
    }

    /** The text in the language, English when missing there, the key itself as a last resort. */
    public static String t(DocLanguage lang, String key) {
        String v = TEXTS.get(lang).getProperty(key);
        if (v == null) {
            v = TEXTS.get(DocLanguage.EN).getProperty(key);
        }
        return v == null ? key : v;
    }

    public static String t(DocLanguage lang, String key, Map<String, String> params) {
        return fill(lang, t(lang, key), params);
    }

    /** A status code as a word, or the code when it has no translation. */
    public static String status(DocLanguage lang, String code) {
        return code == null ? "" : t(lang, "status." + code).replace("status.", "");
    }

    public static String check(DocLanguage lang, EvidenceCheck c) {
        return fill(lang, t(lang, "check." + c.code() + "." + c.level().name()), c.params());
    }

    static String fill(DocLanguage lang, String template, Map<String, String> params) {
        Matcher m = PARAM.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = params.get(name);
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : format(lang, name, value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String format(DocLanguage lang, String name, String value) {
        try {
            if (MONEY.contains(name)) {
                return DocFormat.money(lang, new BigDecimal(value));
            }
            if (DATES.contains(name)) {
                return DocFormat.date(lang, LocalDate.parse(value));
            }
        } catch (RuntimeException e) {
            return value; // not a number or a date after all: show as given
        }
        return switch (name) {
            case "status" -> status(lang, value);
            case "role" -> t(lang, "role." + value);
            default -> value;
        };
    }

    private static Properties load(String path) {
        try (InputStream in = SummaryTexts.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
