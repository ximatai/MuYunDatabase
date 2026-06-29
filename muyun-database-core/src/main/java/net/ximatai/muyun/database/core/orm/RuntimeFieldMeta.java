package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.util.Objects;
import java.util.Optional;

public final class RuntimeFieldMeta implements FieldMeta {
    private final String fieldName;
    private final String columnName;
    private final ColumnType columnType;
    private final ColumnType elementColumnType;
    private final boolean id;
    private final Class<?> fieldType;
    private final Optional<Class<?>> collectionElementType;

    public RuntimeFieldMeta(String fieldName,
                            String columnName,
                            ColumnType columnType,
                            ColumnType elementColumnType,
                            Class<?> fieldType,
                            Class<?> collectionElementType,
                            boolean id) {
        this.fieldName = requireName(fieldName, "fieldName");
        this.columnName = requireName(columnName, "columnName");
        this.columnType = columnType == null ? ColumnType.UNKNOWN : columnType;
        this.elementColumnType = elementColumnType == null ? ColumnType.UNKNOWN : elementColumnType;
        this.fieldType = fieldType == null ? Object.class : fieldType;
        this.collectionElementType = Optional.ofNullable(collectionElementType);
        this.id = id;
    }

    public static RuntimeFieldMeta of(String fieldName,
                                      String columnName,
                                      ColumnType columnType,
                                      Class<?> fieldType) {
        return new RuntimeFieldMeta(fieldName, columnName, columnType, ColumnType.UNKNOWN, fieldType, null, false);
    }

    public static RuntimeFieldMeta collection(String fieldName,
                                              String columnName,
                                              ColumnType columnType,
                                              ColumnType elementColumnType,
                                              Class<?> fieldType,
                                              Class<?> elementJavaType) {
        return new RuntimeFieldMeta(fieldName, columnName, columnType, elementColumnType, fieldType, elementJavaType, false);
    }

    public RuntimeFieldMeta asId() {
        return new RuntimeFieldMeta(fieldName, columnName, columnType, elementColumnType, fieldType,
                collectionElementType.orElse(null), true);
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    @Override
    public ColumnType getColumnType() {
        return columnType;
    }

    @Override
    public ColumnType getElementColumnType() {
        return elementColumnType;
    }

    @Override
    public boolean isId() {
        return id;
    }

    @Override
    public Class<?> getFieldType() {
        return fieldType;
    }

    @Override
    public Optional<Class<?>> getCollectionElementType() {
        return collectionElementType;
    }

    private static String requireName(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, name + " must not be blank");
        }
        return value;
    }
}
