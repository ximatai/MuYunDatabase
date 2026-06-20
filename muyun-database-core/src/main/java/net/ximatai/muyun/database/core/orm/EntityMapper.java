package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.internal.JsonArrayParser;
import net.ximatai.muyun.database.core.internal.JsonArrayParserLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

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
            result.put(fieldMeta.getColumnName(), toDatabaseValue(fieldMeta, value, converter));
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

            Object converted = convertValue(value, fieldMeta, converter);
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

    private static Object toDatabaseValue(EntityFieldMeta fieldMeta,
                                          Object value,
                                          DatabaseValueConverter valueConverter) {
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            return toCsvSetValue(value);
        }
        if (fieldMeta.getColumnType() == ColumnType.JSON_SET) {
            return toJsonSetValue(value);
        }
        return valueConverter.toDatabaseValue(value);
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

    private static Object convertValue(Object value,
                                       EntityFieldMeta fieldMeta,
                                       DatabaseValueConverter valueConverter) {
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            return fromCsvSetValue(value, fieldMeta.getFieldType());
        }
        if (fieldMeta.getColumnType() == ColumnType.JSON_SET) {
            return fromJsonSetValue(value, fieldMeta.getFieldType());
        }

        Class<?> targetType = fieldMeta.getFieldType();
        return valueConverter.fromDatabaseValue(value, targetType);
    }

    private static String toCsvSetValue(Object value) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> normalized = normalizeToSet(value, true);
        return normalized.isEmpty() ? "" : String.join(",", normalized);
    }

    private static Object fromCsvSetValue(Object value, Class<?> targetType) {
        LinkedHashSet<String> normalized = normalizeToSet(value, false);
        return adaptCollection(normalized, targetType);
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> instantiateCollection(Class<?> targetType) {
        if (!Collection.class.isAssignableFrom(targetType)
                || targetType.isInterface()
                || Modifier.isAbstract(targetType.getModifiers())) {
            return null;
        }
        try {
            Constructor<?> constructor = targetType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            if (instance instanceof Collection<?>) {
                return (Collection<String>) instance;
            }
        } catch (Exception ignored) {
            // fall through and use default LinkedHashSet
        }
        return null;
    }

    private static LinkedHashSet<String> normalizeToSet(Object value, boolean strictCsvValue) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (value == null) {
            return normalized;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return normalized;
            }
            for (String part : text.split(",")) {
                addNormalized(normalized, part, strictCsvValue);
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addNormalized(normalized, item, strictCsvValue);
            }
            return normalized;
        }
        addNormalized(normalized, value, strictCsvValue);
        return normalized;
    }

    private static void addNormalized(LinkedHashSet<String> normalized, Object raw, boolean strictCsvValue) {
        if (raw == null) {
            return;
        }
        String text = String.valueOf(raw).trim();
        if (strictCsvValue && text.contains(",")) {
            throw new IllegalArgumentException("SET value cannot contain ',' in CSV storage: " + text);
        }
        if (!text.isEmpty()) {
            normalized.add(text);
        }
    }

    private static String toJsonSetValue(Object value) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    normalized.add(String.valueOf(item));
                }
            }
        } else if (value instanceof String text && !text.isBlank()) {
            String content = text.trim();
            if (content.startsWith("[")) {
                JsonArrayParser parser = JsonArrayParserLoader.get();
                return parser.serialize(parser.parse(content));
            }
            normalized.add(content);
        } else if (value != null) {
            normalized.add(String.valueOf(value));
        }
        JsonArrayParser parser = JsonArrayParserLoader.get();
        return parser.serialize(new ArrayList<>(normalized));
    }

    private static Object fromJsonSetValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof String text) {
            if (text.isBlank()) {
                return adaptCollection(result, targetType);
            }
            JsonArrayParser parser = JsonArrayParserLoader.get();
            result.addAll(parser.parse(text));
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        } else {
            result.add(String.valueOf(value));
        }
        return adaptCollection(result, targetType);
    }

    @SuppressWarnings("unchecked")
    private static Object adaptCollection(LinkedHashSet<String> elements, Class<?> targetType) {
        if (Collection.class.isAssignableFrom(targetType) && targetType.isInterface()) {
            if (List.class.isAssignableFrom(targetType)) {
                return new ArrayList<>(elements);
            }
            if (Queue.class.isAssignableFrom(targetType)) {
                return new LinkedList<>(elements);
            }
            return elements;
        }
        if (targetType.isAssignableFrom(LinkedHashSet.class)) {
            return elements;
        }
        if (targetType.isAssignableFrom(TreeSet.class)
                || SortedSet.class.isAssignableFrom(targetType)
                || NavigableSet.class.isAssignableFrom(targetType)) {
            return new TreeSet<>(elements);
        }
        if (targetType.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>(elements);
        }
        if (targetType.isAssignableFrom(LinkedList.class)) {
            return new LinkedList<>(elements);
        }
        Collection<String> customCollection = instantiateCollection(targetType);
        if (customCollection != null) {
            customCollection.addAll(elements);
            return customCollection;
        }
        return elements;
    }

}
