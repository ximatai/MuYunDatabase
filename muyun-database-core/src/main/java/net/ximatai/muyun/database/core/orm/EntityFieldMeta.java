package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.lang.reflect.Field;

public class EntityFieldMeta {
    private final Field field;
    private final String fieldName;
    private final String columnName;
    private final ColumnType columnType;
    private final boolean id;

    public EntityFieldMeta(Field field, String columnName, ColumnType columnType, boolean id) {
        this.field = field;
        this.fieldName = field.getName();
        this.columnName = columnName;
        this.columnType = columnType;
        this.id = id;
        this.field.setAccessible(true);
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getColumnName() {
        return columnName;
    }

    public ColumnType getColumnType() {
        return columnType;
    }

    public boolean isId() {
        return id;
    }

    public Class<?> getFieldType() {
        return field.getType();
    }

    public Object read(Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Failed to read field: " + fieldName, e);
        }
    }

    public void write(Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Failed to write field: " + fieldName, e);
        }
    }
}
