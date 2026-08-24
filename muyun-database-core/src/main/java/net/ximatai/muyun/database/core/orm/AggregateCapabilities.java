package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.math.BigDecimal;

/** Central aggregate capability and result-type rules for runtime table metadata. */
final class AggregateCapabilities {

    private AggregateCapabilities() {
    }

    static void validateGroupBy(FieldMeta fieldMeta, String field) {
        if (fieldMeta != null && !isGroupable(fieldMeta.getColumnType())) {
            throw unsupported("GROUP BY", fieldMeta.getColumnType(), field);
        }
    }

    static void validateSelection(AggregateSelection selection, FieldMeta fieldMeta) {
        if (selection.operation() == AggregateOperation.COUNT || fieldMeta == null) {
            return;
        }
        ColumnType columnType = fieldMeta.getColumnType();
        boolean supported = switch (selection.operation()) {
            case SUM, AVG -> isNumeric(columnType);
            case MIN, MAX -> isComparableScalar(columnType);
            case COUNT -> true;
        };
        if (!supported) {
            throw unsupported(selection.operation().name(), columnType, selection.field());
        }
    }

    static Object normalizeSelectionValue(Object value, AggregateSelection selection, FieldMeta fieldMeta,
                                          DatabaseValueConverter valueConverter) {
        if (value == null) {
            return null;
        }
        return switch (selection.operation()) {
            case COUNT -> toLong(value, selection.key());
            case SUM, AVG -> toBigDecimal(value, selection.key());
            case MIN, MAX -> fieldMeta == null ? value : FieldValueCodec.fromDatabaseValue(value, fieldMeta, valueConverter);
        };
    }

    private static boolean isGroupable(ColumnType columnType) {
        return isComparableScalar(columnType) || columnType == ColumnType.BOOLEAN;
    }

    private static boolean isNumeric(ColumnType columnType) {
        return columnType == ColumnType.INT || columnType == ColumnType.BIGINT || columnType == ColumnType.NUMERIC;
    }

    private static boolean isComparableScalar(ColumnType columnType) {
        return isNumeric(columnType)
                || columnType == ColumnType.VARCHAR
                || columnType == ColumnType.TEXT
                || columnType == ColumnType.LONGTEXT
                || columnType == ColumnType.TIMESTAMP
                || columnType == ColumnType.DATE;
    }

    private static Long toLong(Object value, String key) {
        try {
            return new BigDecimal(String.valueOf(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY,
                    "COUNT result is not a whole number for " + key + ": " + value, ex);
        }
    }

    private static BigDecimal toBigDecimal(Object value, String key) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY,
                    "Numeric aggregate result is invalid for " + key + ": " + value, ex);
        }
    }

    private static OrmException unsupported(String operation, ColumnType columnType, String field) {
        return new OrmException(OrmException.Code.INVALID_CRITERIA,
                operation + " does not support " + columnType + " field: " + field);
    }
}
