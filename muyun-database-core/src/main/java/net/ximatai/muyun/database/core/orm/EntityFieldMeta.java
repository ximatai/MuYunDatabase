package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Optional;

public class EntityFieldMeta {
    private final Field field;
    private final String fieldName;
    private final String columnName;
    private final ColumnType columnType;
    private final boolean id;
    private final Optional<Class<?>> collectionElementType;

    public EntityFieldMeta(Field field, String columnName, ColumnType columnType, boolean id) {
        this.field = field;
        this.fieldName = field.getName();
        this.columnName = columnName;
        this.columnType = columnType;
        this.id = id;
        this.collectionElementType = resolveCollectionElementType(field);
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

    public Optional<Class<?>> getCollectionElementType() {
        return collectionElementType;
    }

    private static Optional<Class<?>> resolveCollectionElementType(Field field) {
        if (!Collection.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }

        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return Optional.empty();
        }

        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class<?> elementType)) {
            return Optional.empty();
        }
        return Optional.of(elementType);
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
