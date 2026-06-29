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

    public static String columnType(Column column, DBInfo.Type dbType) {
        if (column.getType() == ColumnType.ARRAY) {
            if (dbType != POSTGRESQL) {
                throw new IllegalArgumentException("ARRAY columns are only supported on PostgreSQL: " + column.getName());
            }
            return postgresArrayType(column);
        }
        return columnTypeTransform(dbType).transform(column.getType());
    }

    public static boolean sameColumnType(String expectedType, String actualType) {
        if (expectedType == null || actualType == null) {
            return expectedType == null && actualType == null;
        }
        return normalizeColumnType(expectedType).equals(normalizeColumnType(actualType));
    }

    public static String normalizeColumnType(String type) {
        String normalized = type.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
        if (normalized.startsWith("_")) {
            return normalizePostgresArrayElementType(normalized.substring(1)) + "[]";
        }
        if (normalized.endsWith("[]")) {
            String elementType = normalized.substring(0, normalized.length() - 2);
            return normalizePostgresArrayElementType(elementType) + "[]";
        }
        return normalizeScalarType(normalized);
    }

    public static String columnLength(Column column) {
        String length = column.getLength() == null ? "" : "(" + column.getLength() + ")";

        if (ignoresColumnLength(column)) {
            return "";
        }

        if (column.getType().equals(ColumnType.NUMERIC)
                && column.getScale() != null
                && column.getPrecision() != null) {
            return "(" + column.getPrecision() + "," + column.getScale() + ")";
        }

        return length;
    }

    public static boolean ignoresColumnLength(Column column) {
        return column.getType().equals(ColumnType.TEXT)
                || column.getType().equals(ColumnType.LONGTEXT)
                || column.getType().equals(ColumnType.ARRAY);
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

    private static String postgresArrayType(Column column) {
        ColumnType elementType = column.getElementType();
        if (elementType == null || elementType == ColumnType.UNKNOWN) {
            throw new IllegalArgumentException("ARRAY column elementType must be provided or inferred: " + column.getName());
        }
        return postgresArrayElementType(elementType) + "[]";
    }

    private static String postgresArrayElementType(ColumnType elementType) {
        return switch (elementType) {
            case VARCHAR -> "varchar";
            case TEXT, LONGTEXT -> "text";
            case INT -> "int";
            case BIGINT -> "bigint";
            case BOOLEAN -> "boolean";
            case TIMESTAMP -> "timestamp";
            case DATE -> "date";
            case NUMERIC -> "numeric";
            default -> throw new IllegalArgumentException("Unsupported ARRAY element type: " + elementType);
        };
    }

    private static String normalizePostgresArrayElementType(String type) {
        return switch (type) {
            case "character varying", "varchar" -> "varchar";
            case "integer", "int", "int4" -> "int";
            case "bigint", "int8" -> "bigint";
            case "bool", "boolean" -> "boolean";
            case "timestamp without time zone", "timestamp" -> "timestamp";
            case "date" -> "date";
            case "numeric", "decimal" -> "numeric";
            case "text", "longtext" -> "text";
            default -> type;
        };
    }

    private static String normalizeScalarType(String type) {
        return switch (type) {
            case "character varying" -> "varchar";
            case "integer" -> "int";
            case "boolean" -> "bool";
            case "timestamp without time zone" -> "timestamp";
            default -> type;
        };
    }
}
