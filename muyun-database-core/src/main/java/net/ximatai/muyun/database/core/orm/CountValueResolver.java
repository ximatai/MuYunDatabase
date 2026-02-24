package net.ximatai.muyun.database.core.orm;

import java.util.Map;

final class CountValueResolver {

    private CountValueResolver() {
    }

    static Long resolve(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (key.equalsIgnoreCase("total_count")
                    || key.equalsIgnoreCase("count")
                    || key.equalsIgnoreCase("count(*)")) {
                return toLong(entry.getValue());
            }
        }

        for (Object value : row.values()) {
            Long parsed = toLong(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
