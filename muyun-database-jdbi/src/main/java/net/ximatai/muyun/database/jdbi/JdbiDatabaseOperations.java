package net.ximatai.muyun.database.jdbi;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBTable;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.MapMapper;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * JDBI数据库操作实现类
 * 基于JDBI框架实现数据库的CRUD操作和数据类型转换
 */
public class JdbiDatabaseOperations<K> implements IDatabaseOperations<K> {

    // 日期时间格式化器
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Jdbi jdbi;
    private final JdbiMetaDataLoader metaDataLoader;
    private RowMapper<Map<String, Object>> rowMapper;
    private final static MapMapper MAP_MAPPER = new MapMapper();

    private final Class<K> pkType;
    private final String pkName;

    @Override
    public String getPKName() {
        return pkName;
    }

    public RowMapper<Map<String, Object>> getRowMapper() {
        if (rowMapper == null) {
            return MAP_MAPPER;
        }
        return rowMapper;
    }

    /**
     * 设置自定义行映射器
     *
     * @param rowMapper 行映射器实例
     * @return 当前操作实例
     */
    public JdbiDatabaseOperations<K> setRowMapper(RowMapper<Map<String, Object>> rowMapper) {
        Objects.requireNonNull(rowMapper);
        this.rowMapper = rowMapper;
        return this;
    }

    public JdbiDatabaseOperations(Jdbi jdbi, JdbiMetaDataLoader metaDataLoader, Class<K> pkType, String pkName) {
        this.jdbi = jdbi;
        this.metaDataLoader = metaDataLoader;
        this.pkType = pkType;
        this.pkName = pkName;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    @Override
    public IMetaDataLoader getMetaDataLoader() {
        return metaDataLoader;
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

    /**
     * 将Java对象转换为数据库对应的数据类型
     * 支持数组、数值、布尔、日期时间等类型转换
     *
     * @param value Java对象值
     * @param type  数据库类型名称
     * @return 转换后的数据库值
     */
    public Object getDBValue(Object value, String type) {
        if (value == null) {
            return null;
        }

        // 处理数组类型（以_开头的类型，如_varchar）
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

        // 处理标量类型
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

    @Override
    public K insertWithPK(String sql, Map<String, Object> params, K pk) {
        getJdbi().withHandle(handle ->
                handle.createUpdate(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .execute()
        );
        return pk;
    }

    @Override
    public K insert(String sql, Map<String, Object> params) {
        return getJdbi().withHandle(handle ->
                handle.createUpdate(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .executeAndReturnGeneratedKeys(getPKName()).mapTo(pkType).one());
    }

    @Override
    public List<K> batchInsert(String sql, List<Map<String, Object>> paramsList) {
        return getJdbi().withHandle(handle -> {
            List<K> generatedKeys = new ArrayList<>();
            PreparedBatch batch = handle.prepareBatch(sql);

            for (Map<String, Object> params : paramsList) {
                batch.bindMap(params).add();
            }

            batch.attachToHandleForCleanup()
                    .executePreparedBatch(getPKName())
                    .mapTo(pkType)
                    .forEach(generatedKeys::add);

            return generatedKeys;
        });
    }

    @Override
    public Map<String, Object> row(String sql, Map<String, Object> params) {
        return getJdbi().withHandle(handle -> (Map<String, Object>) handle.createQuery(sql)
                .attachToHandleForCleanup()
                .bindMap(params)
                .map(getRowMapper())
                .findOne()
                .orElse(null));
    }

    @Override
    public Map<String, Object> row(String sql, List<Object> params) {
        return getJdbi().withHandle(handle -> {
            Query query = handle.createQuery(sql).attachToHandleForCleanup();
            if (params != null && !params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    query.bind(i, params.get(i));
                }
            }
            return query.map(getRowMapper()).findOne().orElse(null);
        });
    }

    @Override
    public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
        return getJdbi().withHandle(handle ->
                handle.createQuery(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .map(getRowMapper())
                        .list());
    }

    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        return getJdbi().withHandle(handle -> {
            Query query = handle.createQuery(sql).attachToHandleForCleanup();
            if (params != null && !params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    query.bind(i, params.get(i));
                }
            }
            return query.map(getRowMapper()).list();
        });
    }

    @Override
    public int update(String sql, Map<String, Object> params) {
        return getJdbi().withHandle(handle ->
                handle.createUpdate(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .execute());
    }

    @Override
    public int update(String sql, List<Object> params) {
        return getJdbi().withHandle(handle -> {
            Update query = handle.createUpdate(sql).attachToHandleForCleanup();
            if (params != null && !params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    query.bind(i, params.get(i));
                }
            }
            return query.execute();
        });
    }

    @Override
    public int execute(String sql) {
        return getJdbi().withHandle(handle -> handle.execute(sql));
    }

    @Override
    public int execute(String sql, Object... params) {
        return getJdbi().withHandle(handle -> handle.execute(sql, params));
    }

    @Override
    public int execute(String sql, List<Object> params) {
        return getJdbi().withHandle(handle -> handle.execute(sql, params.toArray()));
    }

    /**
     * 创建数据库数组对象
     *
     * @param list 数据列表
     * @param type 数组元素类型
     * @return SQL数组对象
     */
    public Array createArray(List list, String type) {
        try {
            return getJdbi().withHandle(handle -> {
                Connection connection = handle.getConnection();
                return connection.createArrayOf(type, list.toArray());
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 数据类型转换辅助方法
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

    /**
     * 字符串转换为SQL日期
     */
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

    /**
     * 字符串转换为SQL时间戳
     */
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

    /**
     * 处理日期时间类型转换
     */
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
