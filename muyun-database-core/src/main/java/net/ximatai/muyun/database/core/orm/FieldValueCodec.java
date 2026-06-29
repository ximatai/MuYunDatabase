package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.internal.JsonArrayParser;
import net.ximatai.muyun.database.core.internal.JsonArrayParserLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;

final class FieldValueCodec {

    private FieldValueCodec() {
    }

    static Object toDatabaseValue(EntityFieldMeta fieldMeta,
                                  Object value,
                                  DatabaseValueConverter valueConverter) {
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            return toCsvSetValue(value, valueConverter);
        }
        if (fieldMeta.getColumnType() == ColumnType.JSON_SET) {
            return toJsonSetValue(value, valueConverter);
        }
        return valueConverter.toDatabaseValue(value);
    }

    static Object fromDatabaseValue(Object value,
                                    EntityFieldMeta fieldMeta,
                                    DatabaseValueConverter valueConverter) {
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            return fromCsvSetValue(value, fieldMeta, valueConverter);
        }
        if (fieldMeta.getColumnType() == ColumnType.JSON_SET) {
            return fromJsonSetValue(value, fieldMeta, valueConverter);
        }

        Class<?> targetType = fieldMeta.getFieldType();
        return valueConverter.fromDatabaseValue(value, targetType);
    }

    static String toCollectionElementDatabaseValue(EntityFieldMeta fieldMeta,
                                                   Object value,
                                                   DatabaseValueConverter valueConverter) {
        Object converted = convertCollectionItem(value, valueConverter);
        if (converted == null) {
            return null;
        }
        String text = String.valueOf(converted);
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            text = text.trim();
            if (text.contains(",")) {
                throw new IllegalArgumentException("SET value cannot contain ',' in CSV storage: " + text);
            }
            return text.isEmpty() ? null : text;
        }
        return text;
    }

    private static String toCsvSetValue(Object value, DatabaseValueConverter valueConverter) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> normalized = normalizeToSet(value, true, valueConverter);
        return normalized.isEmpty() ? "" : String.join(",", normalized);
    }

    private static Object fromCsvSetValue(Object value,
                                          EntityFieldMeta fieldMeta,
                                          DatabaseValueConverter valueConverter) {
        LinkedHashSet<String> normalized = normalizeToSet(value, false, DatabaseValueConverter.DEFAULT);
        return adaptCollection(convertElements(normalized, fieldMeta, valueConverter), fieldMeta.getFieldType());
    }

    private static LinkedHashSet<String> normalizeToSet(Object value,
                                                        boolean strictCsvValue,
                                                        DatabaseValueConverter valueConverter) {
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
                addNormalized(normalized, convertCollectionItem(item, valueConverter), strictCsvValue);
            }
            return normalized;
        }
        addNormalized(normalized, valueConverter.toDatabaseValue(value), strictCsvValue);
        return normalized;
    }

    private static Object convertCollectionItem(Object item, DatabaseValueConverter valueConverter) {
        if (item == null) {
            return null;
        }
        return valueConverter.toDatabaseValue(item);
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

    private static String toJsonSetValue(Object value, DatabaseValueConverter valueConverter) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addJsonElement(normalized, convertCollectionItem(item, valueConverter));
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

    private static void addJsonElement(LinkedHashSet<String> normalized, Object raw) {
        if (raw != null) {
            normalized.add(String.valueOf(raw));
        }
    }

    private static Object fromJsonSetValue(Object value,
                                           EntityFieldMeta fieldMeta,
                                           DatabaseValueConverter valueConverter) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof String text) {
            if (text.isBlank()) {
                return adaptCollection(convertElements(result, fieldMeta, valueConverter), fieldMeta.getFieldType());
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
        return adaptCollection(convertElements(result, fieldMeta, valueConverter), fieldMeta.getFieldType());
    }

    private static LinkedHashSet<?> convertElements(LinkedHashSet<String> elements,
                                                    EntityFieldMeta fieldMeta,
                                                    DatabaseValueConverter valueConverter) {
        Optional<Class<?>> elementType = fieldMeta.getCollectionElementType();
        if (elementType.isEmpty()) {
            return elements;
        }

        LinkedHashSet<Object> converted = new LinkedHashSet<>();
        for (String element : elements) {
            converted.add(valueConverter.fromDatabaseValue(element, elementType.get()));
        }
        return converted;
    }

    @SuppressWarnings("unchecked")
    private static Object adaptCollection(LinkedHashSet<?> elements, Class<?> targetType) {
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
        Collection<Object> customCollection = instantiateCollection(targetType);
        if (customCollection != null) {
            customCollection.addAll(elements);
            return customCollection;
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> instantiateCollection(Class<?> targetType) {
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
                return (Collection<Object>) instance;
            }
        } catch (Exception ignored) {
            // fall through and use default LinkedHashSet
        }
        return null;
    }
}
