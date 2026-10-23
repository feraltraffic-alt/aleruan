package com.aleruan.dealos.counterparty.registry;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Small helpers to read already-parsed JSON (Map/List from Jackson) without a framework dependency. */
final class JsonPaths {

    private JsonPaths() {
    }

    static Object get(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    static String text(Map<String, Object> root, String... path) {
        Object v = get(root, path);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    static Boolean bool(Map<String, Object> root, String... path) {
        Object v = get(root, path);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? null : Boolean.valueOf(v.toString());
    }

    static LocalDate date(Map<String, Object> root, String... path) {
        String s = text(root, path);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
