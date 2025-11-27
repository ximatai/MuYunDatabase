package net.ximatai.muyun.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ximatai.muyun.database.core.annotation.Default;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBTable;
import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Slf4JSqlLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MuYunDatabaseBaseTest {

    private DataSource dataSource;

    Jdbi jdbi;
    JdbiMetaDataLoader loader;
    JdbiDatabaseOperations<String> db;

    abstract DatabaseType getDatabaseType();

    abstract Column getPrimaryKey();

    abstract JdbcDatabaseContainer getContainer();

    private boolean tableCreated = false;

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
        loader = new JdbiMetaDataLoader(jdbi);
        db = new JdbiDatabaseOperations<>(jdbi, loader, String.class, "id");
    }

    @BeforeEach
    void beforeEach() {
        if (!tableCreated) { // 测试之前要先创建表
            testTableBuilder();
        }
    }

    @Test
    void testGetDBInfo() {
        DBInfo info = loader.getDBInfo();

        assertEquals(getDatabaseType().name().toLowerCase(), info.getTypeName().toLowerCase());
        assertNotNull(info.getDefaultSchema());
    }

    @Test
    void testTableBuilder() {
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

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getDefaultSchema().getTable("basic");

        assertNotNull(table);

        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("b_flag"));
        assertTrue(table.contains("i_age"));

        assertTrue(table.getColumn("id").isPrimaryKey());

        tableCreated = true;
    }

    @Test
    void testTableBuilderChangeLength() {
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

        Map body = Map.of("v_name", "abcd_efgh",
                "i_age", 5,
                "b_flag", true,
                "n_price", 10.2,
                "d_date", "2024-01-01"
        );

        String id = db.insertItem("basic", body);

        TableWrapper basic2 = TableWrapper.withName("basic")
                .addColumn(Column.of("v_name").setLength(12).setIndexed().setComment("名称").setDefaultValue("test"));

        new TableBuilder(db).build(basic2);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getDefaultSchema().getTable("basic");
        DBColumn vName = table.getColumn("v_name");

        assertEquals(12, vName.getLength());
    }

    @Test
    void testTableBuilderWithoutDefaultSchema() {
        String schema = "just_a_test";
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

        tableCreated = true;
    }

    @Test
    void testSimpleInsert() {

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

    @Test
    void testUpsert() {

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

    @Test
    void testBatchInsert() {
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

    @Test
    void testUpdate() {
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

    @Test
    void testDelete() {
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

    @Test
    void testQuery() {
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

    @Test
    void testJdbiConnectionClosedWhenException() throws SQLException {
        AtomicReference<Connection> connection = new AtomicReference<>();
        assertThrowsExactly(SQLException.class, () -> {
            jdbi.withHandle(handle -> {
                connection.set(handle.getConnection());
                throw new SQLException("");
            });
        });

        assertTrue(connection.get().isClosed());
    }

    @Test
    void testJdbiConnectionClosedWhenException2() throws SQLException {
        AtomicReference<Connection> connection = new AtomicReference<>();
        assertThrowsExactly(SQLException.class, () -> {
            try (Handle open = jdbi.open();) {
                connection.set(open.getConnection());
                throw new SQLException("");
            }
        });

        assertTrue(connection.get().isClosed());
    }

    @Test
    void testJdbiConnectionClosedWhenException3() throws SQLException {
        AtomicReference<Connection> connection = new AtomicReference<>();
        assertThrowsExactly(SQLException.class, () -> {
            Handle open = jdbi.open();
            connection.set(open.getConnection());
            throw new SQLException("");
        });

        assertFalse(connection.get().isClosed());
    }

    @Test
    void testModifyColumnTypeToText() {
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

    @Test
    void testInherit() {
        String schema = "test";
        String baseTable = "test_inherit_base";
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

        TableWrapper child = TableWrapper.withName("test_inherit_child")
                .setSchema(schema)
                .addColumn(Column.of("v_name2")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"))
                .setInherit(basic);

        new TableBuilder(db).build(child);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getSchema(schema).getTable("test_inherit_child");

        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("v_name2"));
    }

    @Test
    void testInheritLater() {
        String schema = "test";
        TableWrapper basic = TableWrapper.withName("test_inherit_base")
                .setSchema(schema)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(basic);

        TableWrapper child = TableWrapper.withName("test_inherit_child")
                .setSchema(schema)
                .addColumn(Column.of("v_name2")
                        .setLength(20)
                        .setComment("名称")
                        .setDefaultValue("test"));

        new TableBuilder(db).build(child);

        child.setInherit(basic);

        new TableBuilder(db).build(child);

        DBInfo info = loader.getDBInfo();

        DBTable table = info.getSchema(schema).getTable("test_inherit_child");

        assertTrue(table.contains("id"));
        assertTrue(table.contains("v_name"));
        assertTrue(table.contains("v_name2"));
    }

    @Test
    void testTableBuilderWithEntity() {

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

    @Test
    void testTableBuilderWithEntityNoIdDefaultValue() {
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

    @Test
    void testIndexChange() {
        String schema = "test";
        String baseTable = "a_table";
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
