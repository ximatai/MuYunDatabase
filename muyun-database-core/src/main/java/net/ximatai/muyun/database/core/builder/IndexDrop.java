package net.ximatai.muyun.database.core.builder;

import java.util.List;
import java.util.Objects;

/**
 * Selects an existing index to delete, independently of an index definition.
 * A selector contains either an exact name or a non-empty column set.
 */
public record IndexDrop(String name, List<String> columns) {
    public IndexDrop {
        columns = List.copyOf(Objects.requireNonNull(columns, "index columns must not be null"));
        if (name != null) {
            if (name.isBlank() || !columns.isEmpty()) {
                throw new IllegalArgumentException("named index drop requires a non-blank name and no columns");
            }
        } else if (columns.isEmpty() || columns.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("column index drop requires non-blank columns");
        }
    }

    public static IndexDrop byName(String name) {
        return new IndexDrop(Objects.requireNonNull(name, "index name must not be null"), List.of());
    }

    public static IndexDrop byColumns(List<String> columns) {
        return new IndexDrop(null, columns);
    }
}
