package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.TableWrapper;

import java.util.*;

public class EntityMeta {
    private final Class<?> entityClass;
    private final String tableName;
    private final String schema;
    private final TableWrapper tableWrapper;
    private final List<EntityFieldMeta> fields;
    private final EntityFieldMeta idField;

    private final Map<String, EntityFieldMeta> fieldNameMap = new HashMap<>();
    private final Map<String, EntityFieldMeta> columnNameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public EntityMeta(
            Class<?> entityClass,
            String tableName,
            String schema,
            TableWrapper tableWrapper,
            List<EntityFieldMeta> fields,
            EntityFieldMeta idField
    ) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.schema = schema;
        this.tableWrapper = tableWrapper;
        this.fields = List.copyOf(fields);
        this.idField = idField;

        fields.forEach(field -> {
            fieldNameMap.put(field.getFieldName(), field);
            columnNameMap.put(field.getColumnName(), field);
        });
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSchema() {
        return schema;
    }

    public TableWrapper getTableWrapper() {
        return tableWrapper;
    }

    public List<EntityFieldMeta> getFields() {
        return fields;
    }

    public EntityFieldMeta getIdField() {
        return idField;
    }

    public String getIdColumnName() {
        return idField.getColumnName();
    }

    public EntityFieldMeta findByFieldName(String fieldName) {
        return fieldNameMap.get(fieldName);
    }

    public EntityFieldMeta findByColumnName(String columnName) {
        return columnNameMap.get(columnName);
    }

    public String resolveColumnName(String fieldOrColumn) {
        EntityFieldMeta byField = fieldNameMap.get(fieldOrColumn);
        if (byField != null) {
            return byField.getColumnName();
        }

        EntityFieldMeta byColumn = columnNameMap.get(fieldOrColumn);
        if (byColumn != null) {
            return byColumn.getColumnName();
        }

        return null;
    }
}
