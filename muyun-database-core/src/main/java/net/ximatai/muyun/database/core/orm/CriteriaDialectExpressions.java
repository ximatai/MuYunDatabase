package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.Objects;

final class CriteriaDialectExpressions {

    private CriteriaDialectExpressions() {
    }

    static String collectionContains(DBInfo.Type dbType,
                                     ColumnType columnType,
                                     String columnSql,
                                     String valueExpression) {
        requireCollectionType(columnType);
        Objects.requireNonNull(dbType, "dbType must not be null");
        requireExpression(columnSql, "columnSql");
        requireExpression(valueExpression, "valueExpression");

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

    static String collectionIsEmpty(DBInfo.Type dbType, ColumnType columnType, String columnSql) {
        requireCollectionType(columnType);
        Objects.requireNonNull(dbType, "dbType must not be null");
        requireExpression(columnSql, "columnSql");

        if (columnType == ColumnType.JSON_SET) {
            if (dbType == DBInfo.Type.POSTGRESQL) {
                return "(" + columnSql + " IS NULL OR jsonb_array_length(" + columnSql + "::jsonb) = 0)";
            }
            return "(" + columnSql + " IS NULL OR JSON_LENGTH(CAST(" + columnSql + " AS JSON)) = 0)";
        }
        return "(" + columnSql + " IS NULL OR " + columnSql + " = '')";
    }

    private static void requireCollectionType(ColumnType columnType) {
        if (columnType != ColumnType.SET && columnType != ColumnType.JSON_SET) {
            throw new IllegalArgumentException("Column type must be SET or JSON_SET: " + columnType);
        }
    }

    private static void requireExpression(String expression, String name) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
