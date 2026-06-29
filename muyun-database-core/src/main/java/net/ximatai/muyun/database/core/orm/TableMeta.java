package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class TableMeta implements RuntimeColumnMapper {
    private final String tableName;
    private final String schema;
    private final List<FieldMeta> fields;
    private final FieldMeta idField;
    private final Map<String, FieldMeta> fieldNameMap = new HashMap<>();
    private final Map<String, FieldMeta> columnNameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    TableMeta(String tableName, String schema, List<? extends FieldMeta> fields, FieldMeta idField) {
        this.tableName = requireIdentifier(tableName, "tableName");
        this.schema = schema == null || schema.isBlank() ? null : requireIdentifier(schema, "schema");
        Objects.requireNonNull(fields, "fields must not be null");
        if (fields.isEmpty()) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "table fields must not be empty");
        }
        this.fields = List.copyOf(fields);
        FieldMeta resolvedId = null;
        for (FieldMeta field : fields) {
            Objects.requireNonNull(field, "field must not be null");
            if (field.getFieldName() == null || field.getFieldName().isBlank()) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "fieldName must not be blank");
            }
            if (!SqlIdentifiers.isSafe(field.getColumnName())) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "Unsafe column name: " + field.getColumnName());
            }
            if (field.getColumnType() == null) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "columnType must not be null: " + field.getFieldName());
            }
            validateFieldType(field);
            FieldMeta previousField = fieldNameMap.putIfAbsent(field.getFieldName(), field);
            if (previousField != null) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "Duplicate field name: " + field.getFieldName());
            }
            FieldMeta previousColumn = columnNameMap.putIfAbsent(field.getColumnName(), field);
            if (previousColumn != null) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "Duplicate column name: " + field.getColumnName());
            }
            if (field.isId()) {
                if (resolvedId != null) {
                    throw new OrmException(OrmException.Code.INVALID_MAPPING, "Duplicate id field");
                }
                resolvedId = field;
            }
        }
        if (idField != null) {
            FieldMeta explicitId = fieldNameMap.get(idField.getFieldName());
            if (explicitId == null) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "idField must be one of table fields");
            }
            if (resolvedId != null && resolvedId != explicitId) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "Duplicate id field");
            }
            resolvedId = explicitId;
        }
        this.idField = resolvedId;
    }

    public static TableMeta of(String schema,
                               String tableName,
                               List<? extends FieldMeta> fields,
                               FieldMeta idField) {
        return new TableMeta(tableName, schema, fields, idField);
    }

    public static Builder builder(String schema, String tableName) {
        return new Builder(schema, tableName);
    }

    public static Builder builder(String tableName) {
        return new Builder(null, tableName);
    }

    public String getTableName() {
        return tableName;
    }

    public String getSchema() {
        return schema;
    }

    public List<FieldMeta> getFields() {
        return fields;
    }

    public FieldMeta getIdField() {
        return idField;
    }

    public FieldMeta findByFieldName(String fieldName) {
        return fieldNameMap.get(fieldName);
    }

    public FieldMeta findByColumnName(String columnName) {
        return columnNameMap.get(columnName);
    }

    @Override
    public String resolveColumnName(String fieldOrColumn) {
        FieldMeta byField = fieldNameMap.get(fieldOrColumn);
        if (byField != null) {
            return byField.getColumnName();
        }
        FieldMeta byColumn = columnNameMap.get(fieldOrColumn);
        if (byColumn != null) {
            return byColumn.getColumnName();
        }
        return null;
    }

    @Override
    public String resolveFieldName(String columnName) {
        FieldMeta fieldMeta = columnNameMap.get(columnName);
        return fieldMeta == null ? columnName : fieldMeta.getFieldName();
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !SqlIdentifiers.isSafe(value)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Invalid " + name + ": " + value);
        }
        return value;
    }

    private static void validateFieldType(FieldMeta field) {
        if (field.getColumnType() != ColumnType.ARRAY) {
            return;
        }
        ColumnType elementType = field.getElementColumnType();
        if (elementType == null || elementType == ColumnType.UNKNOWN) {
            throw new OrmException(
                    OrmException.Code.INVALID_MAPPING,
                    "ARRAY field requires elementColumnType: " + field.getFieldName()
            );
        }
        if (!isSupportedArrayElementType(elementType)) {
            throw new OrmException(
                    OrmException.Code.INVALID_MAPPING,
                    "Unsupported ARRAY element type for field " + field.getFieldName() + ": " + elementType
            );
        }
    }

    private static boolean isSupportedArrayElementType(ColumnType elementType) {
        return switch (elementType) {
            case VARCHAR, TEXT, LONGTEXT, INT, BIGINT, BOOLEAN, TIMESTAMP, DATE, NUMERIC -> true;
            default -> false;
        };
    }

    public static final class Builder {
        private final String schema;
        private final String tableName;
        private final List<RuntimeFieldMeta> fields = new ArrayList<>();

        private Builder(String schema, String tableName) {
            this.schema = schema;
            this.tableName = tableName;
        }

        public Builder id(String fieldName, String columnName, ColumnType columnType, Class<?> fieldType) {
            fields.add(new RuntimeFieldMeta(fieldName, columnName, columnType, ColumnType.UNKNOWN,
                    fieldType, null, true));
            return this;
        }

        public Builder field(String fieldName, String columnName, ColumnType columnType, Class<?> fieldType) {
            fields.add(new RuntimeFieldMeta(fieldName, columnName, columnType, ColumnType.UNKNOWN,
                    fieldType, null, false));
            return this;
        }

        public Builder csvSet(String fieldName, String columnName, Class<?> fieldType, Class<?> elementJavaType) {
            return field(fieldName, columnName, ColumnType.SET, ColumnType.UNKNOWN, fieldType, elementJavaType);
        }

        public Builder jsonSet(String fieldName, String columnName, Class<?> fieldType, Class<?> elementJavaType) {
            return field(fieldName, columnName, ColumnType.JSON_SET, ColumnType.UNKNOWN, fieldType, elementJavaType);
        }

        public Builder array(String fieldName,
                             String columnName,
                             ColumnType elementColumnType,
                             Class<?> fieldType,
                             Class<?> elementJavaType) {
            return field(fieldName, columnName, ColumnType.ARRAY, elementColumnType, fieldType, elementJavaType);
        }

        public Builder field(String fieldName,
                             String columnName,
                             ColumnType columnType,
                             ColumnType elementColumnType,
                             Class<?> fieldType,
                             Class<?> elementJavaType) {
            fields.add(new RuntimeFieldMeta(fieldName, columnName, columnType, elementColumnType,
                    fieldType, elementJavaType, false));
            return this;
        }

        public Builder field(RuntimeFieldMeta fieldMeta) {
            fields.add(Objects.requireNonNull(fieldMeta, "fieldMeta must not be null"));
            return this;
        }

        public TableMeta build() {
            RuntimeFieldMeta idField = null;
            for (RuntimeFieldMeta field : fields) {
                if (field.isId()) {
                    idField = field;
                    break;
                }
            }
            return new TableMeta(tableName, schema, fields, idField);
        }
    }
}
