package net.ximatai.muyun.database.core.metadata;

import net.ximatai.muyun.database.core.builder.ForeignKeyAction;

import java.util.List;

public record DBForeignKey(
        String name,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        ForeignKeyAction onDelete
) {
    public DBForeignKey {
        columns = List.copyOf(columns);
        referencedColumns = List.copyOf(referencedColumns);
    }
}
