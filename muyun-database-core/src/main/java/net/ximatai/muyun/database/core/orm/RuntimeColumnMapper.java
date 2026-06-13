package net.ximatai.muyun.database.core.orm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bidirectional field/column mapper for runtime-defined records.
 */
public interface RuntimeColumnMapper extends CriteriaColumnResolver {

    String resolveFieldName(String columnName);

    static RuntimeColumnMapper of(Map<String, String> fieldToColumn) {
        Objects.requireNonNull(fieldToColumn, "fieldToColumn must not be null");
        Map<String, String> fields = new LinkedHashMap<>(fieldToColumn);
        Map<String, String> columns = new LinkedHashMap<>();
        fields.forEach((field, column) -> {
            String previous = columns.putIfAbsent(column, field);
            if (previous != null) {
                throw new OrmException(
                        OrmException.Code.INVALID_MAPPING,
                        "Duplicate runtime column mapping: " + previous + " and " + field + " -> " + column
                );
            }
        });

        return new RuntimeColumnMapper() {
            @Override
            public String resolveColumnName(String fieldName) {
                String column = fields.get(fieldName);
                if (column != null) {
                    return column;
                }
                if (columns.containsKey(fieldName)) {
                    return fieldName;
                }
                return null;
            }

            @Override
            public String resolveFieldName(String columnName) {
                return columns.getOrDefault(columnName, columnName);
            }
        };
    }
}
