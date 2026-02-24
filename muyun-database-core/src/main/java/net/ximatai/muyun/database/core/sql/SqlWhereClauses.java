package net.ximatai.muyun.database.core.sql;

import java.util.List;

/**
 * Reusable SQL where-clause assembly helpers for positional-parameter style SQL.
 */
public final class SqlWhereClauses {

    private SqlWhereClauses() {
    }

    public static void appendPositionalPlaceholders(StringBuilder sql, List<Object> params, Iterable<?> values) {
        int index = 0;
        for (Object value : values) {
            if (index++ > 0) {
                sql.append(", ");
            }
            sql.append("?");
            params.add(value);
        }
        if (index == 0) {
            throw new IllegalArgumentException("values must not be empty when building SQL placeholders");
        }
    }

    public static void appendInCondition(StringBuilder sql, List<Object> params, String columnName, Iterable<?> values) {
        sql.append(columnName).append(" IN (");
        appendPositionalPlaceholders(sql, params, values);
        sql.append(")");
    }

    public static boolean appendEqualsConditionIfPresent(StringBuilder sql,
                                                         List<Object> params,
                                                         String columnName,
                                                         Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text && text.isBlank()) {
            return false;
        }
        sql.append(" AND ").append(columnName).append(" = ?");
        params.add(value);
        return true;
    }
}
