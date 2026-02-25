package net.ximatai.muyun.database.core.annotation;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static net.ximatai.muyun.database.core.builder.ColumnType.*;

public class TypeMapper {

    private static final Map<Class<?>, ColumnType> JAVA_TO_COLUMN_TYPE = new HashMap<>();

    static {
        // 字符串类型
        JAVA_TO_COLUMN_TYPE.put(String.class, VARCHAR);

        // 数值类型
        JAVA_TO_COLUMN_TYPE.put(Integer.class, INT);
        JAVA_TO_COLUMN_TYPE.put(int.class, INT);
        JAVA_TO_COLUMN_TYPE.put(Long.class, BIGINT);
        JAVA_TO_COLUMN_TYPE.put(long.class, BIGINT);

        JAVA_TO_COLUMN_TYPE.put(Double.class, NUMERIC);
        JAVA_TO_COLUMN_TYPE.put(double.class, NUMERIC);
        JAVA_TO_COLUMN_TYPE.put(Float.class, NUMERIC);
        JAVA_TO_COLUMN_TYPE.put(float.class, NUMERIC);

        JAVA_TO_COLUMN_TYPE.put(java.math.BigDecimal.class, NUMERIC);
        JAVA_TO_COLUMN_TYPE.put(java.math.BigInteger.class, NUMERIC);

        // 布尔类型
        JAVA_TO_COLUMN_TYPE.put(Boolean.class, BOOLEAN);
        JAVA_TO_COLUMN_TYPE.put(boolean.class, BOOLEAN);

        // 日期时间类型
        JAVA_TO_COLUMN_TYPE.put(java.util.Date.class, TIMESTAMP);
        JAVA_TO_COLUMN_TYPE.put(java.sql.Date.class, DATE);
        JAVA_TO_COLUMN_TYPE.put(java.sql.Timestamp.class, TIMESTAMP);
        JAVA_TO_COLUMN_TYPE.put(java.time.LocalDate.class, DATE);
        JAVA_TO_COLUMN_TYPE.put(java.time.LocalDateTime.class, TIMESTAMP);
        JAVA_TO_COLUMN_TYPE.put(java.time.Instant.class, TIMESTAMP);

        JAVA_TO_COLUMN_TYPE.put(Map.class, JSON);
        JAVA_TO_COLUMN_TYPE.put(Set.class, SET);
        JAVA_TO_COLUMN_TYPE.put(HashSet.class, SET);
        JAVA_TO_COLUMN_TYPE.put(LinkedHashSet.class, SET);
        JAVA_TO_COLUMN_TYPE.put(String[].class, VARCHAR_ARRAY);
        JAVA_TO_COLUMN_TYPE.put(int[].class, INT_ARRAY);
    }

    public static ColumnType inferSqlType(Class<?> fieldType) {
        return JAVA_TO_COLUMN_TYPE.getOrDefault(fieldType, VARCHAR);
    }

}
