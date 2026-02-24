package net.ximatai.muyun.database.core.orm;

import java.util.Map;
import java.util.Objects;

public class SqlRawCondition {
    private static final RawSqlGuard DEFAULT_GUARD = RawSqlGuard.strict();

    private final String sql;
    private final Map<String, Object> params;

    private SqlRawCondition(String sql, Map<String, Object> params) {
        this.sql = sql;
        this.params = params;
    }

    public static SqlRawCondition of(String sql, Map<String, Object> params) {
        return of(sql, params, DEFAULT_GUARD);
    }

    public static SqlRawCondition of(String sql, Map<String, Object> params, RawSqlGuard guard) {
        String safeSql = Objects.requireNonNull(sql, "sql must not be null").trim();
        if (safeSql.isEmpty()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        Objects.requireNonNull(guard, "guard must not be null").validate(safeSql);
        return new SqlRawCondition(safeSql, params == null ? Map.of() : Map.copyOf(params));
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
