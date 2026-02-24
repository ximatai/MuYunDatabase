package net.ximatai.muyun.database.core.sql;

import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.Objects;

/**
 * Cross-database SQL expression helpers for business-side query assembly.
 */
public final class SqlDialectExpressions {

    private SqlDialectExpressions() {
    }

    /**
     * Build CSV contains expression by database type.
     * valueExpression can be "?" or ":name".
     */
    public static String csvContains(DBInfo.Type dbType, String valueExpression, String columnExpression) {
        Objects.requireNonNull(dbType, "dbType must not be null");
        requireExpression(valueExpression, "valueExpression");
        requireExpression(columnExpression, "columnExpression");
        if (dbType == DBInfo.Type.MYSQL) {
            return "FIND_IN_SET(" + valueExpression + ", " + columnExpression + ") > 0";
        }
        return "POSITION(',' || " + valueExpression + " || ',' IN ',' || COALESCE(" + columnExpression + ", '') || ',') > 0";
    }

    private static void requireExpression(String expression, String name) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
