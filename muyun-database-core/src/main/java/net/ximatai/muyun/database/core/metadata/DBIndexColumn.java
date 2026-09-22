package net.ximatai.muyun.database.core.metadata;

import net.ximatai.muyun.database.core.builder.IndexSortDirection;

import java.util.Objects;

public record DBIndexColumn(String name, IndexSortDirection direction, int ordinal) {
    public DBIndexColumn {
        name = Objects.requireNonNull(name, "index column name must not be null");
        direction = Objects.requireNonNullElse(direction, IndexSortDirection.ASC);
    }
}
