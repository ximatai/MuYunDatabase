package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;
import net.ximatai.muyun.database.core.builder.PredefinedColumn;
import net.ximatai.muyun.database.core.builder.PrimaryKeyConstraint;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.builder.UniqueConstraint;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.RuntimeColumnMapper;
import net.ximatai.muyun.database.core.orm.RuntimeTableGateway;
import net.ximatai.muyun.database.core.orm.SchemaManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("db")
@Testcontainers
public class MuYunDatabaseMySQLTest extends MuYunDatabaseUsageExamplesTestBase {

    @Container
//    private static final JdbcDatabaseContainer postgresContainer = new MySQLContainer("mysql:8.4.5")
    private static final JdbcDatabaseContainer container = new MySQLContainer()
            .withDatabaseName("testdb")
            .withUsername("root")
            .withPassword("testpass");

    @Override
    DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    Column getPrimaryKey() {
        return PredefinedColumn.Id.MYSQL.toColumn();
    }

    @Override
    JdbcDatabaseContainer getContainer() {
        return container;
    }

    @Override
    Class<?> getEntityClass() {
        return TestEntityForMysql.class;
    }

    @Test
    void testLikeIgnoreCaseAgainstCaseSensitiveCollation() {
        String tableName = "runtime_case_sensitive_record";
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name").setType(ColumnType.VARCHAR).setLength(64));
        new TableBuilder(db).build(table);
        db.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `v_name` "
                + "VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        db.execute("delete from " + tableName);

        RuntimeTableGateway gateway = new RuntimeTableGateway(
                db,
                db.getDefaultSchemaName(),
                tableName,
                RuntimeColumnMapper.of(Map.of("id", "id", "name", "v_name"))
        );
        gateway.insert(Map.of("name", "CaseSensitiveValue"));

        String lowercasePattern = "%casesensitivevalue%";
        assertEquals(0L, gateway.count(Criteria.of().like("name", lowercasePattern)));
        assertEquals(1L, gateway.count(Criteria.of().likeIgnoreCase("name", lowercasePattern)));
    }

    @Test
    void testCompositeConstraintsAreIdempotent() {
        TableWrapper parent = TableWrapper.withName("governed_mysql_parent")
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("record_id").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("latitude").setType(ColumnType.DOUBLE))
                .setPrimaryKey(PrimaryKeyConstraint.named("pk_governed_mysql_parent", "tenant_id", "record_id"))
                .addUniqueConstraint(UniqueConstraint.named("uk_governed_mysql_record", "record_id"));
        TableWrapper child = TableWrapper.withName("governed_mysql_child")
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("record_id").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("child_id").setType(ColumnType.VARCHAR).setLength(64))
                .setPrimaryKey(PrimaryKeyConstraint.named(
                        "pk_governed_mysql_child", "tenant_id", "record_id", "child_id"))
                .addForeignKey(ForeignKeyConstraint.named(
                        "fk_governed_mysql_parent", List.of("record_id"),
                        "governed_mysql_parent", List.of("record_id"), ForeignKeyAction.CASCADE));

        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(parent, MigrationOptions.execute());
        db.resetDBInfo();
        manager.ensureTable(child, MigrationOptions.execute());
        db.resetDBInfo();

        var parentPlan = manager.ensureTable(parent, MigrationOptions.dryRun());
        var childPlan = manager.ensureTable(child, MigrationOptions.dryRun());
        assertFalse(parentPlan.isChanged(), parentPlan.getStatements().toString());
        assertFalse(childPlan.isChanged(), childPlan.getStatements().toString());
    }
}
