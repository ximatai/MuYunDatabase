package net.ximatai.muyun.database.core.builder;

import java.util.List;

public record UniqueConstraint(String name, List<String> columns) {

    public UniqueConstraint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("unique constraint name must not be blank");
        }
        columns = List.copyOf(columns);
        if (columns.isEmpty() || columns.stream().anyMatch(column -> column == null || column.isBlank())) {
            throw new IllegalArgumentException("unique constraint columns must not be empty");
        }
    }

    public static UniqueConstraint named(String name, String... columns) {
        return new UniqueConstraint(name, List.of(columns));
    }
}
