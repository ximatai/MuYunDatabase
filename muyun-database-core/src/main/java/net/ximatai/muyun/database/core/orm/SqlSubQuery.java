package net.ximatai.muyun.database.core.orm;

import java.util.Map;
import java.util.Objects;

public class SqlSubQuery {
    private final String sql;
    private final Map<String, Object> params;

    private SqlSubQuery(String sql, Map<String, Object> params) {
        this.sql = sql;
        this.params = params;
    }

    public static SqlSubQuery of(String sql, Map<String, Object> params) {
        String safeSql = Objects.requireNonNull(sql, "sql must not be null").trim();
        if (safeSql.isEmpty()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        String normalized = safeSql.toLowerCase();
        if (!normalized.startsWith("select ")) {
            throw new IllegalArgumentException("subquery must start with SELECT");
        }
        if (safeSql.contains(";") || safeSql.contains("--") || safeSql.contains("/*")) {
            throw new IllegalArgumentException("subquery contains unsafe fragment");
        }
        return new SqlSubQuery(safeSql, params == null ? Map.of() : Map.copyOf(params));
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
