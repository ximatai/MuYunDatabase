package net.ximatai.muyun.database.core.builder;

import java.util.Objects;

public record IndexColumn(String name, IndexSortDirection direction) {

    public IndexColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("index column name must not be blank");
        }
        name = name.trim();
        direction = Objects.requireNonNullElse(direction, IndexSortDirection.ASC);
    }

    public static IndexColumn asc(String name) {
        return new IndexColumn(name, IndexSortDirection.ASC);
    }

    public static IndexColumn desc(String name) {
        return new IndexColumn(name, IndexSortDirection.DESC);
    }
}
