package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTableGatewayTest {

    @Test
    void shouldMapFieldsForInsertPatchAndDelete() {
        CapturingOperations operations = new CapturingOperations();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, "public", "runtime_record", this::resolveColumn);

        assertEquals("r-1", gateway.insert(Map.of("id", "r-1", "title", "First")));
        assertEquals(Map.of("id", "r-1", "record_title", "First"), operations.insertValues);

        assertEquals(1, gateway.patchWhere(Map.of("title", "Updated"), Map.of("id", "r-1", "version", 1)));
        assertEquals(Map.of("record_title", "Updated"), operations.patchValues);
        assertEquals(Map.of("id", "r-1", "version", 1), operations.whereValues);

        assertEquals(1, gateway.deleteWhere(Map.of("id", "r-1")));
        assertEquals(Map.of("id", "r-1"), operations.whereValues);
    }

    @Test
    void shouldQueryPageAndCountWithCriteriaAndSort() {
        CapturingOperations operations = new CapturingOperations();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, "public", "runtime_record", this::resolveColumn);

        List<Map<String, Object>> records = gateway.query(
                Criteria.of().eq("title", "First"),
                PageRequest.of(2, 10),
                Sort.desc("version")
        );

        assertEquals(List.of(Map.of("id", "r-1")), records);
        assertTrue(operations.querySql.contains("FROM \"public\".\"runtime_record\""));
        assertTrue(operations.querySql.contains("\"record_title\" = :p0"));
        assertTrue(operations.querySql.contains("ORDER BY \"version\" DESC"));
        assertEquals(10, operations.queryParams.get("limit"));
        assertEquals(10, operations.queryParams.get("offset"));

        PageResult<Map<String, Object>> page = gateway.pageQuery(Criteria.of().eq("title", "First"), PageRequest.of(1, 10));

        assertEquals(7L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertTrue(operations.countSql.contains("COUNT(*)"));
    }

    @Test
    void shouldListWithoutPagingSql() {
        CapturingOperations operations = new CapturingOperations();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, "public", "runtime_record", this::resolveColumn);

        List<Map<String, Object>> records = gateway.listColumns(
                Criteria.of().eq("title", "First"),
                Sort.desc("version")
        );

        assertEquals(List.of(Map.of("id", "r-1")), records);
        assertTrue(operations.querySql.contains("FROM \"public\".\"runtime_record\""));
        assertTrue(operations.querySql.contains("\"record_title\" = :p0"));
        assertTrue(operations.querySql.contains("ORDER BY \"version\" DESC"));
        assertTrue(!operations.querySql.contains("LIMIT"), "Unpaged SQL should not contain LIMIT: " + operations.querySql);
        assertTrue(!operations.querySql.contains("OFFSET"), "Unpaged SQL should not contain OFFSET: " + operations.querySql);
    }

    @Test
    void shouldMapQueryRowsToFieldsWhenRuntimeColumnMapperIsProvided() {
        CapturingOperations operations = new CapturingOperations();
        operations.queryResult = List.of(Map.of("id", "r-1", "record_title", "First", "version", 2));
        RuntimeColumnMapper mapper = RuntimeColumnMapper.of(Map.of(
                "id", "id",
                "title", "record_title",
                "version", "version"
        ));
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, "public", "runtime_record", mapper);

        List<Map<String, Object>> records = gateway.query(
                Criteria.of().eq("title", "First"),
                PageRequest.of(1, 10)
        );

        assertEquals(List.of(Map.of("id", "r-1", "title", "First", "version", 2)), records);

        List<Map<String, Object>> columnRecords = gateway.queryColumns(
                Criteria.of().eq("title", "First"),
                PageRequest.of(1, 10)
        );

        assertEquals(List.of(Map.of("id", "r-1", "record_title", "First", "version", 2)), columnRecords);
    }

    @Test
    void runtimeColumnMapperShouldRejectDuplicateColumns() {
        OrmException ex = assertThrows(OrmException.class, () -> RuntimeColumnMapper.of(Map.of(
                "title", "record_title",
                "name", "record_title"
        )));

        assertEquals(OrmException.Code.INVALID_MAPPING, ex.getCode());
    }

    @Test
    void shouldRejectUnknownFieldsBeforeCallingOperations() {
        CapturingOperations operations = new CapturingOperations();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, "public", "runtime_record", this::resolveColumn);

        OrmException ex = assertThrows(OrmException.class, () -> gateway.patchWhere(
                Map.of("missing", "x"),
                Map.of("id", "r-1")
        ));

        assertEquals(OrmException.Code.INVALID_CRITERIA, ex.getCode());
        assertEquals(0, operations.patchCalls);
    }

    @Test
    void shouldRejectInsertWithoutEffectiveFields() {
        CapturingOperations operations = new CapturingOperations();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, "public", "runtime_record", this::resolveColumn);

        OrmException ex = assertThrows(OrmException.class, () -> gateway.insert(Map.of()));

        assertEquals(OrmException.Code.INVALID_ENTITY, ex.getCode());
    }

    private String resolveColumn(String field) {
        return switch (field) {
            case "id" -> "id";
            case "title", "record_title" -> "record_title";
            case "version" -> "version";
            default -> null;
        };
    }

    static class CapturingOperations implements IDatabaseOperations<Object> {
        private final DBInfo dbInfo = new DBInfo("POSTGRESQL").setName("test_db");
        private Map<String, Object> insertValues;
        private Map<String, Object> patchValues;
        private Map<String, Object> whereValues;
        private String querySql;
        private Map<String, Object> queryParams;
        private String countSql;
        private int patchCalls;
        private List<Map<String, Object>> queryResult = List.of(Map.of("id", "r-1"));

        @Override
        public IMetaDataLoader getMetaDataLoader() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DBInfo getDBInfo() {
            return dbInfo;
        }

        @Override
        public String getPKName() {
            return "id";
        }

        @Override
        public Object insertItem(String schema, String tableName, Map<String, Object> params) {
            this.insertValues = Map.copyOf(params);
            return params.get("id");
        }

        @Override
        public int patchUpdateItemWhere(String schema, String tableName, Map<String, Object> patchParams, Map<String, Object> whereParams) {
            this.patchCalls++;
            this.patchValues = Map.copyOf(patchParams);
            this.whereValues = Map.copyOf(whereParams);
            return 1;
        }

        @Override
        public int deleteItemWhere(String schema, String tableName, Map<String, Object> whereParams) {
            this.whereValues = Map.copyOf(whereParams);
            return 1;
        }

        @Override
        public Object insert(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object insertWithPK(String sql, Map<String, Object> params, Object pk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Object> batchInsert(String sql, List<Map<String, Object>> paramsList) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> row(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> row(String sql, Map<String, Object> params) {
            this.countSql = sql;
            return Map.of("total_count", 7L);
        }

        @Override
        public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
            this.querySql = sql;
            this.queryParams = Map.copyOf(params);
            return queryResult;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(String sql, Object... params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Array createArray(List<Object> list, String type) {
            throw new UnsupportedOperationException();
        }
    }
}
