package net.ximatai.muyun.database.core.orm;

@FunctionalInterface
public interface RawSqlGuard {
    void validate(String sql);

    static RawSqlGuard strict() {
        return sql -> {
            String safeSql = sql.trim();
            String lower = safeSql.toLowerCase();
            if (safeSql.contains(";") || safeSql.contains("--") || safeSql.contains("/*")) {
                throw new IllegalArgumentException("raw sql contains unsafe fragment");
            }
            if (lower.contains("insert ") || lower.contains("update ") || lower.contains("delete ")
                    || lower.contains("drop ") || lower.contains("alter ") || lower.contains("truncate ")
                    || lower.contains("create ") || lower.contains("merge ")) {
                throw new IllegalArgumentException("raw sql contains non-query keyword");
            }
        };
    }
}
