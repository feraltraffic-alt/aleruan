package com.aleruan.dealos.evidence.summary;

import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Where each file goes in the archive: the section's folder and a safe, unique ASCII name. */
public final class EvidenceLayout {

    private static final int MAX_NAME = 100;

    private EvidenceLayout() {
    }

    /** Paths keyed by file identity, in entry order. */
    public static Map<EvidenceFile, String> paths(List<EvidenceEntry> entries) {
        Map<EvidenceFile, String> paths = new IdentityHashMap<>();
        Set<String> taken = new HashSet<>();
        for (EvidenceEntry e : entries) {
            for (EvidenceFile f : e.files()) {
                paths.put(f, unique(e.section().folder() + "/", safeName(f.fileName()), taken));
            }
        }
        return paths;
    }

    /** Letters without diacritics, digits, dot, dash and underscore; never empty, never hidden. */
    public static String safeName(String name) {
        String plain = Normalizer.normalize(letters(name == null ? "" : name), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String safe = plain.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_{2,}", "_").replaceAll("^[._]+", "");
        if (safe.isEmpty()) {
            safe = "file";
        }
        if (safe.length() > MAX_NAME) {
            int dot = safe.lastIndexOf('.');
            String ext = dot > 0 && safe.length() - dot <= 6 ? safe.substring(dot) : "";
            safe = safe.substring(0, MAX_NAME - ext.length()) + ext;
        }
        return safe;
    }

    /** Letters that are not a base letter plus an accent, so NFD leaves them alone. */
    private static String letters(String s) {
        return s.replace("ł", "l").replace("Ł", "L").replace("ø", "o").replace("Ø", "O").replace("đ", "d")
                .replace("Đ", "D").replace("ß", "ss").replace("æ", "ae").replace("Æ", "AE").replace("œ", "oe")
                .replace("Œ", "OE");
    }

    private static String unique(String folder, String name, Set<String> taken) {
        String path = folder + name;
        if (taken.add(path.toLowerCase(Locale.ROOT))) {
            return path;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; ; n++) {
            path = folder + base + "-" + n + ext;
            if (taken.add(path.toLowerCase(Locale.ROOT))) {
                return path;
            }
        }
    }
}
