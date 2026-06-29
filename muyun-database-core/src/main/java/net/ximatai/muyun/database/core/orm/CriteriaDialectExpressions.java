package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.List;
import java.util.Objects;

final class CriteriaDialectExpressions {

    private CriteriaDialectExpressions() {
    }

    static String collectionContains(DBInfo.Type dbType,
                                     ColumnType columnType,
                                     ColumnType elementColumnType,
                                     String columnSql,
                                     String valueExpression) {
        requireCollectionType(columnType);
        Objects.requireNonNull(dbType, "dbType must not be null");
        requireExpression(columnSql, "columnSql");
        requireExpression(valueExpression, "valueExpression");

        if (columnType == ColumnType.ARRAY) {
            requirePostgresArray(dbType);
            return columnSql + " @> " + postgresArrayExpression(elementColumnType, List.of(valueExpression));
        }
        if (columnType == ColumnType.JSON_SET) {
            if (dbType == DBInfo.Type.POSTGRESQL) {
                return "jsonb_exists(" + columnSql + "::jsonb, " + valueExpression + ")";
            }
            return "JSON_CONTAINS(CAST(" + columnSql + " AS JSON), JSON_QUOTE(" + valueExpression + "))";
        }
        if (dbType == DBInfo.Type.POSTGRESQL) {
            return "POSITION(',' || " + valueExpression + " || ',' IN ',' || COALESCE("
                    + columnSql + ", '') || ',') > 0";
        }
        return "FIND_IN_SET(" + valueExpression + ", COALESCE(" + columnSql + ", '')) > 0";
    }

    static String collectionContainsAny(DBInfo.Type dbType,
                                        ColumnType columnType,
                                        ColumnType elementColumnType,
                                        String columnSql,
                                        List<String> valueExpressions) {
        requireCollectionType(columnType);
        requireValueExpressions(valueExpressions);
        if (columnType == ColumnType.ARRAY) {
            requirePostgresArray(dbType);
            return columnSql + " && " + postgresArrayExpression(elementColumnType, valueExpressions);
        }
        return "(" + valueExpressions.stream()
                .map(valueExpression -> collectionContains(dbType, columnType, elementColumnType, columnSql, valueExpression))
                .reduce((left, right) -> left + " OR " + right)
                .orElseThrow() + ")";
    }

    static String collectionContainsAll(DBInfo.Type dbType,
                                        ColumnType columnType,
                                        ColumnType elementColumnType,
                                        String columnSql,
                                        List<String> valueExpressions) {
        requireCollectionType(columnType);
        requireValueExpressions(valueExpressions);
        if (columnType == ColumnType.ARRAY) {
            requirePostgresArray(dbType);
            return columnSql + " @> " + postgresArrayExpression(elementColumnType, valueExpressions);
        }
        return "(" + valueExpressions.stream()
                .map(valueExpression -> collectionContains(dbType, columnType, elementColumnType, columnSql, valueExpression))
                .reduce((left, right) -> left + " AND " + right)
                .orElseThrow() + ")";
    }

    static String collectionIsEmpty(DBInfo.Type dbType,
                                    ColumnType columnType,
                                    ColumnType elementColumnType,
                                    String columnSql) {
        requireCollectionType(columnType);
        Objects.requireNonNull(dbType, "dbType must not be null");
        requireExpression(columnSql, "columnSql");

        if (columnType == ColumnType.ARRAY) {
            requirePostgresArray(dbType);
            return "(" + columnSql + " IS NULL OR cardinality(" + columnSql + ") = 0)";
        }
        if (columnType == ColumnType.JSON_SET) {
            if (dbType == DBInfo.Type.POSTGRESQL) {
                return "(" + columnSql + " IS NULL OR jsonb_array_length(" + columnSql + "::jsonb) = 0)";
            }
            return "(" + columnSql + " IS NULL OR JSON_LENGTH(CAST(" + columnSql + " AS JSON)) = 0)";
        }
        return "(" + columnSql + " IS NULL OR " + columnSql + " = '')";
    }

    private static void requireCollectionType(ColumnType columnType) {
        if (columnType != ColumnType.SET && columnType != ColumnType.JSON_SET && columnType != ColumnType.ARRAY) {
            throw new IllegalArgumentException("Column type must be SET, JSON_SET, or ARRAY: " + columnType);
        }
    }

    private static void requirePostgresArray(DBInfo.Type dbType) {
        if (dbType != DBInfo.Type.POSTGRESQL) {
            throw new IllegalArgumentException("ARRAY criteria are only supported on PostgreSQL");
        }
    }

    private static void requireExpression(String expression, String name) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireValueExpressions(List<String> valueExpressions) {
        Objects.requireNonNull(valueExpressions, "valueExpressions must not be null");
        if (valueExpressions.isEmpty()) {
            throw new IllegalArgumentException("valueExpressions must not be empty");
        }
        valueExpressions.forEach(valueExpression -> requireExpression(valueExpression, "valueExpression"));
    }

    private static String postgresArrayExpression(ColumnType elementColumnType, List<String> valueExpressions) {
        return "ARRAY[" + String.join(", ", valueExpressions) + "]::" + postgresArrayCast(elementColumnType);
    }

    private static String postgresArrayCast(ColumnType elementColumnType) {
        if (elementColumnType == null || elementColumnType == ColumnType.UNKNOWN) {
            throw new IllegalArgumentException("ARRAY criteria require elementType");
        }
        return switch (elementColumnType) {
            case VARCHAR -> "varchar[]";
            case TEXT, LONGTEXT -> "text[]";
            case INT -> "int[]";
            case BIGINT -> "bigint[]";
            case BOOLEAN -> "boolean[]";
            case TIMESTAMP -> "timestamp[]";
            case DATE -> "date[]";
            case NUMERIC -> "numeric[]";
            default -> throw new IllegalArgumentException("Unsupported ARRAY element type: " + elementColumnType);
        };
    }
}
