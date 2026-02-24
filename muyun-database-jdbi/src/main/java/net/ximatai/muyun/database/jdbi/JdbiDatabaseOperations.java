package net.ximatai.muyun.database.jdbi;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * JDBI数据库操作实现类
 * 基于JDBI框架实现数据库的CRUD操作和数据类型转换
 */
public class JdbiDatabaseOperations<K> extends AbstractJdbiDatabaseOperations<K> {

    private static final int WRITE_RETRY_TIMES = 3;
    private final Jdbi jdbi;

    public JdbiDatabaseOperations(Jdbi jdbi, JdbiMetaDataLoader metaDataLoader, Class<K> pkType, String pkName) {
        super(metaDataLoader, pkType, pkName);
        this.jdbi = jdbi;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public Class<K> getPkType() {
        return pkType;
    }

    /**
     * 设置自定义行映射器
     */
    public JdbiDatabaseOperations<K> setRowMapper(RowMapper<Map<String, Object>> rowMapper) {
        setRowMapperInternal(rowMapper);
        return this;
    }

    @Override
    public K insertWithPK(String sql, Map<String, Object> params, K pk) {
        withWriteRetry(() -> getJdbi().withHandle(handle ->
                handle.createUpdate(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .execute()
        ));
        return pk;
    }

    @Override
    public K insert(String sql, Map<String, Object> params) {
        return withWriteRetry(() -> getJdbi().withHandle(handle ->
                handle.createUpdate(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .executeAndReturnGeneratedKeys(getPKName()).mapTo(pkType).one()));
    }

    @Override
    public List<K> batchInsert(String sql, List<Map<String, Object>> paramsList) {
        return withWriteRetry(() -> getJdbi().withHandle(handle -> {
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
        }));
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
        return withWriteRetry(() -> getJdbi().withHandle(handle ->
                handle.createUpdate(sql)
                        .attachToHandleForCleanup()
                        .bindMap(params)
                        .execute()));
    }

    @Override
    public int update(String sql, List<Object> params) {
        return withWriteRetry(() -> getJdbi().withHandle(handle -> {
            Update query = handle.createUpdate(sql).attachToHandleForCleanup();
            if (params != null && !params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    query.bind(i, params.get(i));
                }
            }
            return query.execute();
        }));
    }

    @Override
    public int execute(String sql) {
        return withWriteRetry(() -> getJdbi().withHandle(handle -> handle.execute(sql)));
    }

    @Override
    public int execute(String sql, Object... params) {
        return withWriteRetry(() -> getJdbi().withHandle(handle -> handle.execute(sql, params)));
    }

    @Override
    public int execute(String sql, List<Object> params) {
        return withWriteRetry(() -> getJdbi().withHandle(handle -> handle.execute(sql, params.toArray())));
    }

    @Override
    public Array createArray(List<Object> list, String type) {
        try {
            return getJdbi().withHandle(handle -> {
                Connection connection = handle.getConnection();
                return connection.createArrayOf(type, list.toArray());
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T withWriteRetry(Supplier<T> action) {
        RuntimeException last = null;
        for (int i = 0; i < WRITE_RETRY_TIMES; i++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                if (!isRetryableWriteException(ex) || i >= WRITE_RETRY_TIMES - 1) {
                    throw ex;
                }
                last = ex;
                try {
                    Thread.sleep(20L * (i + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last == null ? new IllegalStateException("Unexpected write retry state") : last;
    }

    private boolean isRetryableWriteException(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
                int code = sqlEx.getErrorCode();
                if ("40001".equals(sqlState) || code == 1213 || code == 1205) {
                    return true;
                }
                String message = sqlEx.getMessage();
                if (message != null && (message.contains("Deadlock found")
                        || message.contains("Lock wait timeout exceeded"))) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
