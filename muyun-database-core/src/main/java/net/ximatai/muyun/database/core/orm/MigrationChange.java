package net.ximatai.muyun.database.core.orm;

import java.util.Objects;

public class MigrationChange {
    public enum Type {
        CREATE_SCHEMA,
        CREATE_TABLE,
        SET_TABLE_COMMENT,
        ADD_COLUMN,
        DROP_COLUMN,
        DROP_TEMP_COLUMN,
        ALTER_COLUMN_TYPE,
        ADD_PRIMARY_KEY,
        ALTER_COLUMN_NULLABLE,
        ALTER_COLUMN_DEFAULT,
        ALTER_COLUMN_SEQUENCE,
        SET_COLUMN_COMMENT,
        CREATE_INDEX,
        DROP_INDEX,
        RAW_SQL
    }

    private final Type type;
    private final String target;
    private final String sql;
    private final boolean nonAdditive;

    public MigrationChange(Type type, String target, String sql, boolean nonAdditive) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.target = target;
        this.sql = Objects.requireNonNull(sql, "sql must not be null");
        this.nonAdditive = nonAdditive;
    }

    public static MigrationChange additive(Type type, String target, String sql) {
        return new MigrationChange(type, target, sql, false);
    }

    public static MigrationChange nonAdditive(Type type, String target, String sql) {
        return new MigrationChange(type, target, sql, true);
    }

    public Type getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public String getSql() {
        return sql;
    }

    public boolean isNonAdditive() {
        return nonAdditive;
    }
}
