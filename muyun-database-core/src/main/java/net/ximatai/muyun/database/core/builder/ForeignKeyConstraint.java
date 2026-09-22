package net.ximatai.muyun.database.core.builder;

import java.util.List;
import java.util.Objects;

public record ForeignKeyConstraint(
        String name,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        ForeignKeyAction onDelete
) {
    public ForeignKeyConstraint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("foreign key name must not be blank");
        }
        columns = List.copyOf(columns);
        referencedColumns = List.copyOf(referencedColumns);
        if (columns.isEmpty() || columns.size() != referencedColumns.size()) {
            throw new IllegalArgumentException("foreign key columns must be non-empty and match referenced columns");
        }
        if (columns.stream().anyMatch(column -> column == null || column.isBlank())
                || referencedColumns.stream().anyMatch(column -> column == null || column.isBlank())) {
            throw new IllegalArgumentException("foreign key columns must not be blank");
        }
        if (referencedTable == null || referencedTable.isBlank()) {
            throw new IllegalArgumentException("referenced table must not be blank");
        }
        onDelete = Objects.requireNonNullElse(onDelete, ForeignKeyAction.NO_ACTION);
    }

    public static ForeignKeyConstraint named(
            String name,
            List<String> columns,
            String referencedTable,
            List<String> referencedColumns,
            ForeignKeyAction onDelete
    ) {
        return new ForeignKeyConstraint(name, columns, null, referencedTable, referencedColumns, onDelete);
    }
}
