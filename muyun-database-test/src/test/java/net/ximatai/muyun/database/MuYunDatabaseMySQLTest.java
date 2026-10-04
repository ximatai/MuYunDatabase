package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.builder.PredefinedColumn;
import net.ximatai.muyun.database.core.builder.PrimaryKeyConstraint;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.builder.UniqueConstraint;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationChange;
import net.ximatai.muyun.database.core.orm.OrmException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void indexNamesAndColumnSelectorsShouldIgnoreCase() {
        String tableName = "mysql_index_identity";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", false).named("Lookup_Code"));
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table);
        table.getIndexes().clear();
        table.addIndex(new Index("CODE", false).named("LOOKUP_CODE"));
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRunStrict()).isChanged());

        TableWrapper drop = TableWrapper.withName(tableName).dropIndex(List.of("CODE")).dropIndexByName("LOOKUP_CODE");
        var preview = manager.ensureTable(drop, MigrationOptions.dryRun());
        assertEquals(1, preview.getChanges().size());
        assertThrows(OrmException.class, () -> manager.ensureTable(drop, MigrationOptions.strict()));
        assertEquals(preview.getStatements(), manager.ensureTable(drop, MigrationOptions.execute()).getStatements());
        assertEquals(0, db.getDBInfo().getDefaultSchema().getTable(tableName).getIndexList().size());
        assertFalse(manager.ensureTable(drop, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void declaredUniqueConstraintShouldRejectIndexDeletionBeforeAnyDdl() {
        String tableName = "mysql_unique_drop_conflict";
        String uniqueName = "guard_unique_code";
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addUniqueConstraint(UniqueConstraint.named(uniqueName, "code")));
        db.execute("insert into " + tableName + " values ('SAME', 'TENANT')");

        for (int scenario = 0; scenario < 3; scenario++) {
            TableWrapper target = TableWrapper.withName(tableName)
                    .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                    .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                    .addColumn(Column.of("extra").setType(ColumnType.VARCHAR).setLength(64))
                    .addUniqueConstraint(UniqueConstraint.named(uniqueName, scenario == 2 ? "tenant_id" : "code"));
            if (scenario == 0) target.dropIndexByName(uniqueName.toUpperCase(java.util.Locale.ROOT));
            else target.dropIndex(List.of("CODE"));

            for (MigrationOptions options : List.of(MigrationOptions.dryRun(), MigrationOptions.execute(),
                    MigrationOptions.strict(), MigrationOptions.dryRunStrict())) {
                OrmException error = assertThrows(OrmException.class, () -> manager.ensureTable(target, options));
                assertEquals(OrmException.Code.INVALID_MAPPING, error.getCode());
            }
        }
        var actual = db.getDBInfo().getDefaultSchema().getTable(tableName);
        assertFalse(actual.contains("extra"));
        assertEquals(List.of(uniqueName), actual.getUniqueConstraints().stream().map(constraint -> constraint.name()).toList());
        assertEquals(List.of("code"), actual.getIndexList().getFirst().getColumns());
        assertThrows(RuntimeException.class, () -> db.execute("insert into " + tableName + " values ('SAME', 'OTHER')"));
        assertEquals(1, db.query("select code from " + tableName, List.of()).size());
    }

    @Test
    void differentNameUniqueConstraintReplacementAndExplicitRemovalShouldWork() {
        String tableName = "mysql_unique_rename";
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addUniqueConstraint(UniqueConstraint.named("old_unique_code", "code")));
        db.execute("insert into " + tableName + " values ('SAME')");
        TableWrapper target = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addUniqueConstraint(UniqueConstraint.named("new_unique_code", "code"))
                .dropIndexByName("old_unique_code");

        var preview = manager.ensureTable(target, MigrationOptions.dryRun());
        assertEquals(List.of(MigrationChange.Type.ADD_UNIQUE_CONSTRAINT, MigrationChange.Type.DROP_INDEX),
                preview.getChanges().stream().map(MigrationChange::getType).toList());
        assertThrows(OrmException.class, () -> manager.ensureTable(target, MigrationOptions.strict()));
        assertEquals(preview.getStatements(), manager.ensureTable(target, MigrationOptions.execute()).getStatements());
        assertFalse(manager.ensureTable(target, MigrationOptions.dryRunStrict()).isChanged());
        assertEquals(List.of("new_unique_code"), db.getDBInfo().getDefaultSchema().getTable(tableName)
                .getUniqueConstraints().stream().map(constraint -> constraint.name()).toList());
        assertThrows(RuntimeException.class, () -> db.execute("insert into " + tableName + " values ('SAME')"));

        TableWrapper drop = TableWrapper.withName(tableName).dropIndexByName("new_unique_code");
        assertThrows(OrmException.class, () -> manager.ensureTable(drop, MigrationOptions.strict()));
        manager.ensureTable(drop, MigrationOptions.execute());
        assertEquals(0, db.getDBInfo().getDefaultSchema().getTable(tableName).getUniqueConstraints().size());
        db.execute("insert into " + tableName + " values ('SAME')");
        assertFalse(manager.ensureTable(drop, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void sameNamedIndexReplacementShouldPrecedeDroppingItsOldColumn() {
        String tableName = "mysql_index_replace_column";
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", true).named("Lookup_Code")), MigrationOptions.strict());
        TableWrapper target = TableWrapper.withName(tableName)
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .dropColumn("code").addIndex(new Index("TENANT_ID", false).named("lookup_code"));

        var preview = manager.ensureTable(target, MigrationOptions.dryRun());
        assertEquals(List.of(MigrationChange.Type.DROP_INDEX, MigrationChange.Type.CREATE_INDEX, MigrationChange.Type.DROP_COLUMN),
                preview.getChanges().stream().map(MigrationChange::getType).toList());
        assertThrows(OrmException.class, () -> manager.ensureTable(target, MigrationOptions.strict()));
        assertEquals(preview.getStatements(), manager.ensureTable(target, MigrationOptions.execute()).getStatements());
        assertFalse(manager.ensureTable(target, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void existingTableUniqueIndexShouldRequireValidation() {
        String tableName = "mysql_unique_index_validation";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64));
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table);
        db.execute("insert into " + tableName + " values ('SAME'), ('SAME')");
        table.addIndex(new Index("code", true).named("unique_code"));

        var preview = manager.ensureTable(table, MigrationOptions.dryRun());
        assertEquals(MigrationChange.Risk.DATA_VALIDATION_REQUIRED, preview.getChanges().getFirst().getRisk());
        assertThrows(OrmException.class, () -> manager.ensureTable(table, MigrationOptions.strict()));
        assertThrows(RuntimeException.class, () -> manager.ensureTable(table, MigrationOptions.execute()));
        assertEquals(0, db.getDBInfo().getDefaultSchema().getTable(tableName).getIndexList().size());
        assertEquals(2, db.query("select code from " + tableName, List.of()).size());
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
