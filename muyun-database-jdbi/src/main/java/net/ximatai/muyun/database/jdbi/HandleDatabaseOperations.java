package net.ximatai.muyun.database.jdbi;

import net.ximatai.muyun.database.core.IMetaDataLoader;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class HandleDatabaseOperations<K> extends AbstractJdbiDatabaseOperations<K> {

    private final Handle handle;

    HandleDatabaseOperations(Handle handle, IMetaDataLoader metaDataLoader, Class<K> pkType, String pkName) {
        super(metaDataLoader, pkType, pkName);
        this.handle = handle;
    }

    public HandleDatabaseOperations<K> setRowMapper(RowMapper<Map<String, Object>> rowMapper) {
        setRowMapperInternal(rowMapper);
        return this;
    }

    @Override
    public K insert(String sql, Map<String, Object> params) {
        return handle.createUpdate(sql)
                .bindMap(params)
                .executeAndReturnGeneratedKeys(getPKName())
                .mapTo(pkType)
                .one();
    }

    @Override
    public K insertWithPK(String sql, Map<String, Object> params, K pk) {
        handle.createUpdate(sql)
                .bindMap(params)
                .execute();
        return pk;
    }

    @Override
    public List<K> batchInsert(String sql, List<Map<String, Object>> paramsList) {
        List<K> generatedKeys = new ArrayList<>();
        PreparedBatch batch = handle.prepareBatch(sql);

        for (Map<String, Object> params : paramsList) {
            batch.bindMap(params).add();
        }

        batch.executePreparedBatch(getPKName())
                .mapTo(pkType)
                .forEach(generatedKeys::add);

        return generatedKeys;
    }

    @Override
    public Map<String, Object> row(String sql, List<Object> params) {
        Query query = handle.createQuery(sql);
        if (params != null && !params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                query.bind(i, params.get(i));
            }
        }
        return query.map(getRowMapper()).findOne().orElse(null);
    }

    @Override
    public Map<String, Object> row(String sql, Map<String, Object> params) {
        return handle.createQuery(sql)
                .bindMap(params)
                .map(getRowMapper())
                .findOne()
                .orElse(null);
    }

    @Override
    public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
        return handle.createQuery(sql)
                .bindMap(params)
                .map(getRowMapper())
                .list();
    }

    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        Query query = handle.createQuery(sql);
        if (params != null && !params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                query.bind(i, params.get(i));
            }
        }
        return query.map(getRowMapper()).list();
    }

    @Override
    public int update(String sql, Map<String, Object> params) {
        return handle.createUpdate(sql)
                .bindMap(params)
                .execute();
    }

    @Override
    public int update(String sql, List<Object> params) {
        Update update = handle.createUpdate(sql);
        if (params != null && !params.isEmpty()) {
            for (int i = 0; i < params.size(); i++) {
                update.bind(i, params.get(i));
            }
        }
        return update.execute();
    }

    @Override
    public int execute(String sql) {
        return handle.execute(sql);
    }

    @Override
    public int execute(String sql, Object... params) {
        return handle.execute(sql, params);
    }

    @Override
    public int execute(String sql, List<Object> params) {
        return handle.execute(sql, params.toArray());
    }

    @Override
    public Array createArray(List<Object> list, String type) {
        try {
            return handle.getConnection().createArrayOf(type, list.toArray());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
