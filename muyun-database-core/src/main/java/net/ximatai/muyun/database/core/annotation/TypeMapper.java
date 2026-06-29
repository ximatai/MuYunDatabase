package net.ximatai.muyun.database.core.annotation;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static net.ximatai.muyun.database.core.builder.ColumnType.*;

public final class TypeMapper {

    private static final Map<Class<?>, ColumnType> JAVA_TO_COLUMN_TYPE = createMappings();

    private TypeMapper() {
    }

    private static Map<Class<?>, ColumnType> createMappings() {
        Map<Class<?>, ColumnType> mappings = new HashMap<>();
        // 字符串类型
        mappings.put(String.class, VARCHAR);

        // 数值类型
        mappings.put(Integer.class, INT);
        mappings.put(int.class, INT);
        mappings.put(Long.class, BIGINT);
        mappings.put(long.class, BIGINT);

        mappings.put(Double.class, NUMERIC);
        mappings.put(double.class, NUMERIC);
        mappings.put(Float.class, NUMERIC);
        mappings.put(float.class, NUMERIC);

        mappings.put(java.math.BigDecimal.class, NUMERIC);
        mappings.put(java.math.BigInteger.class, NUMERIC);

        // 布尔类型
        mappings.put(Boolean.class, BOOLEAN);
        mappings.put(boolean.class, BOOLEAN);

        // 日期时间类型
        mappings.put(java.util.Date.class, TIMESTAMP);
        mappings.put(java.sql.Date.class, DATE);
        mappings.put(java.sql.Timestamp.class, TIMESTAMP);
        mappings.put(java.time.LocalDate.class, DATE);
        mappings.put(java.time.LocalDateTime.class, TIMESTAMP);
        mappings.put(java.time.Instant.class, TIMESTAMP);

        mappings.put(Map.class, JSON);
        mappings.put(Set.class, SET);
        mappings.put(String[].class, ARRAY);
        mappings.put(int[].class, ARRAY);
        return Collections.unmodifiableMap(mappings);
    }

    public static ColumnType inferSqlType(Class<?> fieldType) {
        if (fieldType == null) {
            throw new IllegalArgumentException("fieldType must not be null");
        }
        ColumnType direct = JAVA_TO_COLUMN_TYPE.get(fieldType);
        if (direct != null) {
            return direct;
        }
        if (Set.class.isAssignableFrom(fieldType)) {
            return SET;
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            return JSON;
        }
        return VARCHAR;
    }

    public static Optional<Class<?>> inferElementJavaType(Field field) {
        if (field == null) {
            return Optional.empty();
        }
        Class<?> fieldType = field.getType();
        if (fieldType.isArray()) {
            return Optional.of(fieldType.getComponentType());
        }
        if (!Collection.class.isAssignableFrom(fieldType)) {
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

}
