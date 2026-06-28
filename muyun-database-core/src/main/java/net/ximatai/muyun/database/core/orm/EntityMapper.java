package net.ximatai.muyun.database.core.orm;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public final class EntityMapper {

    private EntityMapper() {
    }

    public static Map<String, Object> toMap(EntityMeta meta, Object entity, boolean includeNull, boolean includeId) {
        return toMap(meta, entity, includeNull, includeId, DatabaseValueConverter.DEFAULT);
    }

    public static Map<String, Object> toMap(EntityMeta meta,
                                            Object entity,
                                            boolean includeNull,
                                            boolean includeId,
                                            DatabaseValueConverter valueConverter) {
        DatabaseValueConverter converter = valueConverter == null ? DatabaseValueConverter.DEFAULT : valueConverter;
        Map<String, Object> result = new HashMap<>();

        for (EntityFieldMeta fieldMeta : meta.getFields()) {
            if (!includeId && fieldMeta.isId()) {
                continue;
            }

            Object value = fieldMeta.read(entity);
            if (value == null && !includeNull) {
                continue;
            }
            result.put(fieldMeta.getColumnName(), FieldValueCodec.toDatabaseValue(fieldMeta, value, converter));
        }

        return result;
    }

    public static <T> T fromMap(EntityMeta meta, Map<String, Object> row, Class<T> entityClass) {
        return fromMap(meta, row, entityClass, DatabaseValueConverter.DEFAULT);
    }

    public static <T> T fromMap(EntityMeta meta,
                                Map<String, Object> row,
                                Class<T> entityClass,
                                DatabaseValueConverter valueConverter) {
        if (row == null) {
            return null;
        }
        DatabaseValueConverter converter = valueConverter == null ? DatabaseValueConverter.DEFAULT : valueConverter;

        T entity = instantiate(entityClass);

        for (EntityFieldMeta fieldMeta : meta.getFields()) {
            Object value = findByColumn(row, fieldMeta.getColumnName());
            if (value == null) {
                continue;
            }

            Object converted = FieldValueCodec.fromDatabaseValue(value, fieldMeta, converter);
            fieldMeta.write(entity, converted);
        }

        return entity;
    }

    private static Object findByColumn(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (columnName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static <T> T instantiate(Class<T> entityClass) {
        try {
            Constructor<T> constructor = entityClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new OrmException(
                    OrmException.Code.INVALID_ENTITY,
                    "Entity must provide a no-args constructor: " + entityClass.getName(),
                    e
            );
        }
    }
}
