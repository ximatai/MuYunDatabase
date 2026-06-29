package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void shouldUseRuntimeTableMetaForCollectionCriteriaAndCodec() {
        CapturingOperations operations = new CapturingOperations();
        operations.queryResult = List.of(Map.of(
                "id", "r-1",
                "json_statuses", "[\"enabled\",\"disabled\"]"
        ));
        TableMeta tableMeta = TableMeta.builder("public", "runtime_record")
                .id("id", "id", ColumnType.VARCHAR, String.class)
                .field("jsonStatuses", "json_statuses", ColumnType.JSON_SET, ColumnType.VARCHAR, Set.class, RuntimeStatus.class)
                .build();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, tableMeta, new RuntimeStatusCodeConverter());

        assertEquals("r-1", gateway.insert(Map.of(
                "id", "r-1",
                "jsonStatuses", new LinkedHashSet<>(List.of(RuntimeStatus.ENABLED, RuntimeStatus.DISABLED))
        )));
        assertEquals(Map.of("id", "r-1", "json_statuses", "[\"enabled\",\"disabled\"]"), operations.insertValues);

        List<Map<String, Object>> records = gateway.query(
                Criteria.of().contains("jsonStatuses", RuntimeStatus.ENABLED).isNotEmpty("jsonStatuses"),
                PageRequest.of(1, 10)
        );

        assertTrue(operations.querySql.contains("jsonb_exists(\"json_statuses\"::jsonb, :p0)"));
        assertEquals("enabled", operations.queryParams.get("p0"));
        assertEquals(List.of(Map.of(
                "id", "r-1",
                "jsonStatuses", new LinkedHashSet<>(List.of(RuntimeStatus.ENABLED, RuntimeStatus.DISABLED))
        )), records);

        List<Map<String, Object>> rawRows = gateway.queryColumns(
                Criteria.of().contains("jsonStatuses", RuntimeStatus.DISABLED),
                PageRequest.of(1, 10)
        );

        assertEquals(List.of(Map.of(
                "id", "r-1",
                "json_statuses", "[\"enabled\",\"disabled\"]"
        )), rawRows);
        assertEquals("disabled", operations.queryParams.get("p0"));

        List<Map<String, Object>> listed = gateway.list(Criteria.of().contains("jsonStatuses", RuntimeStatus.ENABLED));
        assertEquals(List.of(Map.of(
                "id", "r-1",
                "jsonStatuses", new LinkedHashSet<>(List.of(RuntimeStatus.ENABLED, RuntimeStatus.DISABLED))
        )), listed);

        PageResult<Map<String, Object>> rawPage = gateway.pageQueryColumns(
                Criteria.of().contains("jsonStatuses", RuntimeStatus.ENABLED),
                PageRequest.of(1, 10)
        );
        assertEquals(List.of(Map.of(
                "id", "r-1",
                "json_statuses", "[\"enabled\",\"disabled\"]"
        )), rawPage.getRecords());

        long total = gateway.count(Criteria.of().contains("jsonStatuses", RuntimeStatus.ENABLED));
        assertEquals(7L, total);
        assertTrue(operations.countSql.contains("jsonb_exists(\"json_statuses\"::jsonb, :p0)"));
        assertEquals("enabled", operations.countParams.get("p0"));
    }

    @Test
    void shouldApplyRuntimeFieldCodecForSetInsertPatchAndDeleteWhere() {
        CapturingOperations operations = new CapturingOperations();
        TableMeta tableMeta = TableMeta.builder("public", "runtime_record")
                .id("id", "id", ColumnType.VARCHAR, String.class)
                .csvSet("tags", "tags", Set.class, String.class)
                .build();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, tableMeta);

        gateway.insert(Map.of(
                "id", "r-1",
                "tags", new LinkedHashSet<>(List.of("a", "b"))
        ));
        assertEquals(Map.of("id", "r-1", "tags", "a,b"), operations.insertValues);

        gateway.patchWhere(
                Map.of("tags", new LinkedHashSet<>(List.of("c", "d"))),
                Map.of("tags", new LinkedHashSet<>(List.of("a", "b")))
        );
        assertEquals(Map.of("tags", "c,d"), operations.patchValues);
        assertEquals(Map.of("tags", "a,b"), operations.whereValues);

        gateway.deleteWhere(Map.of("tags", new LinkedHashSet<>(List.of("c", "d"))));
        assertEquals(Map.of("tags", "c,d"), operations.whereValues);
    }

    @Test
    void shouldWrapRuntimeFieldCodecFailuresWithOrmException() {
        CapturingOperations operations = new CapturingOperations();
        TableMeta tableMeta = TableMeta.builder("public", "runtime_record")
                .id("id", "id", ColumnType.VARCHAR, String.class)
                .csvSet("tags", "tags", Set.class, String.class)
                .build();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, tableMeta);

        OrmException insertException = assertThrows(
                OrmException.class,
                () -> gateway.insert(Map.of("id", "r-1", "tags", List.of("a,b")))
        );
        assertEquals(OrmException.Code.INVALID_ENTITY, insertException.getCode());

        OrmException whereException = assertThrows(
                OrmException.class,
                () -> gateway.deleteWhere(Map.of("tags", List.of("a,b")))
        );
        assertEquals(OrmException.Code.INVALID_CRITERIA, whereException.getCode());
        assertEquals(0, operations.patchCalls);

        OrmException criteriaException = assertThrows(
                OrmException.class,
                () -> gateway.list(Criteria.of().eq("tags", List.of("a,b")))
        );
        assertEquals(OrmException.Code.INVALID_CRITERIA, criteriaException.getCode());
    }

    @Test
    void shouldWrapRuntimeReadCodecFailuresWithOrmException() {
        CapturingOperations operations = new CapturingOperations();
        operations.queryResult = List.of(Map.of("json_tags", "[not-json]"));
        TableMeta tableMeta = TableMeta.builder("public", "runtime_record")
                .id("id", "id", ColumnType.VARCHAR, String.class)
                .jsonSet("jsonTags", "json_tags", Set.class, String.class)
                .build();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, tableMeta);

        OrmException exception = assertThrows(
                OrmException.class,
                () -> gateway.list(Criteria.of().isNotNull("jsonTags"))
        );

        assertEquals(OrmException.Code.INVALID_ENTITY, exception.getCode());
    }

    @Test
    void shouldResolveReturnedRowsByColumnBeforeFieldName() {
        CapturingOperations operations = new CapturingOperations();
        operations.queryResult = List.of(Map.of("other", "[\"enabled\"]"));
        TableMeta tableMeta = TableMeta.builder("public", "runtime_record")
                .jsonSet("tags", "other", Set.class, RuntimeStatus.class)
                .field("other", "other_col", ColumnType.VARCHAR, String.class)
                .build();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, tableMeta, new RuntimeStatusCodeConverter());

        List<Map<String, Object>> records = gateway.list(Criteria.of().isNotNull("tags"));

        assertEquals(List.of(Map.of(
                "tags", new LinkedHashSet<>(List.of(RuntimeStatus.ENABLED))
        )), records);
    }

    @Test
    void shouldCompileRuntimePostgresArrayCriteriaFromTableMeta() {
        CapturingOperations operations = new CapturingOperations();
        TableMeta tableMeta = TableMeta.builder("public", "runtime_record")
                .id("id", "id", ColumnType.VARCHAR, String.class)
                .array("scores", "scores", ColumnType.INT, List.class, Integer.class)
                .build();
        RuntimeTableGateway gateway = new RuntimeTableGateway(operations, tableMeta);

        gateway.listColumns(Criteria.of()
                .contains("scores", 1)
                .containsAny("scores", List.of(2, 3))
                .containsAll("scores", List.of(4, 5))
                .isEmpty("scores"));

        assertTrue(operations.querySql.contains("\"scores\" @> ARRAY[:p0]::int[]"));
        assertTrue(operations.querySql.contains("\"scores\" && ARRAY[:p1, :p2]::int[]"));
        assertTrue(operations.querySql.contains("\"scores\" @> ARRAY[:p3, :p4]::int[]"));
        assertTrue(operations.querySql.contains("(\"scores\" IS NULL OR cardinality(\"scores\") = 0)"));
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

    private enum RuntimeStatus {
        ENABLED,
        DISABLED
    }

    private static class RuntimeStatusCodeConverter implements DatabaseValueConverter {
        @Override
        public Object toDatabaseValue(Object value) {
            if (value instanceof RuntimeStatus status) {
                return status.name().toLowerCase();
            }
            return DatabaseValueConverter.DEFAULT.toDatabaseValue(value);
        }

        @Override
        public Object fromDatabaseValue(Object value, Class<?> targetType) {
            if (targetType == RuntimeStatus.class && value != null) {
                return RuntimeStatus.valueOf(String.valueOf(value).toUpperCase());
            }
            return DatabaseValueConverter.DEFAULT.fromDatabaseValue(value, targetType);
        }
    }

    static class CapturingOperations implements IDatabaseOperations<Object> {
        private final DBInfo dbInfo = new DBInfo("POSTGRESQL").setName("test_db");
        private Map<String, Object> insertValues;
        private Map<String, Object> patchValues;
        private Map<String, Object> whereValues;
        private String querySql;
        private Map<String, Object> queryParams;
        private String countSql;
        private Map<String, Object> countParams;
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
            this.countParams = Map.copyOf(params);
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
