package net.ximatai.muyun.database.core.orm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One aggregate result row keyed by the group-by fields and aggregate selection keys. */
public record AggregateRow(Map<String, Object> values) {
    public AggregateRow {
        Objects.requireNonNull(values, "aggregate row values must not be null");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Object value(String key) {
        return values.get(key);
    }

    /** Returns the immutable, compatibility-friendly Map representation of this row. */
    public Map<String, Object> asMap() {
        return values;
    }
}
