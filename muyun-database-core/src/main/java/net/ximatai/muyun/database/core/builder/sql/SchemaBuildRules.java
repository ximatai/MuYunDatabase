package net.ximatai.muyun.database.core.builder.sql;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.IColumnTypeTransform;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.regex.Pattern;

import static net.ximatai.muyun.database.core.metadata.DBInfo.Type.POSTGRESQL;

public final class SchemaBuildRules {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SchemaBuildRules() {
    }

    public static boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }

    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        if (!identifier.equals(identifier.trim())) {
            return false;
        }
        return identifier.indexOf('\0') < 0;
    }

    public static String quoteIdentifier(String identifier, DBInfo.Type dbType) {
        if (!isValidIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        if (dbType == DBInfo.Type.MYSQL) {
            return "`" + identifier.replace("`", "``") + "`";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static String qualifiedName(String schema, String table, DBInfo.Type dbType) {
        return quoteIdentifier(schema, dbType) + "." + quoteIdentifier(table, dbType);
    }

    public static IColumnTypeTransform columnTypeTransform(DBInfo.Type dbType) {
        return dbType == POSTGRESQL ? IColumnTypeTransform.POSTGRESQL : IColumnTypeTransform.DEFAULT;
    }

    public static String columnLength(Column column) {
        String length = column.getLength() == null ? "" : "(" + column.getLength() + ")";

        if (column.getType().equals(ColumnType.TEXT)) {
            return "";
        }

        if (column.getType().equals(ColumnType.NUMERIC)
                && column.getScale() != null
                && column.getPrecision() != null) {
            return "(" + column.getPrecision() + "," + column.getScale() + ")";
        }

        return length;
    }

    public static String columnDefinition(Column column, String mappedType, DBInfo.Type dbType) {
        String defaultValue = column.getDefaultValue();
        String defaultValueString = defaultValue == null
                ? ""
                : ("AUTO_INCREMENT".equalsIgnoreCase(defaultValue) ? "" : " DEFAULT ") + defaultValue;

        String primaryKeyString = "";
        if (defaultValueString.equalsIgnoreCase("AUTO_INCREMENT") && column.isPrimaryKey()) {
            primaryKeyString = " PRIMARY KEY ";
        }

        return quoteIdentifier(column.getName(), dbType)
                + " "
                + mappedType
                + columnLength(column)
                + (column.isNullable() ? " null " : " not null ")
                + defaultValueString
                + primaryKeyString;
    }

    public static String indexName(String tableName, Index index) {
        if (index.getName() != null && !index.getName().isEmpty()) {
            return index.getName();
        }
        String suffix = index.isUnique() ? "_uindex" : "_index";
        return tableName + "_" + String.join("_", index.getColumns()) + suffix;
    }
}
