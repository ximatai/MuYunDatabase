package net.ximatai.muyun.database.core.builder;

import net.ximatai.muyun.database.core.builder.sql.SchemaBuildRules;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.Objects;

/**
 * Evaluates the differences between a requested column definition and database metadata.
 * The result is shared by schema planning and DDL execution so both paths apply identical
 * comparison rules.
 */
public final class ColumnDiffEvaluator {

    private ColumnDiffEvaluator() {
    }

    public static ColumnDiff evaluate(Column column, DBColumn dbColumn, String expectedType, DBInfo.Type databaseType) {
        boolean typeChanged = !SchemaBuildRules.sameColumnType(expectedType, dbColumn.getType(), databaseType, dbColumn.getLength())
                || columnLengthChanged(column, dbColumn);
        boolean primaryKeyChanged = column.isPrimaryKey() && !dbColumn.isPrimaryKey();
        boolean nullableChanged = dbColumn.isNullable() != column.isNullable();
        boolean defaultChanged = !dbColumn.isSequence()
                && !SchemaBuildRules.sameColumnDefault(expectedType, dbColumn.getType(), databaseType, dbColumn.getLength(), column.getDefaultValue(), dbColumn.getDefaultValueWithString());
        boolean sequenceChanged = dbColumn.isSequence() != column.isSequence();
        boolean commentChanged = column.getComment() != null && !Objects.equals(dbColumn.getDescription(), column.getComment());
        return new ColumnDiff(typeChanged, primaryKeyChanged, nullableChanged, defaultChanged, sequenceChanged, commentChanged);
    }

    private static boolean columnLengthChanged(Column column, DBColumn dbColumn) {
        return !SchemaBuildRules.ignoresColumnLength(column)
                && column.getLength() != null
                && !column.getLength().equals(dbColumn.getLength());
    }

    public record ColumnDiff(
            boolean typeChanged,
            boolean primaryKeyChanged,
            boolean nullableChanged,
            boolean defaultChanged,
            boolean sequenceChanged,
            boolean commentChanged
    ) {
    }
}
