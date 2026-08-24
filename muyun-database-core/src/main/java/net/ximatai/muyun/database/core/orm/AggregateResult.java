package net.ximatai.muyun.database.core.orm;

import java.util.List;
import java.util.Objects;

/** Typed aggregate execution result that retains its projection definition. */
public record AggregateResult(AggregateQuery query, List<AggregateRow> rows) {
    public AggregateResult {
        query = Objects.requireNonNull(query, "aggregate query must not be null");
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
