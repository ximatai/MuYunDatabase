package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.metadata.*;
import net.ximatai.muyun.database.core.orm.*;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleEntityManagerUnitTest {

    @Test
    void testSimpleOrmCrudFlow() {
        InMemoryOperations operations = InMemoryOperations.emptyMeta();
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations);

        DemoUser user = new DemoUser();
        user.setName("alice");
        user.setAge(18);

        String id = entityManager.insert(user);
        assertNotNull(id);
        assertEquals(id, user.id);

        DemoUser loaded = entityManager.findById(DemoUser.class, id);
        assertNotNull(loaded);
        assertEquals("alice", loaded.getName());
        assertEquals(18, loaded.getAge());

        loaded.setAge(20);
        int updated = entityManager.update(loaded);
        assertEquals(1, updated);

        DemoUser updatedUser = entityManager.findById(DemoUser.class, id);
        assertNotNull(updatedUser);
        assertEquals(20, updatedUser.getAge());

        updatedUser.setName("alice-2");
        int upserted = entityManager.upsert(updatedUser);
        assertEquals(1, upserted);

        DemoUser upsertedUser = entityManager.findById(DemoUser.class, id);
        assertNotNull(upsertedUser);
        assertEquals("alice-2", upsertedUser.getName());

        int deleted = entityManager.deleteById(DemoUser.class, id);
        assertEquals(1, deleted);

        DemoUser deletedUser = entityManager.findById(DemoUser.class, id);
        assertNull(deletedUser);
    }

    @Test
    void testUpdateIgnoreNulls() {
        InMemoryOperations operations = InMemoryOperations.emptyMeta();
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations);

        DemoUser user = new DemoUser();
        user.setName("alice");
        user.setAge(18);
        String id = entityManager.insert(user);

        DemoUser patch = new DemoUser();
        patch.id = id;
        patch.setName(null);
        patch.setAge(21);

        int updated = entityManager.update(patch, NullUpdateStrategy.IGNORE_NULLS);
        assertEquals(1, updated);

        DemoUser loaded = entityManager.findById(DemoUser.class, id);
        assertNotNull(loaded);
        assertEquals("alice", loaded.getName());
        assertEquals(21, loaded.getAge());
    }

    @Test
    void testEnsureTableDryRun() {
        InMemoryOperations operations = InMemoryOperations.emptyMeta();
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations);

        MigrationResult result = entityManager.ensureTable(DemoUser.class, MigrationOptions.dryRun());

        assertTrue(result.isDryRun());
        assertTrue(result.isChanged());
        assertFalse(result.getStatements().isEmpty());
        assertTrue(result.getStatements().stream().anyMatch(sql ->
                sql.toLowerCase().contains("create table") && sql.contains("demo_user")));
        assertTrue(operations.getExecutedSql().isEmpty());
    }

    @Test
    void testBuildUpdateSqlKeepsOriginalNamedParams() {
        InMemoryOperations operations = InMemoryOperations.withExistingStrictTable();
        String sql = operations.buildUpdateSql(
                "public",
                "demo_user_strict",
                Map.of("id", "u-1", "v_name", "alice", "age", 18),
                "id"
        );

        assertTrue(sql.contains(":v_name"));
        assertTrue(sql.contains(":age"));
        assertTrue(sql.contains(":id"));
        assertFalse(sql.contains(":p_0"));
    }

    @Test
    void testBuildPatchUpdateSqlKeepsOriginalNamedParams() {
        InMemoryOperations operations = InMemoryOperations.withExistingStrictTable();
        String sql = operations.buildPatchUpdateSql(
                "public",
                "demo_user_strict",
                Map.of("v_name", "alice"),
                "id",
                "__pk"
        );

        assertTrue(sql.contains(":v_name"));
        assertTrue(sql.contains(":__pk"));
        assertFalse(sql.contains(":p_0"));
    }

    @Test
    void testEnsureTableStrictRejectsNonAdditiveChanges() {
        InMemoryOperations operations = InMemoryOperations.withExistingStrictTable();
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations);

        OrmException exception = assertThrows(
                OrmException.class,
                () -> entityManager.ensureTable(DemoUserStrict.class, MigrationOptions.dryRunStrict())
        );

        assertEquals(OrmException.Code.STRICT_MIGRATION_REJECTED, exception.getCode());
    }

    @Test
    void testUpsertStrategyAtomicOnlyRejectsUnsupportedOperations() {
        InMemoryOperations operations = InMemoryOperations.emptyMeta().withAtomicSupport(false, false);
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations, UpsertStrategy.ATOMIC_ONLY);

        DemoUser user = new DemoUser();
        user.id = "u1";
        user.setName("alice");
        user.setAge(18);

        OrmException exception = assertThrows(OrmException.class, () -> entityManager.upsert(user));
        assertEquals(OrmException.Code.INVALID_MAPPING, exception.getCode());
    }

    @Test
    void testUpsertStrategyAtomicPreferredFallsBackToLegacy() {
        InMemoryOperations operations = InMemoryOperations.emptyMeta().withAtomicSupport(true, true);
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations, UpsertStrategy.ATOMIC_PREFERRED);

        DemoUser user = new DemoUser();
        user.id = "u2";
        user.setName("alice");
        user.setAge(18);
        entityManager.insert(user);

        user.setAge(21);
        int affected = entityManager.upsert(user);
        assertEquals(1, affected);

        assertTrue(operations.atomicUpsertCalled);
        DemoUser loaded = entityManager.findById(DemoUser.class, "u2");
        assertNotNull(loaded);
        assertEquals(21, loaded.getAge());
    }

    @Test
    void testCriteriaGetClausesIncludesNestedClauses() {
        Criteria criteria = Criteria.of()
                .eq("name", "alice")
                .orGroup(group -> group.gt("age", 18).andGroup(nested -> nested.lt("score", 100)));

        List<CriteriaClause> clauses = criteria.getClauses();
        assertEquals(3, clauses.size());
    }

    @Test
    void testCriteriaSupportsNotInOperator() {
        Criteria criteria = Criteria.of().notIn("age", List.of(18, 20));
        List<CriteriaClause> clauses = criteria.getClauses();

        assertEquals(1, clauses.size());
        assertEquals(CriteriaOperator.NOT_IN, clauses.getFirst().getOperator());
        assertEquals(List.of(18, 20), clauses.getFirst().getValues());
    }

    @Test
    void testPageResultUnknownTotalSemantics() {
        PageRequest pageRequest = PageRequest.of(2, 10);
        PageResult<String> page = PageResult.unknownTotal(List.of("a", "b"), pageRequest);

        assertEquals(PageResult.UNKNOWN_TOTAL, page.getTotal());
        assertEquals(PageResult.UNKNOWN_PAGES, page.getPages());
        assertFalse(page.isTotalKnown());
        assertEquals(2, page.getPageNum());
        assertEquals(10, page.getPageSize());
    }

    @Test
    void testSqlRawConditionRejectsUnsafeSql() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlRawCondition.of("age > :age; delete from t", Map.of("age", 1)));
    }

    @Test
    void testSqlRawConditionUsesCustomGuard() {
        RawSqlGuard denyLike = sql -> {
            if (sql.toLowerCase().contains(" like ")) {
                throw new IllegalArgumentException("LIKE is not allowed");
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> SqlRawCondition.of("name like :name", Map.of("name", "a%"), denyLike));
    }

    @Test
    void testEntityMapperWritesEnumCodeValue() {
        InMemoryOperations operations = InMemoryOperations.emptyMeta();
        SimpleEntityManager entityManager = new DefaultSimpleEntityManager(operations);

        DemoTask task = new DemoTask();
        task.setStatus(DemoStatus.ACTIVE);

        String id = entityManager.insert(task);
        assertNotNull(id);

        Map<Object, Map<String, Object>> table = operations.tables.get("public.demo_task");
        assertNotNull(table);
        Map<String, Object> row = table.get(id);
        assertNotNull(row);
        assertEquals("A", row.get("status"));
    }

    @Table(name = "demo_user")
    public static class DemoUser {

        @Id
        @Column
        public String id;

        @Column(name = "v_name")
        private String name;

        @Column
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    @Table(name = "demo_user_strict")
    public static class DemoUserStrict {

        @Id
        @Column
        public String id;

        @Column(name = "v_name", length = 64)
        private String name;

        @Column
        private int age;
    }

    @Table(name = "demo_task")
    public static class DemoTask {
        @Id
        @Column
        public String id;

        @Column
        private DemoStatus status;

        public DemoStatus getStatus() {
            return status;
        }

        public void setStatus(DemoStatus status) {
            this.status = status;
        }
    }

    public enum DemoStatus {
        ACTIVE("A"),
        INACTIVE("I");

        private final String code;

        DemoStatus(String code) {
            this.code = code;
        }
    }

    private static class InMemoryOperations implements IDatabaseOperations<Object> {

        private final AtomicLong sequence = new AtomicLong(1L);
        private final Map<String, Map<Object, Map<String, Object>>> tables = new ConcurrentHashMap<>();
        private final List<String> executedSql = new ArrayList<>();
        private final StubMetaDataLoader metaLoader;
        private boolean atomicUpsertSupported;
        private boolean atomicUpsertThrow;
        private boolean atomicUpsertCalled;

        private InMemoryOperations(StubMetaDataLoader metaLoader) {
            this.metaLoader = metaLoader;
        }

        static InMemoryOperations emptyMeta() {
            DBInfo info = new DBInfo("PostgreSQL").setName("testdb");
            return new InMemoryOperations(new StubMetaDataLoader(info));
        }

        static InMemoryOperations withExistingStrictTable() {
            DBInfo info = new DBInfo("PostgreSQL").setName("testdb");
            DBSchema schema = new DBSchema("public");
            info.addSchema(schema);

            StubMetaDataLoader loader = new StubMetaDataLoader(info);
            DBTable table = new DBTable(loader).setSchema("public").setName("demo_user_strict");
            schema.addTable(table);

            Map<String, DBColumn> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            columns.put("id", column("id", "varchar", true, true, false, null));
            columns.put("v_name", column("v_name", "varchar", true, false, false, 20));
            columns.put("age", column("age", "int", true, false, false, null));

            loader.putColumns("public", "demo_user_strict", columns);
            loader.putIndexes("public", "demo_user_strict", List.of());

            return new InMemoryOperations(loader);
        }

        List<String> getExecutedSql() {
            return executedSql;
        }

        InMemoryOperations withAtomicSupport(boolean supported, boolean throwOnAtomicUpsert) {
            this.atomicUpsertSupported = supported;
            this.atomicUpsertThrow = throwOnAtomicUpsert;
            return this;
        }

        @Override
        public IMetaDataLoader getMetaDataLoader() {
            return metaLoader;
        }

        @Override
        public String getPKName() {
            return "id";
        }

        @Override
        public String getDefaultSchemaName() {
            return "public";
        }

        @Override
        public Object insertItem(String schema, String tableName, Map<String, Object> params) {
            Object id = params.get(getPKName());
            if (id == null) {
                id = String.valueOf(sequence.getAndIncrement());
            }

            Map<String, Object> row = new HashMap<>(params);
            row.put(getPKName(), id);
            table(schema, tableName).put(id, row);
            return id;
        }

        @Override
        public int updateItem(String schema, String tableName, Map<String, Object> params) {
            Object id = params.get(getPKName());
            if (id == null) {
                throw new IllegalArgumentException("id is required");
            }

            Map<Object, Map<String, Object>> table = table(schema, tableName);
            Map<String, Object> row = table.get(id);
            if (row == null) {
                return 0;
            }

            row.putAll(params);
            return 1;
        }

        @Override
        public Map<String, Object> getItem(String schema, String tableName, Object id) {
            Map<String, Object> row = table(schema, tableName).get(id);
            return row == null ? null : new HashMap<>(row);
        }

        @Override
        public int deleteItem(String schema, String tableName, Object id) {
            return table(schema, tableName).remove(id) != null ? 1 : 0;
        }

        @Override
        public boolean supportsAtomicUpsert() {
            return atomicUpsertSupported;
        }

        @Override
        public int atomicUpsertItem(String schema, String tableName, Map<String, Object> params) {
            atomicUpsertCalled = true;
            if (atomicUpsertThrow) {
                throw new RuntimeException("atomic upsert failed");
            }
            return upsertItem(schema, tableName, params);
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
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
            executedSql.add(sql);
            return 0;
        }

        @Override
        public int execute(String sql, Object... params) {
            executedSql.add(sql);
            return 0;
        }

        @Override
        public int execute(String sql, List<Object> params) {
            executedSql.add(sql);
            return 0;
        }

        @Override
        public Array createArray(List<Object> list, String type) {
            return null;
        }

        private Map<Object, Map<String, Object>> table(String schema, String tableName) {
            return tables.computeIfAbsent(schema + "." + tableName, key -> new ConcurrentHashMap<>());
        }

        private static DBColumn column(String name, String type, boolean nullable, boolean pk, boolean sequence, Integer length) {
            DBColumn column = new DBColumn();
            column.setName(name);
            column.setType(type);
            column.setNullable(nullable);
            column.setPrimaryKey(pk);
            if (sequence) {
                column.setSequence();
            }
            if (length != null) {
                column.setLength(length);
            }
            return column;
        }
    }

    private static class StubMetaDataLoader implements IMetaDataLoader {
        private final DBInfo info;
        private final Map<String, Map<String, DBColumn>> columnMapByTable = new HashMap<>();
        private final Map<String, List<DBIndex>> indexListByTable = new HashMap<>();

        StubMetaDataLoader(DBInfo info) {
            this.info = info;
        }

        void putColumns(String schema, String table, Map<String, DBColumn> columns) {
            columnMapByTable.put(schema + "." + table, columns);
        }

        void putIndexes(String schema, String table, List<DBIndex> indexes) {
            indexListByTable.put(schema + "." + table, indexes);
        }

        @Override
        public DBInfo getDBInfo() {
            return info;
        }

        @Override
        public void resetInfo() {
        }

        @Override
        public List<DBIndex> getIndexList(String schema, String table) {
            return indexListByTable.getOrDefault(schema + "." + table, List.of());
        }

        @Override
        public Map<String, DBColumn> getColumnMap(String schema, String table) {
            return columnMapByTable.getOrDefault(schema + "." + table, new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        }
    }
}
