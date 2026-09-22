package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.builder.TableBase;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.builder.IndexColumn;
import net.ximatai.muyun.database.core.builder.IndexSortDirection;
import net.ximatai.muyun.database.core.builder.PrimaryKeyConstraint;
import net.ximatai.muyun.database.core.builder.UniqueConstraint;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBForeignKey;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBSchema;
import net.ximatai.muyun.database.core.metadata.DBTable;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaManagerTest {

    @Test
    void shouldPlanPostgresNativeTypesAndRichConstraints() {
        FakeOperations operations = new FakeOperations(new DBInfo("POSTGRESQL"));
        Index active = Index.of(List.of(IndexColumn.asc("tenant_id"), IndexColumn.desc("created_at")), true)
                .named("ux_contract_active")
                .predicate("status in ('ACTIVE')");
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("public")
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64).setNullable(false))
                .addColumn(Column.of("id").setType(ColumnType.UUID).setNullable(false))
                .addColumn(Column.of("created_at").setType(ColumnType.TIMESTAMP_WITH_TIME_ZONE).setNullable(false))
                .addColumn(Column.of("latitude").setType(ColumnType.DOUBLE).setNullable(false))
                .addColumn(Column.of("status").setType(ColumnType.VARCHAR).setLength(32).setNullable(false))
                .setPrimaryKey(PrimaryKeyConstraint.named("pk_contract", "tenant_id", "id"))
                .addUniqueConstraint(UniqueConstraint.named("uk_contract_id", "id"))
                .addForeignKey(ForeignKeyConstraint.named(
                        "fk_contract_parent", List.of("tenant_id"), "tenant", List.of("id"), ForeignKeyAction.CASCADE))
                .addIndex(active);

        MigrationResult result = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("\"id\" uuid")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("timestamp with time zone")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("double precision")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("primary key (\"tenant_id\", \"id\")")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("constraint \"uk_contract_id\" unique")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("on delete CASCADE")));
        assertTrue(result.getStatements().stream().anyMatch(sql ->
                sql.contains("\"tenant_id\" ASC,\"created_at\" DESC") && sql.contains(" where status in ('ACTIVE')")));
    }

    @Test
    void shouldExecuteExactlyThePlannedStatements() {
        FakeOperations operations = new FakeOperations(new DBInfo("POSTGRESQL"));
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.UUID).setPrimaryKey());

        MigrationResult result = new SchemaManager(operations).ensureTable(table, MigrationOptions.execute());

        assertEquals(result.getStatements(), operations.executedSql);
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.ADD_PRIMARY_KEY));
    }

    @Test
    void shouldResetMetadataWhenDdlExecutionFails() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        FakeOperations operations = new FakeOperations(loader);
        operations.failOnExecute = true;
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.UUID).setPrimaryKey());

        assertThrows(RuntimeException.class,
                () -> new SchemaManager(operations).ensureTable(table, MigrationOptions.execute()));

        assertEquals(1, loader.resetCount);
    }

    @Test
    void shouldRebuildIndexWhenColumnDirectionDiffers() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("created_at", aliasedColumn("created_at", "timestamp", null));
        DBIndex actual = new DBIndex().setName("ix_contract_created");
        actual.addColumn("created_at", IndexSortDirection.ASC, 1);
        loader.indexes.put("public.contract", List.of(actual));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("created_at").setType(ColumnType.TIMESTAMP))
                .addIndex(Index.of(List.of(IndexColumn.desc("created_at")), false).named("ix_contract_created"));

        MigrationResult result = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.DROP_INDEX));
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_INDEX));
    }

    @Test
    void shouldRejectPartialIndexOnMysqlInsteadOfDowngradingIt() {
        FakeOperations operations = new FakeOperations(new DBInfo("MYSQL"));
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("app")
                .addColumn(Column.of("id").setType(ColumnType.VARCHAR))
                .addIndex(new Index("id", true).named("ux_contract_active").predicate("id is not null"));

        assertThrows(OrmException.class,
                () -> new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun()));
    }

    @Test
    void shouldPlanTableWrapperMigrationWithoutExecutingDryRun() {
        FakeOperations operations = new FakeOperations(new DBInfo("POSTGRESQL"));
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64).setUnique());

        MigrationResult result = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(result.isChanged());
        assertTrue(result.isDryRun());
        assertFalse(result.hasNonAdditiveChanges());
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("create table")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("create unique index")));
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_TABLE));
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.ADD_COLUMN));
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_INDEX));
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.DROP_TEMP_COLUMN));
        assertTrue(result.getChanges().stream().noneMatch(MigrationChange::isNonAdditive));
        assertEquals(List.of(), operations.executedSql);
    }

    @Test
    void shouldRejectNonAdditiveTableWrapperMigrationInStrictMode() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(existingInfo());
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey());

        OrmException exception = assertThrows(
                OrmException.class,
                () -> new SchemaManager(operations).ensureTable(table, MigrationOptions.strict())
        );

        assertEquals(OrmException.Code.STRICT_MIGRATION_REJECTED, exception.getCode());
        assertEquals(List.of(), operations.executedSql);
    }

    @Test
    void shouldNotImplicitlyDropAUniqueIndexWhenAddingAWiderOne() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("tenant_id", varcharColumn("tenant_id", 64));
        loader.columns.get("public.contract").put("code", varcharColumn("code", 64));
        loader.indexes.put("public.contract", List.of(index("contract_code_uindex", true, "code")));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(List.of("tenant_id", "code"), true);

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertFalse(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("drop index")));
        assertTrue(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("create unique index")));

        new SchemaManager(operations).ensureTable(table, MigrationOptions.execute());

        assertFalse(operations.executedSql.stream().anyMatch(sql -> sql.contains("drop index")));
        assertTrue(operations.executedSql.stream().anyMatch(sql -> sql.contains("create unique index")));
    }

    @Test
    void shouldPreservePredicateLiteralCaseWhenComparingPartialIndexes() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("status", varcharColumn("status", 32));
        DBIndex actual = index("ux_contract_status", true, "status")
                .setPredicate("status = 'active'");
        loader.indexes.put("public.contract", List.of(actual));
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("status").setType(ColumnType.VARCHAR).setLength(32))
                .addIndex(new Index("status", true).named("ux_contract_status").predicate("status = 'ACTIVE'"));

        MigrationResult result = new SchemaManager(new FakeOperations(loader))
                .ensureTable(table, MigrationOptions.dryRun());

        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.DROP_INDEX));
        assertTrue(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_INDEX));
    }

    @Test
    void shouldIgnorePostgresTypeCastsWithoutChangingLiteralCase() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("status", varcharColumn("status", 32));
        DBIndex actual = index("ux_contract_status", true, "status")
                .setPredicate("(status = 'ACTIVE'::character varying)");
        loader.indexes.put("public.contract", List.of(actual));
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("status").setType(ColumnType.VARCHAR).setLength(32))
                .addIndex(new Index("status", true).named("ux_contract_status").predicate("status = 'ACTIVE'"));

        MigrationResult result = new SchemaManager(new FakeOperations(loader))
                .ensureTable(table, MigrationOptions.dryRun());

        assertFalse(result.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.DROP_INDEX
                        || change.getType() == MigrationChange.Type.CREATE_INDEX));
    }

    @Test
    void shouldMatchOnlyTheDeterministicIndexNameWhenColumnsOverlap() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("code", varcharColumn("code", 64));
        loader.indexes.put("public.contract", List.of(
                index("another_code_index", true, "code"),
                index("contract_code_index", false, "code")
        ));
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", false));

        MigrationResult result = new SchemaManager(new FakeOperations(loader))
                .ensureTable(table, MigrationOptions.dryRun());

        assertFalse(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.DROP_INDEX));
        assertFalse(result.getChanges().stream().anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_INDEX));
    }

    @Test
    void shouldEscapeCommentsAsSqlLiterals() {
        FakeOperations operations = new FakeOperations(new DBInfo("POSTGRESQL"));
        TableWrapper table = TableWrapper.withName("commented")
                .setComment("owner's records")
                .addColumn(Column.of("owner_id").setType(ColumnType.VARCHAR).setComment("owner's id"));

        MigrationResult result = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("owner''s records")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("owner''s id")));
    }

    @Test
    void shouldUseInheritedColumnsWithoutAddingThemAgain() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        DBInfo info = loader.getDBInfo();
        DBSchema schema = new DBSchema("public");
        schema.addTable(new DBTable(loader).setSchema("public").setName("parent_record"));
        info.addSchema(schema);
        loader.columns.put("public.parent_record", Map.of("tenant_id", varcharColumn("tenant_id", 64)));
        TableWrapper child = TableWrapper.withName("child_record")
                .setInherit(new TableBase("public", "parent_record"))
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("tenant_id", false));

        MigrationResult result = new SchemaManager(new FakeOperations(loader))
                .ensureTable(child, MigrationOptions.dryRun());

        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("inherits")));
        assertFalse(result.getStatements().stream().anyMatch(sql -> sql.contains("add \"tenant_id\"")));
        assertTrue(result.getStatements().stream().anyMatch(sql -> sql.contains("on \"public\".\"child_record\"(\"tenant_id\" ASC)")));
    }

    @Test
    void shouldTreatMysqlNoActionAndRestrictAsEquivalent() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("MYSQL"));
        DBSchema schema = new DBSchema("app");
        schema.addTable(new DBTable(loader).setSchema("app").setName("contract"));
        loader.getDBInfo().addSchema(schema);
        loader.columns.put("app.contract", new HashMap<>(Map.of(
                "id", varcharColumn("id", 32),
                "parent_id", varcharColumn("parent_id", 32)
        )));
        loader.foreignKeys.put("app.contract", List.of(new DBForeignKey(
                "fk_contract_parent", List.of("parent_id"), "app", "parent_record", List.of("id"),
                ForeignKeyAction.RESTRICT)));
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("app")
                .addColumn(Column.of("id").setType(ColumnType.VARCHAR).setLength(32))
                .addColumn(Column.of("parent_id").setType(ColumnType.VARCHAR).setLength(32))
                .addForeignKey(ForeignKeyConstraint.named(
                        "fk_contract_parent", List.of("parent_id"), "parent_record", List.of("id"),
                        ForeignKeyAction.NO_ACTION));

        MigrationResult result = new SchemaManager(new FakeOperations(loader))
                .ensureTable(table, MigrationOptions.dryRun());

        assertFalse(result.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.DROP_FOREIGN_KEY
                        || change.getType() == MigrationChange.Type.ADD_FOREIGN_KEY));
    }

    @Test
    void shouldPlanAndExecuteExplicitColumnDropAsNonAdditiveMigration() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("code", varcharColumn("code", 64));
        loader.columns.get("public.contract").put("removed_name", varcharColumn("removed_name", 128));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .dropColumn("removed_name");

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(dryRun.isChanged());
        assertTrue(dryRun.hasNonAdditiveChanges());
        assertTrue(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("drop column \"removed_name\"")));
        assertTrue(dryRun.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.DROP_COLUMN
                        && change.isNonAdditive()
                        && "removed_name".equals(change.getTarget())));

        MigrationResult executed = new SchemaManager(operations).ensureTable(table, MigrationOptions.execute());

        assertTrue(executed.isChanged());
        assertTrue(executed.hasNonAdditiveChanges());
        assertTrue(operations.executedSql.stream().anyMatch(sql -> sql.contains("drop column \"removed_name\"")));
    }

    @Test
    void shouldTreatRequiredColumnWithoutDefaultOnExistingTableAsNonAdditive() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("required_code").setType(ColumnType.VARCHAR).setLength(64).setNullable(false));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(dryRun.hasNonAdditiveChanges());
        assertTrue(dryRun.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.ADD_COLUMN
                        && change.isNonAdditive()
                        && "required_code".equals(change.getTarget())));

        OrmException exception = assertThrows(
                OrmException.class,
                () -> new SchemaManager(operations).ensureTable(table, MigrationOptions.strict())
        );
        assertEquals(OrmException.Code.STRICT_MIGRATION_REJECTED, exception.getCode());
    }

    @Test
    void migrationResultShouldRejectMismatchedStatementsAndChanges() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationResult(
                        true,
                        true,
                        false,
                        List.of("select 1"),
                        List.of(MigrationChange.additive(MigrationChange.Type.RAW_SQL, null, "select 2"))
                )
        );

        assertEquals("migration statements must match change SQL order", exception.getMessage());
    }

    @Test
    void migrationResultShouldRejectMismatchedNonAdditiveFlagAndChanges() {
        IllegalArgumentException missingFlag = assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationResult(
                        true,
                        true,
                        false,
                        List.of("drop table contract"),
                        List.of(MigrationChange.nonAdditive(MigrationChange.Type.RAW_SQL, null, "drop table contract"))
                )
        );
        assertEquals("migration non-additive flag must match change details", missingFlag.getMessage());

        IllegalArgumentException redundantFlag = assertThrows(
                IllegalArgumentException.class,
                () -> new MigrationResult(
                        true,
                        true,
                        true,
                        List.of("select 1"),
                        List.of(MigrationChange.additive(MigrationChange.Type.RAW_SQL, null, "select 1"))
                )
        );
        assertEquals("migration non-additive flag must match change details", redundantFlag.getMessage());
    }

    @Test
    void shouldRejectExplicitColumnDropInStrictMode() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("removed_name", varcharColumn("removed_name", 128));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .dropColumn("removed_name");

        OrmException exception = assertThrows(
                OrmException.class,
                () -> new SchemaManager(operations).ensureTable(table, MigrationOptions.strict())
        );

        assertEquals(OrmException.Code.STRICT_MIGRATION_REJECTED, exception.getCode());
        assertEquals(List.of(), operations.executedSql);
    }

    @Test
    void shouldKeepNarrowUniqueIndexWhenTargetStillDeclaresIt() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("tenant_id", varcharColumn("tenant_id", 64));
        loader.columns.get("public.contract").put("code", varcharColumn("code", 64));
        loader.indexes.put("public.contract", List.of(index("contract_code_uindex", true, "code")));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(List.of("code"), true)
                .addIndex(List.of("tenant_id", "code"), true);

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertFalse(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("drop index")));
        assertTrue(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("create unique index")));
    }

    @Test
    void shouldTreatCommonDatabaseTypeAliasesAsAligned() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("id", primaryKeyColumn("id", "character varying", 32));
        loader.columns.get("public.contract").put("code", aliasedColumn("code", "character varying", 64));
        loader.columns.get("public.contract").put("age", aliasedColumn("age", "integer", null));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("age").setType(ColumnType.INT));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertFalse(dryRun.isChanged());
    }

    @Test
    void shouldPlanExistingTableCommentOnlyWhenChanged() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        DBInfo info = existingInfo(loader);
        info.getSchema("public").getTable("contract").setDescription("Contract");
        loader.columns.get("public.contract").put("id", primaryKeyColumn("id", "varchar", 32));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper unchanged = TableWrapper.withName("contract")
                .setComment("Contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey());

        MigrationResult unchangedDryRun = new SchemaManager(operations).ensureTable(unchanged, MigrationOptions.dryRun());

        assertFalse(unchangedDryRun.isChanged());

        TableWrapper changed = TableWrapper.withName("contract")
                .setComment("Contract data")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey());

        MigrationResult changedDryRun = new SchemaManager(operations).ensureTable(changed, MigrationOptions.dryRun());

        assertTrue(changedDryRun.isChanged());
        assertTrue(changedDryRun.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.SET_TABLE_COMMENT));
    }

    @Test
    void shouldNotApplyMysqlBooleanTypeAliasesToPostgres() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("flag", aliasedColumn("flag", "bit", 1));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("flag").setType(ColumnType.BOOLEAN));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(dryRun.hasNonAdditiveChanges());
        assertTrue(dryRun.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.ALTER_COLUMN_TYPE
                        && "flag".equals(change.getTarget())));
    }

    @Test
    void shouldMapLongTextForMysqlAndPostgres() {
        FakeOperations mysqlOperations = new FakeOperations(new DBInfo("MYSQL"));
        TableWrapper mysqlTable = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("payload").setType(ColumnType.LONGTEXT));

        MigrationResult mysqlDryRun = new SchemaManager(mysqlOperations).ensureTable(mysqlTable, MigrationOptions.dryRun());

        assertTrue(mysqlDryRun.getStatements().stream().anyMatch(sql -> sql.contains("`payload` LONGTEXT")));

        FakeOperations postgresOperations = new FakeOperations(new DBInfo("POSTGRESQL"));
        TableWrapper postgresTable = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("payload").setType(ColumnType.LONGTEXT));

        MigrationResult postgresDryRun = new SchemaManager(postgresOperations).ensureTable(postgresTable, MigrationOptions.dryRun());

        assertTrue(postgresDryRun.getStatements().stream().anyMatch(sql -> sql.contains("\"payload\" text")));
    }

    @Test
    void shouldMapArrayColumnsForPostgresOnly() {
        FakeOperations postgresOperations = new FakeOperations(new DBInfo("POSTGRESQL"));
        TableWrapper postgresTable = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("tags").setType(ColumnType.ARRAY).setElementType(ColumnType.VARCHAR))
                .addColumn(Column.of("scores").setType(ColumnType.ARRAY).setElementType(ColumnType.INT));

        MigrationResult postgresDryRun = new SchemaManager(postgresOperations).ensureTable(postgresTable, MigrationOptions.dryRun());

        assertTrue(postgresDryRun.getStatements().stream().anyMatch(sql -> sql.contains("\"tags\" varchar[]")));
        assertTrue(postgresDryRun.getStatements().stream().anyMatch(sql -> sql.contains("\"scores\" int[]")));

        FakeOperations mysqlOperations = new FakeOperations(new DBInfo("MYSQL"));
        TableWrapper mysqlTable = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("tags").setType(ColumnType.ARRAY).setElementType(ColumnType.VARCHAR));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaManager(mysqlOperations).ensureTable(mysqlTable, MigrationOptions.dryRun())
        );
    }

    @Test
    void shouldTreatPostgresMetadataArrayAliasesAsSameColumnTypes() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("id", primaryKeyColumn("id", "varchar", 32));
        loader.columns.get("public.contract").put("tags", aliasedColumn("tags", "_varchar", null));
        loader.columns.get("public.contract").put("scores", aliasedColumn("scores", "_int4", null));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("tags").setType(ColumnType.ARRAY).setElementType(ColumnType.VARCHAR))
                .addColumn(Column.of("scores").setType(ColumnType.ARRAY).setElementType(ColumnType.INT));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRunStrict());

        assertFalse(dryRun.isChanged());
    }

    @Test
    void shouldRejectArrayColumnsWithoutElementType() {
        FakeOperations operations = new FakeOperations(new DBInfo("POSTGRESQL"));
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("tags").setType(ColumnType.ARRAY));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun())
        );
    }

    @Test
    void dbColumnShouldConvertPostgresArrayMetadataToColumn() {
        DBColumn dbColumn = aliasedColumn("tags", "_varchar", null);

        Column column = dbColumn.toColumn();

        assertEquals(ColumnType.ARRAY, column.getType());
        assertEquals(ColumnType.VARCHAR, column.getElementType());
    }

    @Test
    void shouldPlanTextToLongTextMigrationForMysql() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("MYSQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("payload", aliasedColumn("payload", "text", null));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("payload").setType(ColumnType.LONGTEXT));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(dryRun.hasNonAdditiveChanges());
        assertTrue(dryRun.getStatements().stream().anyMatch(sql ->
                sql.contains("modify column `payload` LONGTEXT")));
    }

    @Test
    void shouldPlanTimestampToDatetimeMigrationForMysql() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("MYSQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("occurred_at", aliasedColumn("occurred_at", "datetime", null));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("occurred_at").setType(ColumnType.TIMESTAMP));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertTrue(dryRun.hasNonAdditiveChanges());
        assertTrue(dryRun.getChanges().stream().anyMatch(change ->
                change.getType() == MigrationChange.Type.ALTER_COLUMN_TYPE
                        && "occurred_at".equals(change.getTarget())));
        assertTrue(dryRun.getStatements().stream().anyMatch(sql ->
                sql.contains("modify column `occurred_at` TIMESTAMP")));
    }

    @Test
    void shouldIgnoreLengthWhenComparingLongTextColumns() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("MYSQL"));
        existingInfo(loader);
        loader.columns.get("public.contract").put("id", primaryKeyColumn("id", "varchar", 32));
        loader.columns.get("public.contract").put("payload", aliasedColumn("payload", "longtext", null));
        FakeOperations operations = new FakeOperations(loader);
        TableWrapper table = TableWrapper.withName("contract")
                .setSchema("public")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setLength(32).setPrimaryKey())
                .addColumn(Column.of("payload").setType(ColumnType.LONGTEXT).setLength(999));

        MigrationResult dryRun = new SchemaManager(operations).ensureTable(table, MigrationOptions.dryRun());

        assertFalse(dryRun.isChanged());
    }

    private DBInfo existingInfo() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader(new DBInfo("POSTGRESQL"));
        return existingInfo(loader);
    }

    private DBInfo existingInfo(FakeMetaDataLoader loader) {
        DBInfo info = loader.getDBInfo();
        DBSchema schema = new DBSchema("public");
        schema.addTable(new DBTable(loader).setSchema("public").setName("contract"));
        info.addSchema(schema);
        loader.columns.put("public.contract", new HashMap<>(Map.of("id", idColumn())));
        loader.indexes.put("public.contract", List.of());
        return info;
    }

    private DBColumn idColumn() {
        DBColumn column = new DBColumn();
        column.setName("id");
        column.setType("varchar");
        column.setLength(32);
        column.setNullable(true);
        return column;
    }

    private DBColumn varcharColumn(String name, int length) {
        DBColumn column = new DBColumn();
        column.setName(name);
        column.setType("varchar");
        column.setLength(length);
        column.setNullable(true);
        return column;
    }

    private DBColumn aliasedColumn(String name, String type, Integer length) {
        DBColumn column = new DBColumn();
        column.setName(name);
        column.setType(type);
        if (length != null) {
            column.setLength(length);
        }
        column.setNullable(true);
        return column;
    }

    private DBColumn primaryKeyColumn(String name, String type, Integer length) {
        DBColumn column = aliasedColumn(name, type, length);
        column.setPrimaryKey(true);
        column.setNullable(false);
        return column;
    }

    private DBIndex index(String name, boolean unique, String... columns) {
        DBIndex index = new DBIndex().setName(name).setUnique(unique);
        for (String column : columns) {
            index.addColumn(column);
        }
        return index;
    }

    private static class FakeMetaDataLoader implements IMetaDataLoader {
        private final DBInfo info;
        private final Map<String, Map<String, DBColumn>> columns = new HashMap<>();
        private final Map<String, List<DBIndex>> indexes = new HashMap<>();
        private final Map<String, List<DBForeignKey>> foreignKeys = new HashMap<>();
        private int resetCount;

        private FakeMetaDataLoader(DBInfo info) {
            this.info = info;
        }

        @Override
        public DBInfo getDBInfo() {
            return info;
        }

        @Override
        public void resetInfo() {
            resetCount++;
        }

        @Override
        public List<DBIndex> getIndexList(String schema, String table) {
            return indexes.getOrDefault(schema + "." + table, List.of());
        }

        @Override
        public Map<String, DBColumn> getColumnMap(String schema, String table) {
            return columns.getOrDefault(schema + "." + table, Map.of());
        }

        @Override
        public List<DBForeignKey> getForeignKeys(String schema, String table) {
            return foreignKeys.getOrDefault(schema + "." + table, List.of());
        }
    }

    private static class FakeOperations implements IDatabaseOperations<Object> {
        private final FakeMetaDataLoader loader;
        private final List<String> executedSql = new ArrayList<>();
        private boolean failOnExecute;

        private FakeOperations(DBInfo info) {
            this(new FakeMetaDataLoader(info));
        }

        private FakeOperations(FakeMetaDataLoader loader) {
            this.loader = loader;
        }

        @Override
        public IMetaDataLoader getMetaDataLoader() {
            return loader;
        }

        @Override
        public String getPKName() {
            return "id";
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
            return null;
        }

        @Override
        public Map<String, Object> row(String sql, Map<String, Object> params) {
            return null;
        }

        @Override
        public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
            return List.of();
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            return List.of();
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
            if (failOnExecute) {
                throw new RuntimeException("simulated DDL failure");
            }
            return 1;
        }

        @Override
        public int execute(String sql, Object... params) {
            executedSql.add(sql);
            return 1;
        }

        @Override
        public int execute(String sql, List<Object> params) {
            executedSql.add(sql);
            return 1;
        }

        @Override
        public Array createArray(List<Object> list, String type) {
            throw new UnsupportedOperationException();
        }
    }
}
