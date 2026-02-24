package net.ximatai.muyun.database.jdbi;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBTable;
import org.jdbi.v3.core.mapper.MapMapper;
import org.jdbi.v3.core.mapper.RowMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Stream;

abstract class AbstractJdbiDatabaseOperations<K> implements IDatabaseOperations<K> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final MapMapper MAP_MAPPER = new MapMapper();

    protected final IMetaDataLoader metaDataLoader;
    protected final Class<K> pkType;
    protected final String pkName;
    protected RowMapper<Map<String, Object>> rowMapper;

    protected AbstractJdbiDatabaseOperations(IMetaDataLoader metaDataLoader, Class<K> pkType, String pkName) {
        this.metaDataLoader = metaDataLoader;
        this.pkType = pkType;
        this.pkName = pkName;
    }

    @Override
    public IMetaDataLoader getMetaDataLoader() {
        return metaDataLoader;
    }

    @Override
    public String getPKName() {
        return pkName;
    }

    public Class<K> getPkType() {
        return pkType;
    }

    protected RowMapper<Map<String, Object>> getRowMapper() {
        return rowMapper == null ? MAP_MAPPER : rowMapper;
    }

    protected void setRowMapperInternal(RowMapper<Map<String, Object>> rowMapper) {
        this.rowMapper = Objects.requireNonNull(rowMapper);
    }

    @Override
    public Map<String, Object> transformDataForDB(DBTable dbTable, Map<String, Object> data) {
        Map<String, Object> transformedData = new HashMap<>(data);
        transformedData.forEach((k, v) -> {
            DBColumn dbColumn = dbTable.getColumn(k);
            if (dbColumn != null) {
                transformedData.put(k, getDBValue(v, dbColumn.getType()));
            }
        });
        return transformedData;
    }

    @Override
    public boolean supportsAtomicUpsert() {
        DBInfo.Type dbType = getDBInfo().getDatabaseType();
        return dbType == DBInfo.Type.MYSQL || dbType == DBInfo.Type.POSTGRESQL;
    }

    @Override
    public int atomicUpsertItem(String schema, String tableName, Map<String, Object> params) {
        DBTable table = getDBInfo().getSchema(schema).getTable(tableName);
        Map<String, Object> transformed = transformDataForDB(table, params);
        String pk = getPKName();
        Object pkValue = Stream.of(pk, pk.toUpperCase(), pk.toLowerCase())
                .map(transformed::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The primary key value must not be null"));

        List<String> columns = transformed.keySet().stream()
                .filter(key -> table.getColumnMap().containsKey(key))
                .toList();
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("No valid columns for atomic upsert: " + tableName);
        }

        DBInfo.Type dbType = getDBInfo().getDatabaseType();
        String sql = switch (dbType) {
            case MYSQL -> buildMySqlAtomicUpsert(schema, tableName, columns, pk);
            case POSTGRESQL -> buildPostgresAtomicUpsert(schema, tableName, columns, pk);
            default -> throw new UnsupportedOperationException("Atomic upsert is not supported for database type: " + dbType);
        };
        transformed.put(pk, pkValue);
        return update(sql, transformed);
    }

    private String buildMySqlAtomicUpsert(String schema, String tableName, List<String> columns, String pk) {
        String columnSql = String.join(", ", columns);
        String valueSql = columns.stream().map(col -> ":" + col).collect(java.util.stream.Collectors.joining(", "));
        List<String> updateColumns = columns.stream()
                .filter(col -> !col.equalsIgnoreCase(pk))
                .toList();
        String updateSql = updateColumns.isEmpty()
                ? pk + "=" + pk
                : updateColumns.stream()
                .map(col -> col + "=VALUES(" + col + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        return "insert into " + schema + "." + tableName + " (" + columnSql + ") values (" + valueSql + ") on duplicate key update " + updateSql;
    }

    private String buildPostgresAtomicUpsert(String schema, String tableName, List<String> columns, String pk) {
        String columnSql = String.join(", ", columns);
        String valueSql = columns.stream().map(col -> ":" + col).collect(java.util.stream.Collectors.joining(", "));
        List<String> updateColumns = columns.stream()
                .filter(col -> !col.equalsIgnoreCase(pk))
                .toList();
        String updateSql = updateColumns.isEmpty()
                ? pk + "=EXCLUDED." + pk
                : updateColumns.stream()
                .map(col -> col + "=EXCLUDED." + col)
                .collect(java.util.stream.Collectors.joining(", "));
        return "insert into " + schema + "." + tableName + " (" + columnSql + ") values (" + valueSql + ") on conflict (" + pk + ") do update set " + updateSql;
    }

    public Object getDBValue(Object value, String type) {
        if (value == null) {
            return null;
        }

        if (type.startsWith("_")) {
            if (value instanceof java.sql.Array) {
                return value;
            }

            Object[] arrayValue;
            if (value instanceof String val) {
                arrayValue = val.split(",");
            } else if (value instanceof List<?> val) {
                arrayValue = val.toArray();
            } else {
                return value;
            }

            String subType = type.substring(1);
            return switch (subType) {
                case "varchar" -> Arrays.stream(arrayValue)
                        .map(Object::toString)
                        .toArray(String[]::new);
                case "int4" -> Arrays.stream(arrayValue)
                        .map(val -> Integer.parseInt(val.toString()))
                        .toArray(Integer[]::new);
                case "bool" -> Arrays.stream(arrayValue)
                        .map(val -> Boolean.parseBoolean(val.toString()))
                        .toArray(Boolean[]::new);
                default -> value;
            };
        }

        return switch (type) {
            case "varchar" -> value.toString();
            case "int8" -> convertToBigInteger(value);
            case "int4", "int2" -> convertToInteger(value);
            case "bool" -> isTrue(value);
            case "date", "timestamp" -> handleDateTimestamp(value);
            case "numeric" -> convertToBigDecimal(value);
            case "bytea" -> convertToByteArray(value);
            default -> value;
        };
    }

    private BigInteger convertToBigInteger(Object value) {
        if (value instanceof String val) {
            return new BigInteger(val);
        } else if (value instanceof Number val) {
            return BigInteger.valueOf(val.longValue());
        }
        throw new IllegalArgumentException("Cannot convert to Bigint: " + value);
    }

    private Integer convertToInteger(Object value) {
        if (value instanceof String val) {
            return Integer.valueOf(val);
        } else if (value instanceof Number val) {
            return val.intValue();
        }
        throw new IllegalArgumentException("Cannot convert to int: " + value);
    }

    private BigDecimal convertToBigDecimal(Object value) {
        if (value instanceof String val && !isBlank(val)) {
            return new BigDecimal(val);
        } else if (value instanceof Number val) {
            return BigDecimal.valueOf(val.doubleValue());
        }
        return null;
    }

    private byte[] convertToByteArray(Object value) {
        if (value instanceof byte[] val) {
            return val;
        }
        return value.toString().getBytes();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isTrue(Object value) {
        return Objects.equals(value, Boolean.TRUE) || "true".equalsIgnoreCase(value.toString());
    }

    public static Date stringToSqlDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty.");
        }
        try {
            LocalDate localDate = LocalDate.parse(dateString.substring(0, 10), DATE_FORMATTER);
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateString);
        }
    }

    public static Timestamp stringToSqlTimestamp(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            if (dateString.length() == 10) {
                dateString += " 00:00:00";
            }
            LocalDateTime localDateTime = LocalDateTime.parse(dateString, DATE_TIME_FORMATTER);
            return Timestamp.valueOf(localDateTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid datetime format: " + dateString);
        }
    }

    public static Timestamp handleDateTimestamp(Object value) {
        if (value instanceof Timestamp val) {
            return val;
        } else if ("".equals(value)) {
            return null;
        } else if (value instanceof LocalDate val) {
            return Timestamp.valueOf(val.atStartOfDay());
        } else if (value instanceof LocalDateTime val) {
            return Timestamp.valueOf(val);
        } else if (value instanceof Date val) {
            return new Timestamp(val.getTime());
        } else if (value instanceof String val) {
            return stringToSqlTimestamp(val);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + value.getClass().getName());
        }
    }
}
