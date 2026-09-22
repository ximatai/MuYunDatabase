package net.ximatai.muyun.database.core.orm;

import java.util.Objects;

public class MigrationChange {
    public enum Risk {
        SAFE_ADDITIVE,
        DATA_VALIDATION_REQUIRED,
        DESTRUCTIVE
    }

    public enum Type {
        CREATE_SCHEMA,
        CREATE_TABLE,
        ADD_TABLE_INHERITANCE,
        SET_TABLE_COMMENT,
        ADD_COLUMN,
        DROP_COLUMN,
        DROP_TEMP_COLUMN,
        ALTER_COLUMN_TYPE,
        ADD_PRIMARY_KEY,
        DROP_PRIMARY_KEY,
        ADD_UNIQUE_CONSTRAINT,
        DROP_UNIQUE_CONSTRAINT,
        ADD_FOREIGN_KEY,
        DROP_FOREIGN_KEY,
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
    private final Risk risk;

    public MigrationChange(Type type, String target, String sql, boolean nonAdditive) {
        this(type, target, sql, nonAdditive ? Risk.DESTRUCTIVE : Risk.SAFE_ADDITIVE);
    }

    public MigrationChange(Type type, String target, String sql, Risk risk) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.target = target;
        this.sql = Objects.requireNonNull(sql, "sql must not be null");
        this.risk = Objects.requireNonNull(risk, "risk must not be null");
    }

    public static MigrationChange additive(Type type, String target, String sql) {
        return new MigrationChange(type, target, sql, Risk.SAFE_ADDITIVE);
    }

    public static MigrationChange nonAdditive(Type type, String target, String sql) {
        return new MigrationChange(type, target, sql, Risk.DESTRUCTIVE);
    }

    public static MigrationChange validationRequired(Type type, String target, String sql) {
        return new MigrationChange(type, target, sql, Risk.DATA_VALIDATION_REQUIRED);
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
        return risk != Risk.SAFE_ADDITIVE;
    }

    public Risk getRisk() {
        return risk;
    }
}
