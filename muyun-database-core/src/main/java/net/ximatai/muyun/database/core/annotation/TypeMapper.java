package net.ximatai.muyun.database.core.annotation;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        mappings.put(String[].class, VARCHAR_ARRAY);
        mappings.put(int[].class, INT_ARRAY);
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

}
