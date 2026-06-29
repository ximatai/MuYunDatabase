package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.annotation.TypeMapper;

import java.lang.reflect.Field;
import java.util.Optional;

public class EntityFieldMeta {
    private final Field field;
    private final String fieldName;
    private final String columnName;
    private final ColumnType columnType;
    private final ColumnType elementColumnType;
    private final boolean id;
    private final Optional<Class<?>> collectionElementType;

    public EntityFieldMeta(Field field, String columnName, ColumnType columnType, boolean id) {
        this(field, columnName, columnType, ColumnType.UNKNOWN, id);
    }

    public EntityFieldMeta(Field field,
                           String columnName,
                           ColumnType columnType,
                           ColumnType elementColumnType,
                           boolean id) {
        this.field = field;
        this.fieldName = field.getName();
        this.columnName = columnName;
        this.columnType = columnType;
        this.elementColumnType = elementColumnType == null ? ColumnType.UNKNOWN : elementColumnType;
        this.id = id;
        this.collectionElementType = TypeMapper.inferElementJavaType(field);
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

    public ColumnType getElementColumnType() {
        return elementColumnType;
    }

    public boolean isId() {
        return id;
    }

    public Class<?> getFieldType() {
        return field.getType();
    }

    public Optional<Class<?>> getCollectionElementType() {
        return collectionElementType;
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
