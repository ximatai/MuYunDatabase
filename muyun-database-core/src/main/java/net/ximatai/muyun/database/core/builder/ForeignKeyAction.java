package net.ximatai.muyun.database.core.builder;

public enum ForeignKeyAction {
    NO_ACTION("NO ACTION"),
    CASCADE("CASCADE"),
    SET_NULL("SET NULL"),
    RESTRICT("RESTRICT");

    private final String sql;

    ForeignKeyAction(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
