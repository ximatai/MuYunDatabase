package net.ximatai.muyun.database.core.orm;

import java.util.Objects;

/** One projected aggregate value; field is omitted only for {@link AggregateOperation#COUNT}. */
public record AggregateSelection(String key, AggregateOperation operation, String field) {
    public AggregateSelection {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("aggregate key must not be blank");
        key = key.trim();
        operation = Objects.requireNonNull(operation, "aggregate operation must not be null");
        field = field == null || field.isBlank() ? null : field.trim();
        if (operation != AggregateOperation.COUNT && field == null) {
            throw new IllegalArgumentException(operation + " aggregate requires a field");
        }
        if (operation == AggregateOperation.COUNT && field != null) {
            throw new IllegalArgumentException("COUNT aggregate does not accept a field; use COUNT(*)");
        }
    }
    public static AggregateSelection count(String key) { return new AggregateSelection(key, AggregateOperation.COUNT, null); }
    public static AggregateSelection of(String key, AggregateOperation operation, String field) {
        return new AggregateSelection(key, operation, field);
    }
}
