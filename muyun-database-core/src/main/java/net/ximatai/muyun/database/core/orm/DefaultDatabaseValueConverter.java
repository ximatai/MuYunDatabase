package net.ximatai.muyun.database.core.orm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;

final class DefaultDatabaseValueConverter implements DatabaseValueConverter {

    @Override
    public Object toDatabaseValue(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object fromDatabaseValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (targetType.isEnum()) {
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
            return Enum.valueOf((Class<? extends Enum>) targetType, text.toUpperCase(Locale.ROOT));
        }

        if (targetType == String.class) {
            return value.toString();
        }

        if (targetType == int.class || targetType == Integer.class) {
            return asNumber(value).intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return asNumber(value).longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return asNumber(value).doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return asNumber(value).floatValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return asNumber(value).shortValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return asNumber(value).byteValue();
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

        if (targetType == Instant.class && value instanceof Timestamp ts) {
            return ts.toInstant();
        }

        return value;
    }

    private static Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return new BigDecimal(value.toString().trim());
    }
}
