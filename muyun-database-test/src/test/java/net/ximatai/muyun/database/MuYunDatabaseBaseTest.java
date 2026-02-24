package net.ximatai.muyun.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ximatai.muyun.database.core.annotation.Default;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.exception.MuYunDatabaseException;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBTable;
import net.ximatai.muyun.database.core.orm.*;
import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import net.ximatai.muyun.database.jdbi.JdbiRecommendedPlugins;
import net.ximatai.muyun.database.jdbi.JdbiTransactionRunner;
import org.jdbi.v3.json.Json;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.CaseStrategy;
import org.jdbi.v3.core.mapper.MapMappers;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.core.statement.Slf4JSqlLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MuYunDatabaseBaseTest {

    private DataSource dataSource;

    Jdbi jdbi;
    JdbiMetaDataLoader loader;
    JdbiDatabaseOperations<String> db;
    SimpleEntityManager orm;
    JdbiTransactionRunner<String> txRunner;

    abstract DatabaseType getDatabaseType();

    abstract Column getPrimaryKey();

    abstract JdbcDatabaseContainer getContainer();

    DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(getContainer().getJdbcUrl());
            config.setUsername(getContainer().getUsername());
            config.setPassword(getContainer().getPassword());
            config.setDriverClassName(getContainer().getDriverClassName());
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    @BeforeAll
    void setUp() {
        jdbi = Jdbi.create(getDataSource())
                .setSqlLogger(new Slf4JSqlLogger());
        JdbiRecommendedPlugins.installCommon(jdbi);
        if (getDatabaseType() == DatabaseType.POSTGRESQL) {
            JdbiRecommendedPlugins.installPostgres(jdbi);
        }
        jdbi.getConfig(MapMappers.class).setCaseChange(CaseStrategy.NOP);
        loader = new JdbiMetaDataLoader(jdbi);
        db = new JdbiDatabaseOperations<>(jdbi, loader, String.class, "id");
        orm = new DefaultSimpleEntityManager(db);
        txRunner = new JdbiTransactionRunner<>(jdbi, loader, String.class, "id");
    }

    @BeforeEach
    void beforeEach() {
        // 每次测试前都对齐 basic 表基线结构，避免测试间顺序耦合
        ensureBasicTable();
    }

    protected void testGetDBInfo() {
        DBInfo info = loader.getDBInfo();

        assertEquals(getDatabaseType().name().toLowerCase(), info.getTypeName().toLowerCase());
        assertNotNull(info.getDefaultSchema());
    }

    protected void ensureBasicTable() {
        TableWrapper basic = TableWrapper.withName("basic")
                .setPrimaryKey(getPrimaryKey())
                .setComment("测试表")
                .addColumn(Column.of("v_name").setLength(20).setIndexed().setComment("名称").setDefaultValue("test"))
                .addColumn(Column.of("i_age").setComment("年龄"))
                .addColumn(Column.of("n_price").setPrecision(10).setScale(2))
                .addColumn("b_flag")
                .addColumn("d_date")
                .addColumn(Column.of("t_create").setDefaultValueAny("CURRENT_TIMESTAMP"));

        new TableBuilder(db).build(basic);

    }

    protected void testTableBuilder() {
        ensureBasicTable();

        DBInfo info = loader.getDBInfo();
        DBTable table = info.getDefaultSchema().getTable("basic");
        assertNotNull(table);
        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("b_flag"));
        assertTrue(table.contains("i_age"));
        assertTrue(table.getColumn("id").isPrimaryKey());
    }

    protected void testTableBuilderChangeLength() {
        String tableName = "test_table_builder_change_length";
        TableWrapper basic = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .setComment("测试表")
                .addColumn(Column.of("v_name").setLength(20).setIndexed().setComment("名称").setDefaultValue("test"))
                .addColumn(Column.of("i_age").setComment("年龄"))
                .addColumn(Column.of("n_price").setPrecision(10).setScale(2))
                .addColumn("b_flag")
                .addColumn("d_date")
                .addColumn(Column.of("t_create").setDefaultValueAny("CURRENT_TIMESTAMP"));

        new TableBuilder(db).build(basic);

        Map body = Map.of("v_name", "abcd_efgh",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem(tableName, body);

        TableWrapper basic2 = TableWrapper.withName(tableName)
                .addColumn(Column.of("v_name").setLength(12).setIndexed().setComment("名称").setDefaultValue("test"));

        new TableBuilder(db).build(basic2);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getDefaultSchema().getTable(tableName);
        DBColumn vName = table.getColumn("v_name");

        assertEquals(12, vName.getLength());
    }

    protected void testTableBuilderWithoutDefaultSchema() {
        String schema = "test_schema_without_default";
        TableWrapper basic = TableWrapper.withName("basic")
                .setSchema(schema)
                .setPrimaryKey(getPrimaryKey())
                .setComment("测试表")
                .addColumn(Column.of("v_name").setLength(20).setIndexed().setComment("名称").setDefaultValue("test"))
                .addColumn(Column.of("i_age").setComment("年龄"))
                .addColumn(Column.of("n_price").setPrecision(10).setScale(2))
                .addColumn("b_flag")
                .addColumn("d_date")
                .addColumn(Column.of("t_create").setDefaultValueAny("CURRENT_TIMESTAMP"));

        new TableBuilder(db).build(basic);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getSchema(schema).getTable("basic");

        assertNotNull(table);

        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("b_flag"));
        assertTrue(table.contains("i_age"));

        assertTrue(table.getColumn("id").isPrimaryKey());
    }

    protected void testSimpleInsert() {

        Map body = Map.of("v_name", "test_name",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);
        assertNotNull(id);

        Map<String, Object> item = db.getItem("basic", id);

        assertNotNull(item);
        assertEquals("test_name", item.get("v_name"));
        assertEquals(5, item.get("i_age"));
        assertEquals(true, item.get("b_flag"));
        assertEquals(0, BigDecimal.valueOf(10.2).compareTo((BigDecimal) item.get("n_price")));
        assertEquals(LocalDate.of(2024, 1, 1), ((Date) item.get("d_date")).toLocalDate());
    }

    protected void testUpsert() {

        Map body = Map.of(
                "v_name", "test_name",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);
        assertNotNull(id);

        Map<String, Object> item = db.getItem("basic", id);

        assertNotNull(item);
        assertEquals("test_name", item.get("v_name"));
        assertEquals(5, item.get("i_age"));
        assertEquals(true, item.get("b_flag"));
        assertEquals(0, BigDecimal.valueOf(10.2).compareTo((BigDecimal) item.get("n_price")));
        assertEquals(LocalDate.of(2024, 1, 1), ((Date) item.get("d_date")).toLocalDate());

        Map body2 = Map.of(
                "id", id,
                "v_name", "test_name2",
                "i_age", 6,
                "b_flag", true
        );

        int upserted2 = db.upsertItem("basic", body2);
        assertEquals(1, upserted2);

        item = db.getItem("basic", id);

        assertNotNull(item);
        assertEquals("test_name2", item.get("v_name"));
        assertEquals(6, item.get("i_age"));
        assertEquals(true, item.get("b_flag"));
        assertEquals(0, BigDecimal.valueOf(10.2).compareTo((BigDecimal) item.get("n_price")));
        assertEquals(LocalDate.of(2024, 1, 1), ((Date) item.get("d_date")).toLocalDate());

    }

    protected void testAtomicUpsertConcurrent() throws InterruptedException {
        assertTrue(db.supportsAtomicUpsert(), "Current database should support atomic upsert");

        String tableName = "atomic_upsert_concurrent";
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name").setLength(64))
                .addColumn(Column.of("i_age"));
        new TableBuilder(db).build(table);

        String id = getDatabaseType() == DatabaseType.POSTGRESQL ? UUID.randomUUID().toString() : "1";
        int threads = 12;
        int iterations = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<String> firstError = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final int worker = i;
            pool.submit(() -> {
                try {
                    startGate.await(5, TimeUnit.SECONDS);
                    for (int j = 0; j < iterations; j++) {
                        db.atomicUpsertItem(tableName, Map.of(
                                "id", id,
                                "v_name", "worker_" + worker,
                                "i_age", worker
                        ));
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    firstError.compareAndSet(null, e.getClass().getName() + ": " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(60, TimeUnit.SECONDS), "Concurrent upsert tasks timed out");
        pool.shutdownNow();

        assertEquals(0, failures.get(),
                "Atomic upsert should not fail under concurrent writes, first error: " + firstError.get());

        Map<String, Object> row = db.row("select count(*) as c from " + tableName + " where id = :id", Map.of("id", id));
        assertNotNull(row);
        Number count = (Number) row.get("c");
        assertEquals(1, count.intValue(), "Only one row should exist for the same primary key");
    }

    protected void testAtomicUpsertConcurrentHighContention() throws InterruptedException {
        assertTrue(db.supportsAtomicUpsert(), "Current database should support atomic upsert");

        String tableName = "atomic_upsert_high_contention";
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name").setLength(64))
                .addColumn(Column.of("i_age"));
        new TableBuilder(db).build(table);

        String id = getDatabaseType() == DatabaseType.POSTGRESQL ? UUID.randomUUID().toString() : "2";
        int threads = 24;
        int iterations = 40;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<String> firstError = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final int worker = i;
            pool.submit(() -> {
                try {
                    startGate.await(5, TimeUnit.SECONDS);
                    for (int j = 0; j < iterations; j++) {
                        db.atomicUpsertItem(tableName, Map.of(
                                "id", id,
                                "v_name", "hc_" + worker,
                                "i_age", worker
                        ));
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    firstError.compareAndSet(null, e.getClass().getName() + ": " + e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(90, TimeUnit.SECONDS), "High-contention upsert tasks timed out");
        pool.shutdownNow();

        assertEquals(0, failures.get(),
                "Atomic upsert should remain stable under high contention, first error: " + firstError.get());

        Map<String, Object> row = db.row("select count(*) as c from " + tableName + " where id = :id", Map.of("id", id));
        assertNotNull(row);
        Number count = (Number) row.get("c");
        assertEquals(1, count.intValue(), "Only one row should exist under high contention");
    }

    protected void testBatchInsert() {
        String schema = loader.getDBInfo().getDefaultSchemaName();
        Map<String, Object> body = Map.of("v_name", "test_name1",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );
        Map<String, Object> body2 = Map.of("v_name", "test_name2",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        List<String> ids = db.insertList("basic", List.of(body, body2));

        assertNotNull(ids);
        assertEquals(2, ids.size());
    }

    protected void testUpdate() {
        Map body = Map.of("v_name", "test_name",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);
        assertNotNull(id);

        Map<String, Object> item = db.getItem("basic", id);

        assertEquals("test_name", item.get("v_name"));

        db.updateItem("basic", Map.of(
                "id", id,
                "v_name", "test_name2"));

        item = db.getItem("basic", id);

        assertEquals("test_name2", item.get("v_name"));
    }

    protected void testPatchUpdate() {
        Map<String, Object> body = Map.of(
                "v_name", "patch_source",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);
        assertNotNull(id);

        int updated = db.patchUpdateItem("basic", id, Map.of("v_name", "patch_target"));
        assertEquals(1, updated);

        Map<String, Object> item = db.getItem("basic", id);
        assertEquals("patch_target", item.get("v_name"));
        assertEquals(5, ((Number) item.get("i_age")).intValue());

        MuYunDatabaseException ex = assertThrows(
                MuYunDatabaseException.class,
                () -> db.patchUpdateItem("basic", id, Map.of("id", id))
        );
        assertEquals("No updatable fields were provided for patch update", ex.getMessage());
    }

    protected void testDelete() {
        Map body = Map.of("v_name", "test_name",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);
        assertNotNull(id);

        Integer deleteSize = db.deleteItem("basic", id);
        assertEquals(1, deleteSize);

        Map<String, Object> item = db.getItem("basic", id);

        assertNull(item);
    }

    protected void testQuery() {
        Map body = Map.of("v_name", "test_name_x",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);

        List<Map<String, Object>> queried = db.query("select * from basic where id = ?", List.of(id));

        assertEquals(1, queried.size());

        List<Map<String, Object>> queried2 = db.query("select * from basic where id = ?", id);

        assertEquals(1, queried2.size());

        List<Map<String, Object>> queried3 = db.query("select * from basic where id = :id", Map.of("id", id));

        assertEquals(1, queried3.size());

        Map<String, Object> row = db.row("select * from basic where id = :id", Map.of("id", id));

        assertEquals("test_name_x", queried.getFirst().get("v_name"));
        assertEquals("test_name_x", queried2.getFirst().get("v_name"));
        assertEquals("test_name_x", queried3.getFirst().get("v_name"));
        assertEquals("test_name_x", row.get("v_name"));

    }

    protected void testJdbiConnectionClosedWhenException() throws SQLException {
        AtomicReference<Connection> connection = new AtomicReference<>();
        assertThrowsExactly(SQLException.class, () -> {
            jdbi.withHandle(handle -> {
                connection.set(handle.getConnection());
                throw new SQLException("");
            });
        });

        assertTrue(connection.get().isClosed());
    }

    protected void testJdbiConnectionClosedWhenException2() throws SQLException {
        AtomicReference<Connection> connection = new AtomicReference<>();
        assertThrowsExactly(SQLException.class, () -> {
            try (Handle open = jdbi.open();) {
                connection.set(open.getConnection());
                throw new SQLException("");
            }
        });

        assertTrue(connection.get().isClosed());
    }

    protected void testJdbiConnectionClosedWhenException3() throws SQLException {
        AtomicReference<Connection> connection = new AtomicReference<>();
        assertThrowsExactly(SQLException.class, () -> {
            Handle open = jdbi.open();
            connection.set(open.getConnection());
            throw new SQLException("");
        });

        assertFalse(connection.get().isClosed());
    }

    protected void testModifyColumnTypeToText() {
        TableWrapper basic = TableWrapper.withName("test_modify_column_type_to_text")
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(basic);

        Map body = Map.of("v_name", "abcd_efgh");

        String id = db.insertItem("test_modify_column_type_to_text", body);

        Map<String, Object> row = db.row("select * from test_modify_column_type_to_text where id = ?", id);

        assertEquals("abcd_efgh", row.get("v_name"));

        TableWrapper basicModify = TableWrapper.withName("test_modify_column_type_to_text")
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name")
                        .setType(ColumnType.TEXT)
                        .setComment("名称"));

        new TableBuilder(db).build(basicModify);

        Map<String, Object> row2 = db.row("select * from test_modify_column_type_to_text where id = ?", id);

        assertEquals("abcd_efgh", row2.get("v_name"));
    }

    protected void testInherit() {
        String schema = "test_inherit_direct_schema";
        String baseTable = "test_inherit_base_direct";
        String childTable = "test_inherit_child_direct";
        TableWrapper basic = TableWrapper.withName(baseTable)
                .setSchema(schema)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(basic);

        DBTable base = db.getDBInfo().getSchema(schema).getTable(baseTable);

        assertTrue(base.getColumn("id").isPrimaryKey());
        assertNotNull(base.getColumn("id").getDefaultValueWithString());

        TableWrapper child = TableWrapper.withName(childTable)
                .setSchema(schema)
                .addColumn(Column.of("v_name2")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"))
                .setInherit(basic);

        new TableBuilder(db).build(child);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getSchema(schema).getTable(childTable);

        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("v_name2"));
    }

    protected void testInheritLater() {
        String schema = "test_inherit_later_schema";
        String baseTable = "test_inherit_base_later";
        String childTable = "test_inherit_child_later";
        TableWrapper basic = TableWrapper.withName(baseTable)
                .setSchema(schema)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(basic);

        TableWrapper child = TableWrapper.withName(childTable)
                .setSchema(schema)
                .addColumn(Column.of("v_name2")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(child);

        child.setInherit(basic);

        new TableBuilder(db).build(child);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getSchema(schema).getTable(childTable);

        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("v_name2"));
    }

    protected void testInheritMultiParentsForPostgres() {
        if (getDatabaseType() != DatabaseType.POSTGRESQL) {
            return;
        }

        String schema = "test_inherit_multi_schema";
        TableWrapper parent1 = TableWrapper.withName("test_inherit_parent1")
                .setSchema(schema)
                .addColumn(Column.of("v_name1").setLength(20).setDefaultValue("p1"));
        TableWrapper parent2 = TableWrapper.withName("test_inherit_parent2")
                .setSchema(schema)
                .addColumn(Column.of("v_name2").setLength(20).setDefaultValue("p2"));

        new TableBuilder(db).build(parent1);
        new TableBuilder(db).build(parent2);

        TableWrapper child = TableWrapper.withName("test_inherit_child_multi")
                .setSchema(schema)
                .addColumn(Column.of("v_child").setLength(20).setDefaultValue("c"))
                .setInherits(List.of(parent1, parent2));

        new TableBuilder(db).build(child);

        DBTable table = loader.getDBInfo().getSchema(schema).getTable("test_inherit_child_multi");
        assertTrue(table.contains("v_name1"));
        assertTrue(table.contains("v_name2"));
        assertTrue(table.contains("v_child"));

        List<Map<String, Object>> rows = db.query(
                "SELECT inhparent::regclass::text AS parent FROM pg_inherits WHERE inhrelid = ?::regclass",
                schema + ".test_inherit_child_multi"
        );
        assertEquals(2, rows.size());
    }

    protected void testTableBuilderWithEntity() {

        new TableBuilder(db).build(getEntityClass());

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getDefaultSchema().getTable("test_entity");

        assertNotNull(table);

        assertTrue(table.contains("id"));
        assertTrue(table.contains("ID"));
        assertTrue(table.contains("Id"));
        assertTrue(table.contains("name"));
        assertTrue(table.contains("age"));
        assertTrue(table.contains("price"));

        assertTrue(table.getColumn("id").isPrimaryKey());

        assertEquals(12, table.getColumn("age").getDefaultValue());
        assertEquals(true, table.getColumn("flag").getDefaultValue());
        assertEquals(new BigDecimal("1.23"), table.getColumn("price").getDefaultValue());
        assertEquals("CURRENT_TIMESTAMP", table.getColumn("create_time").getDefaultValue());

        System.out.println(table.getColumn("id").getDefaultValueWithString());

        List<DBIndex> indexList = table.getIndexList();
        assertTrue(indexList.stream()
                .anyMatch(i -> i.getColumns().containsAll(Arrays.asList("name", "age")) && i.isUnique())
        );

        assertTrue(indexList.stream()
                .anyMatch(i -> i.getColumns().containsAll(Arrays.asList("name", "flag")) && !i.isUnique())
        );

        assertTrue(indexList.stream()
                .anyMatch(i -> i.getColumns().size() == 1 && i.getColumns().contains("flag") && !i.isUnique())
        );

        assertTrue(indexList.stream()
                .anyMatch(i -> i.getColumns().size() == 1 && i.getColumns().contains("code") && i.isUnique())
        );

        String id = db.insertItem("test_entity", Map.of("code", 10));

        assertNotNull(id);

        Map<String, Object> row = db.row("select * from test_entity where id = ?", id);

        assertNotNull(row);

        assertEquals(10, row.get("code"));
        assertEquals(12, row.get("age"));
        assertEquals(new BigDecimal("1.23"), row.get("price"));
        assertEquals(true, row.get("flag"));
        assertNotNull(row.get("create_time"));

    }

    protected void testTableBuilderWithEntityNoIdDefaultValue() {
        new TableBuilder(db).build(TestEntityBaseNoIdDefaultValue.class);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getDefaultSchema().getTable("test_entity2");

        assertNotNull(table);

        assertTrue(table.contains("id"));
        assertTrue(table.getColumn("id").isPrimaryKey());
        assertFalse(table.getColumn("name2").isNullable());

        String string = db.insertItem("test_entity2",
                Map.of("id", UUID.randomUUID().toString(),
                        "name2", "test"));

        assertNotNull(string);
    }

    protected void testIndexChange() {
        String schema = "test_index_change_schema";
        String baseTable = "test_index_change_table";
        TableWrapper basic = TableWrapper.withName(baseTable)
                .setSchema(schema)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(basic);

        DBTable table = db.getDBInfo().getSchema(schema).getTable(baseTable);

        assertTrue(table.getColumn("id").isPrimaryKey());
        assertEquals(0, table.getIndexList().size());

        basic.addIndex("v_name");

        new TableBuilder(db).build(basic);

        table = db.getDBInfo().getSchema(schema).getTable(baseTable);

        assertEquals(1, table.getIndexList().size());
        assertFalse(table.getIndexList().getFirst().isUnique());

        basic.getIndexes().clear();

        basic.addIndex("v_name", true);

        new TableBuilder(db).build(basic);

        table = db.getDBInfo().getSchema(schema).getTable(baseTable);

        assertEquals(1, table.getIndexList().size());
        assertTrue(table.getIndexList().getFirst().isUnique());

    }

    protected void testSpecialTableNameSupport() {
        String tableName = "测试-Order_表-01";
        String nameColumn = "姓名-Name";
        String ageColumn = "年龄_Age";
        TableWrapper specialTable = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of(nameColumn).setType(ColumnType.VARCHAR).setLength(20))
                .addColumn(Column.of(ageColumn).setType(ColumnType.INT));

        new TableBuilder(db).build(specialTable);

        String id = db.insertItem(tableName, Map.of(nameColumn, "special_name", ageColumn, 7));
        assertNotNull(id);

        Map<String, Object> row = db.getItem(tableName, id);
        assertNotNull(row);
        assertEquals("special_name", row.get(nameColumn));
        assertEquals(7, row.get(ageColumn));

        int updated = db.patchUpdateItem(tableName, id, Map.of(nameColumn, "special_name_v2"));
        assertEquals(1, updated);
        Map<String, Object> patched = db.getItem(tableName, id);
        assertNotNull(patched);
        assertEquals("special_name_v2", patched.get(nameColumn));

        orm.ensureTable(OrmSpecialTableEntity.class);
        OrmSpecialTableEntity entity = new OrmSpecialTableEntity();
        entity.id = UUID.randomUUID().toString();
        entity.name = "orm_special";
        entity.age = 9;

        Object ormId = orm.insert(entity);
        assertNotNull(ormId);

        OrmSpecialTableEntity loaded = orm.findById(OrmSpecialTableEntity.class, ormId);
        assertNotNull(loaded);
        assertEquals("orm_special", loaded.name);
        assertEquals(9, loaded.age);

        List<OrmSpecialTableEntity> rows = orm.query(
                OrmSpecialTableEntity.class,
                Criteria.of().eq("name", "orm_special"),
                PageRequest.of(1, 10),
                Sort.desc("age")
        );
        assertFalse(rows.isEmpty());
        assertEquals("orm_special", rows.getFirst().name);
    }

    abstract Class<?> getEntityClass();
}

@Table(name = "test_entity2")
class TestEntityBaseNoIdDefaultValue {

    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 36)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(length = 20, comment = "名称", defaultVal = @Default(varchar = "test_name"), nullable = false)
    public String name;

    @net.ximatai.muyun.database.core.annotation.Column(length = 20, comment = "名称2", nullable = false)
    public String name2;

}

@Table(name = "orm_patch_entity")
class OrmPatchEntity {
    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "v_name", length = 32)
    public String name;

    @net.ximatai.muyun.database.core.annotation.Column(name = "i_age")
    public Integer age;
}

@Table(name = "orm_dryrun_entity")
class OrmDryRunEntity {
    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "v_name", length = 20)
    public String name;
}

@Table(name = "orm_strict_entity")
class OrmStrictEntityV1 {
    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "v_name", length = 20)
    public String name;
}

@Table(name = "orm_strict_entity")
class OrmStrictEntityV2 {
    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "v_name", length = 128)
    public String name;
}

@Table(name = "orm_json_entity")
class OrmJsonEntity {
    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "j_payload", type = net.ximatai.muyun.database.core.builder.ColumnType.JSON)
    public String payload;
}

@Table(name = "orm-特殊表")
class OrmSpecialTableEntity {
    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "姓名-Name", length = 20)
    public String name;

    @net.ximatai.muyun.database.core.annotation.Column(name = "年龄_Age")
    public Integer age;
}

interface SqlObjectBasicDao {

    @SqlUpdate("insert into <table>(v_name, i_age) values(:name, :age)")
    @GetGeneratedKeys("id")
    String insert(
            @Define("table") String table,
            @Bind("name") String name,
            @Bind("age") int age
    );

    @SqlQuery("select v_name from <table> where id = :id")
    String findNameById(@Define("table") String table, @Bind("id") String id);

    @SqlQuery("select count(*) from <table> where v_name = :name")
    int countByName(@Define("table") String table, @Bind("name") String name);
}

interface SqlObjectJsonDao {
    @SqlUpdate("insert into orm_json_entity(id, j_payload) values(:id, :payload)")
    void insert(@Bind("id") String id, @Json @Bind("payload") Map<String, Object> payload);

    @SqlQuery("select j_payload from orm_json_entity where id = :id")
    String findPayloadText(@Bind("id") String id);
}
