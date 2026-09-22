package net.ximatai.muyun.database.core.builder;

import java.util.List;

public record PrimaryKeyConstraint(String name, List<String> columns) {

    public PrimaryKeyConstraint {
        columns = List.copyOf(columns);
        if (columns.isEmpty() || columns.stream().anyMatch(column -> column == null || column.isBlank())) {
            throw new IllegalArgumentException("primary key columns must not be empty");
        }
    }

    public static PrimaryKeyConstraint of(String... columns) {
        return new PrimaryKeyConstraint(null, List.of(columns));
    }

    public static PrimaryKeyConstraint named(String name, String... columns) {
        return new PrimaryKeyConstraint(name, List.of(columns));
    }
}
