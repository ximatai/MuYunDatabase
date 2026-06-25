package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBSchema;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSimpleEntityManagerTest {

    @Test
    void shouldResolveConditionalUpdateFieldsToColumns() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        SampleRole role = new SampleRole();
        role.setId("r-1");
        role.setTenantId("t-1");
        role.setRoleName("manager");

        assertEquals(1, manager.update(role, Map.of("tenantId", "t-1", "role_name", "manager")));

        assertEquals("sample_schema", operations.schema);
        assertEquals("sample_role", operations.table);
        assertEquals(Map.of("tenant_id", "t-1", "role_name", "manager", "id", "r-1"), operations.where);
    }

    @Test
    void conditionalUpdateShouldReturnZeroWhenNoRowsMatch() {
        CapturingOperations operations = new CapturingOperations();
        operations.updateResult = 0;
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        SampleRole role = new SampleRole();
        role.setId("r-1");
        role.setRoleName("manager");

        assertEquals(0, manager.update(role, Map.of("tenantId", "missing")));
        assertEquals(Map.of("tenant_id", "missing", "id", "r-1"), operations.where);
    }

    @Test
    void shouldResolveConditionalDeleteFieldsToColumns() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        assertEquals(1, manager.deleteById(SampleRole.class, "r-1", Map.of("tenantId", "t-1")));

        assertEquals("sample_schema", operations.schema);
        assertEquals("sample_role", operations.table);
        assertEquals(Map.of("tenant_id", "t-1", "id", "r-1"), operations.where);
    }

    @Test
    void conditionalDeleteShouldReturnZeroWhenNoRowsMatch() {
        CapturingOperations operations = new CapturingOperations();
        operations.deleteResult = 0;
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        assertEquals(0, manager.deleteById(SampleRole.class, "r-1", Map.of("tenantId", "missing")));
        assertEquals(Map.of("tenant_id", "missing", "id", "r-1"), operations.where);
    }

    @Test
    void shouldRejectUnknownConditionalField() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        SampleRole role = new SampleRole();
        role.setId("r-1");

        OrmException ex = assertThrows(OrmException.class, () -> manager.update(role, Map.of("missing", "x")));
        assertEquals(OrmException.Code.INVALID_CRITERIA, ex.getCode());
    }

    @Test
    void countShouldGenerateSelectCountSql() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        long count = manager.count(SampleRole.class, Criteria.of().eq("roleName", "admin"));

        assertNotNull(operations.capturedSql);
        assertTrue(operations.capturedSql.contains("COUNT(*)"), "SQL should contain COUNT(*): " + operations.capturedSql);
        assertTrue(operations.capturedSql.contains("FROM"), "SQL should contain FROM clause: " + operations.capturedSql);
        assertFalse(operations.capturedSql.contains("SELECT *"), "SQL should not contain SELECT *: " + operations.capturedSql);
        assertEquals(5L, count);
    }

    @Test
    void countShouldGenerateSelectCountSqlWithoutCriteria() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        long count = manager.count(SampleRole.class, Criteria.of());

        assertNotNull(operations.capturedSql);
        assertTrue(operations.capturedSql.contains("COUNT(*)"), "SQL should contain COUNT(*): " + operations.capturedSql);
        assertFalse(operations.capturedSql.contains("SELECT *"), "SQL should not contain SELECT *: " + operations.capturedSql);
        assertFalse(operations.capturedSql.contains("WHERE"), "SQL should not contain WHERE without criteria: " + operations.capturedSql);
        assertEquals(5L, count);
    }

    @Test
    void listShouldGenerateUnpagedQuerySql() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        List<SampleRole> records = manager.list(
                SampleRole.class,
                Criteria.of().eq("roleName", "admin"),
                Sort.asc("roleName")
        );

        assertEquals(1, records.size());
        assertEquals("r-1", records.get(0).getId());
        assertNotNull(operations.capturedSql);
        assertTrue(operations.capturedSql.contains("SELECT *"), "SQL should select records: " + operations.capturedSql);
        assertTrue(operations.capturedSql.contains("WHERE"), "SQL should contain WHERE clause: " + operations.capturedSql);
        assertTrue(operations.capturedSql.contains("ORDER BY"), "SQL should contain ORDER BY clause: " + operations.capturedSql);
        assertFalse(operations.capturedSql.contains("LIMIT"), "Unpaged SQL should not contain LIMIT: " + operations.capturedSql);
        assertFalse(operations.capturedSql.contains("OFFSET"), "Unpaged SQL should not contain OFFSET: " + operations.capturedSql);
    }

    @Test
    void existsShouldGenerateSelectOneSql() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        boolean exists = manager.exists(SampleRole.class, "r-1");

        assertNotNull(operations.capturedSql);
        assertTrue(operations.capturedSql.contains("SELECT 1"), "SQL should contain SELECT 1: " + operations.capturedSql);
        assertTrue(operations.capturedSql.contains("LIMIT 1"), "SQL should contain LIMIT 1: " + operations.capturedSql);
        assertTrue(exists);

        boolean notExists = manager.exists(SampleRole.class, "not-found");
        assertFalse(notExists);
    }

    @Test
    void upsertItemShouldPreferAtomicWhenSupported() {
        CapturingOperations operations = new CapturingOperations(true);
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        manager.executeUpsertForTest("sample_schema", "sample_role", Map.of("id", "r-5", "roleName", "new-role"));

        assertTrue(operations.atomicUpsertCalled, "Should call atomicUpsertItem when supported");
        assertFalse(operations.nonAtomicUpsertCalled, "Should NOT call non-atomic upsertItem when atomic is supported");
    }

    @Test
    void upsertItemShouldFallBackToNonAtomicWhenNotSupported() {
        CapturingOperations operations = new CapturingOperations(false);
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        manager.executeUpsertForTest("sample_schema", "sample_role", Map.of("id", "r-5", "roleName", "new-role"));

        assertFalse(operations.atomicUpsertCalled, "Should NOT call atomicUpsertItem when not supported");
        assertTrue(operations.nonAtomicUpsertCalled, "Should call non-atomic upsertItem when atomic is not supported");
    }

    @Test
    void legacyOnlyShouldBypassAtomicEvenWhenSupported() {
        CapturingOperations operations = new CapturingOperations(true);
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations, UpsertStrategy.LEGACY_ONLY);

        manager.executeUpsertForTest("sample_schema", "sample_role", Map.of("id", "r-5", "roleName", "new-role"));

        assertFalse(operations.atomicUpsertCalled, "LEGACY_ONLY should not call atomicUpsertItem");
        assertTrue(operations.legacyUpsertCalled, "LEGACY_ONLY should call legacyUpsertItem");
    }

    @Test
    void upsertItemShouldPropagateAtomicFailureWhenSupported() {
        CapturingOperations operations = new CapturingOperations(true);
        operations.throwOnAtomicUpsert = true;
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        assertThrows(RuntimeException.class, () ->
                manager.executeUpsertForTest("sample_schema", "sample_role", Map.of("id", "r-5", "roleName", "new-role")));

        assertTrue(operations.atomicUpsertCalled, "Should call atomicUpsertItem when supported");
        assertFalse(operations.nonAtomicUpsertCalled, "Should NOT fall back after atomic execution failure");
    }

    @Test
    void entityOperationsShouldUseMappedIdColumn() {
        CapturingOperations operations = new CapturingOperations(true);
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        CustomIdEntity entity = new CustomIdEntity();
        entity.bizId = "biz-1";
        entity.name = "custom-id";

        assertEquals("biz-1", manager.insert(entity));
        assertEquals("biz_id", operations.pkName);

        manager.update(entity);
        assertEquals("biz_id", operations.pkName);

        manager.findById(CustomIdEntity.class, "biz-1");
        assertEquals("biz_id", operations.pkName);

        manager.deleteById(CustomIdEntity.class, "biz-1");
        assertEquals("biz_id", operations.pkName);

        manager.upsert(entity);
        assertEquals("biz_id", operations.pkName);
    }

    @Test
    void legacyUpsertInsertBranchShouldUseMappedIdColumn() {
        CapturingOperations operations = new CapturingOperations(false);
        operations.returnNullOnGet = true;
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations, UpsertStrategy.LEGACY_ONLY);

        CustomIdEntity entity = new CustomIdEntity();
        entity.bizId = "biz-2";
        entity.name = "custom-id";

        assertEquals(1, manager.upsert(entity));
        assertTrue(operations.legacyUpsertCalled);
        assertTrue(operations.insertItemCalled);
        assertEquals("biz_id", operations.pkName);
    }

    @Table(name = "sample_role", schema = "sample_schema")
    static class SampleRole {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tenant_id", length = 32)
        private String tenantId;

        @Column(name = "role_name", length = 64)
        private String roleName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }

    @Table(name = "custom_id_entity", schema = "sample_schema")
    static class CustomIdEntity {
        @Id(name = "biz_id")
        @Column(length = 32)
        String bizId;

        @Column(name = "v_name", length = 64)
        String name;
    }

    static class CapturingOperations implements IDatabaseOperations<Object> {
        private String schema;
        private String table;
        private String pkName;
        private Map<String, Object> where;
        private String capturedSql;
        private Map<String, Object> capturedParams;
        private final boolean supportsAtomic;
        private final DBInfo dbInfo;

        boolean atomicUpsertCalled;
        boolean nonAtomicUpsertCalled;
        boolean legacyUpsertCalled;
        boolean insertItemCalled;
        boolean throwOnAtomicUpsert;
        boolean returnNullOnGet;
        int updateResult = 1;
        int deleteResult = 1;

        CapturingOperations() {
            this(false);
        }

        CapturingOperations(boolean supportsAtomic) {
            this.supportsAtomic = supportsAtomic;
            this.dbInfo = new DBInfo("MYSQL").setName("test_db");
        }

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
        public int patchUpdateItemWhere(String schema, String tableName, Map<String, Object> patchParams, Map<String, Object> whereParams) {
            this.schema = schema;
            this.table = tableName;
            this.pkName = getPKName();
            this.where = Map.copyOf(whereParams);
            return updateResult;
        }

        @Override
        public int patchUpdateItemWhere(String schema,
                                        String tableName,
                                        Map<String, Object> patchParams,
                                        Map<String, Object> whereParams,
                                        String pkName) {
            this.schema = schema;
            this.table = tableName;
            this.pkName = pkName;
            this.where = Map.copyOf(whereParams);
            return updateResult;
        }

        @Override
        public int deleteItemWhere(String schema, String tableName, Map<String, Object> whereParams) {
            this.schema = schema;
            this.table = tableName;
            this.where = Map.copyOf(whereParams);
            return deleteResult;
        }

        @Override
        public Object insertItem(String schema, String tableName, Map<String, Object> params, String pkName) {
            this.schema = schema;
            this.table = tableName;
            this.pkName = pkName;
            this.insertItemCalled = true;
            return params.get(pkName);
        }

        @Override
        public int updateItem(String schema, String tableName, Map<String, Object> params, String pkName) {
            this.schema = schema;
            this.table = tableName;
            this.pkName = pkName;
            return updateResult;
        }

        @Override
        public Map<String, Object> getItem(String schema, String tableName, Object id, String pkName) {
            this.schema = schema;
            this.table = tableName;
            this.pkName = pkName;
            if (returnNullOnGet) {
                return null;
            }
            return Map.of(pkName, id, "v_name", "custom-id");
        }

        @Override
        public int deleteItem(String schema, String tableName, Object id, String pkName) {
            this.schema = schema;
            this.table = tableName;
            this.pkName = pkName;
            return deleteResult;
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
            this.capturedSql = sql;
            return Map.of("1", 1);
        }

        @Override
        public Map<String, Object> row(String sql, Map<String, Object> params) {
            this.capturedSql = sql;
            Object idValue = params != null ? params.get("id") : null;
            if ("not-found".equals(idValue)) {
                return null;
            }
            if (sql != null && sql.contains("COUNT(*)")) {
                return Map.of("total_count", 5L);
            }
            return Map.of("1", 1);
        }

        @Override
        public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
            this.capturedSql = sql;
            this.capturedParams = params == null ? Map.of() : Map.copyOf(params);
            return List.of(Map.of("id", "r-1", "tenant_id", "t-1", "role_name", "admin"));
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

        @Override
        public boolean supportsAtomicUpsert() {
            return supportsAtomic;
        }

        @Override
        public int upsertItem(String schema, String tableName, Map<String, Object> params) {
            this.nonAtomicUpsertCalled = true;
            return 1;
        }

        @Override
        public int upsertItem(String schema, String tableName, Map<String, Object> params, String pkName) {
            this.pkName = pkName;
            this.nonAtomicUpsertCalled = true;
            return 1;
        }

        @Override
        public int legacyUpsertItem(String schema, String tableName, Map<String, Object> params) {
            this.legacyUpsertCalled = true;
            return 1;
        }

        @Override
        public int legacyUpsertItem(String schema, String tableName, Map<String, Object> params, String pkName) {
            this.pkName = pkName;
            this.legacyUpsertCalled = true;
            if (returnNullOnGet) {
                return IDatabaseOperations.super.legacyUpsertItem(schema, tableName, params, pkName);
            }
            return 1;
        }

        @Override
        public int atomicUpsertItem(String schema, String tableName, Map<String, Object> params) {
            this.atomicUpsertCalled = true;
            if (throwOnAtomicUpsert) {
                throw new RuntimeException("atomic upsert failed");
            }
            return 1;
        }

        @Override
        public int atomicUpsertItem(String schema, String tableName, Map<String, Object> params, String pkName) {
            this.pkName = pkName;
            return atomicUpsertItem(schema, tableName, params);
        }
    }
}
