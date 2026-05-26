package net.ximatai.muyun.database.core.orm;

import java.util.Map;

public final class CompiledCriteria {
    private final String sql;
    private final Map<String, Object> params;

    CompiledCriteria(String sql, Map<String, Object> params) {
        this.sql = sql == null ? "" : sql;
        this.params = params == null ? Map.of() : Map.copyOf(params);
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
