package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EntityMapper {

    private EntityMapper() {
    }

    public static Map<String, Object> toMap(EntityMeta meta, Object entity, boolean includeNull, boolean includeId) {
        Map<String, Object> result = new HashMap<>();

        for (EntityFieldMeta fieldMeta : meta.getFields()) {
            if (!includeId && fieldMeta.isId()) {
                continue;
            }

            Object value = fieldMeta.read(entity);
            if (value == null && !includeNull) {
                continue;
            }
            result.put(fieldMeta.getColumnName(), toDatabaseValue(fieldMeta, value));
        }

        return result;
    }

    public static <T> T fromMap(EntityMeta meta, Map<String, Object> row, Class<T> entityClass) {
        if (row == null) {
            return null;
        }

        T entity = instantiate(entityClass);

        for (EntityFieldMeta fieldMeta : meta.getFields()) {
            Object value = findByColumn(row, fieldMeta.getColumnName());
            if (value == null) {
                continue;
            }

            Object converted = convertValue(value, fieldMeta);
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

    private static Object toDatabaseValue(EntityFieldMeta fieldMeta, Object value) {
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            return toCsvSetValue(value);
        }
        if (!(value instanceof Enum<?> enumValue)) {
            return value;
        }

        Object code = readEnumProperty(enumValue, "code");
        if (code != null) {
            return code;
        }

        Object codeValue = readEnumProperty(enumValue, "value");
        if (codeValue != null) {
            return codeValue;
        }

        return enumValue.name();
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

    private static Object convertValue(Object value, EntityFieldMeta fieldMeta) {
        if (fieldMeta.getColumnType() == ColumnType.SET) {
            return fromCsvSetValue(value, fieldMeta.getFieldType());
        }

        Class<?> targetType = fieldMeta.getFieldType();
        if (value == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (targetType.isEnum()) {
            return convertEnum(value, targetType);
        }

        if (targetType == String.class) {
            return value.toString();
        }

        if (targetType == int.class || targetType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return ((Number) value).longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return ((Number) value).floatValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return ((Number) value).shortValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return ((Number) value).byteValue();
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        }

        if (targetType == BigDecimal.class) {
            return (value instanceof BigDecimal) ? value : new BigDecimal(value.toString());
        }
        if (targetType == BigInteger.class) {
            return (value instanceof BigInteger) ? value : new BigInteger(value.toString());
        }

        if (targetType == Date.class) {
            if (value instanceof Timestamp ts) {
                return new Date(ts.getTime());
            }
            if (value instanceof java.sql.Date sqlDate) {
                return new Date(sqlDate.getTime());
            }
        }

        if (targetType == LocalDate.class && value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        if (targetType == LocalDateTime.class && value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }

        return value;
    }

    private static String toCsvSetValue(Object value) {
        if (value == null) {
            return null;
        }
        LinkedHashSet<String> normalized = normalizeToSet(value);
        return normalized.isEmpty() ? "" : String.join(",", normalized);
    }

    private static Object fromCsvSetValue(Object value, Class<?> targetType) {
        LinkedHashSet<String> normalized = normalizeToSet(value);
        if (Set.class.isAssignableFrom(targetType) || Collection.class.equals(targetType)) {
            return normalized;
        }
        if (targetType.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>(normalized);
        }
        return normalized;
    }

    private static LinkedHashSet<String> normalizeToSet(Object value) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (value == null) {
            return normalized;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return normalized;
            }
            for (String part : text.split(",")) {
                addNormalized(normalized, part);
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addNormalized(normalized, item);
            }
            return normalized;
        }
        addNormalized(normalized, value);
        return normalized;
    }

    private static void addNormalized(LinkedHashSet<String> normalized, Object raw) {
        if (raw == null) {
            return;
        }
        String text = String.valueOf(raw).trim();
        if (!text.isEmpty()) {
            normalized.add(text);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertEnum(Object value, Class<?> targetType) {
        if (!(targetType.isEnum())) {
            return value;
        }
        if (value instanceof Number number) {
            Object[] constants = targetType.getEnumConstants();
            int ordinal = number.intValue();
            if (ordinal >= 0 && ordinal < constants.length) {
                return constants[ordinal];
            }
        }
        String text = String.valueOf(value);

        try {
            return Enum.valueOf((Class<? extends Enum>) targetType, text);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        try {
            return Enum.valueOf((Class<? extends Enum>) targetType, text.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // fall through
        }

        Object[] constants = targetType.getEnumConstants();
        for (Object constant : constants) {
            if (constant == null) {
                continue;
            }
            if (constant.toString().equalsIgnoreCase(text)) {
                return constant;
            }
            Object codeValue = readEnumProperty(constant, "code");
            if (codeValue != null && Objects.equals(String.valueOf(codeValue), text)) {
                return constant;
            }
            Object valueField = readEnumProperty(constant, "value");
            if (valueField != null && Objects.equals(String.valueOf(valueField), text)) {
                return constant;
            }
        }

        throw new IllegalArgumentException("No enum constant " + targetType.getName() + "." + text);
    }

    private static Object readEnumProperty(Object constant, String fieldName) {
        try {
            var field = constant.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(constant);
        } catch (Exception ignored) {
            return null;
        }
    }
}
