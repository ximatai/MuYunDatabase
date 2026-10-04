package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.PredefinedColumn;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.builder.IndexColumn;
import net.ximatai.muyun.database.core.builder.PrimaryKeyConstraint;
import net.ximatai.muyun.database.core.builder.UniqueConstraint;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationChange;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.core.orm.OrmException;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.RuntimeTableGateway;
import net.ximatai.muyun.database.core.orm.TableMeta;
import net.ximatai.muyun.database.core.orm.SchemaManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("db")
@Testcontainers
public class MuYunDatabasePostgresTest extends MuYunDatabaseUsageExamplesTestBase {

    @Container
//    private static final JdbcDatabaseContainer postgresContainer = new MySQLContainer("mysql:8.4.5")
    private static final JdbcDatabaseContainer container = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Override
    DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    Column getPrimaryKey() {
        return PredefinedColumn.Id.POSTGRES.toColumn();
    }

    @Override
    JdbcDatabaseContainer getContainer() {
        return container;
    }

    @Override
    Class<?> getEntityClass() {
        return TestEntityForPG.class;
    }

    @Test
    void longGeneratedIndexNamesShouldBeIdempotentAndDistinct() {
        String tableName = "long_index_" + "x".repeat(34);
        String prefix = "relation_" + "y".repeat(12);
        String first = prefix + "_first_id";
        String second = prefix + "_second_id";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of(first).setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of(second).setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(List.of(first), false)
                .addIndex(List.of(second), false);
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table);
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRun()).isChanged());
        assertFalse(manager.ensureTable(table));
        var indexes = db.getDBInfo().getSchema("public").getTable(tableName).getIndexList();
        assertEquals(2, indexes.size());
        assertEquals(2, indexes.stream().map(index -> index.getName()).distinct().count());
        assertTrue(indexes.stream().allMatch(index -> index.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 63));
    }

    @Test
    void shouldRequireExplicitDeletionOfOldPostgresTruncatedIndexName() {
        String tableName = "legacy_index_" + "x".repeat(32);
        String columnName = "relation_" + "y".repeat(20);
        db.execute("create table " + tableName + "(" + columnName + " varchar(64))");
        String rawName = tableName + "_" + columnName + "_index";
        db.execute("create index " + rawName + " on " + tableName + "(" + columnName + ")");
        db.resetDBInfo();
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of(columnName).setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(List.of(columnName), false);
        SchemaManager manager = new SchemaManager(db);
        MigrationResult initial = manager.ensureTable(table, MigrationOptions.dryRunStrict());
        assertEquals(List.of(MigrationChange.Type.CREATE_INDEX), initial.getChanges().stream().map(MigrationChange::getType).toList());

        String physicalName = rawName.substring(0, 63);
        table.dropIndexByName(physicalName);
        MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRun());
        assertEquals(List.of(MigrationChange.Type.DROP_INDEX, MigrationChange.Type.CREATE_INDEX),
                preview.getChanges().stream().map(MigrationChange::getType).toList());
        assertThrows(OrmException.class, () -> manager.ensureTable(table, MigrationOptions.strict()));
        assertEquals(preview.getStatements(), manager.ensureTable(table, MigrationOptions.execute()).getStatements());
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRunStrict()).isChanged());
        var indexes = db.getDBInfo().getSchema("public").getTable(tableName).getIndexList();
        assertEquals(1, indexes.size());
        assertFalse(physicalName.equals(indexes.getFirst().getName()));
    }

    @Test
    void generatedIndexShouldNotClaimCaseDistinctLegacyUniqueIndex() {
        String tableName = "case_index_" + "x".repeat(34);
        String columnName = "relation_" + "y".repeat(20);
        String legacyName = (tableName + "_" + columnName + "_index").substring(0, 63).toUpperCase(java.util.Locale.ROOT);
        db.execute("create table " + tableName + "(" + columnName + " varchar(64))");
        db.execute("create unique index \"" + legacyName + "\" on " + tableName + "(" + columnName + ")");
        db.resetDBInfo();
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of(columnName).setType(ColumnType.VARCHAR).setLength(64)).addIndex(List.of(columnName), false);
        SchemaManager manager = new SchemaManager(db);

        MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRunStrict());
        assertEquals(List.of(MigrationChange.Type.CREATE_INDEX), preview.getChanges().stream().map(MigrationChange::getType).toList());
        assertEquals(preview.getStatements(), manager.ensureTable(table, MigrationOptions.strict()).getStatements());
        assertEquals(2, db.getDBInfo().getSchema("public").getTable(tableName).getIndexList().size());
        db.execute("insert into " + tableName + " values ('SAME')");
        assertThrows(RuntimeException.class, () -> db.execute("insert into " + tableName + " values ('SAME')"));
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void quotedIndexNamesAndColumnsShouldRemainCaseSensitive() {
        String tableName = "quoted_index_identity";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("CODE").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", false).named("IX_CASE"))
                .addIndex(new Index("code", false).named("ix_case"));
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table, MigrationOptions.strict());
        assertEquals(2, db.getDBInfo().getSchema("public").getTable(tableName).getIndexList().size());
        table.getIndexes().removeIf(index -> "ix_case".equals(index.getName()));
        table.addIndex(new Index("CODE", false).named("ix_case"));

        MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRun());
        assertEquals(List.of(MigrationChange.Type.DROP_INDEX, MigrationChange.Type.CREATE_INDEX),
                preview.getChanges().stream().map(MigrationChange::getType).toList());
        assertEquals(preview.getStatements(), manager.ensureTable(table, MigrationOptions.execute()).getStatements());
        var indexes = db.getDBInfo().getSchema("public").getTable(tableName).getIndexList();
        assertEquals(List.of("CODE"), indexes.stream().filter(index -> "ix_case".equals(index.getName())).findFirst().orElseThrow().getColumns());
        assertEquals(List.of("code"), indexes.stream().filter(index -> "IX_CASE".equals(index.getName())).findFirst().orElseThrow().getColumns());
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void conflictingDropShouldFailBeforeDdlAndDesiredDefinitionShouldReplaceOnce() {
        String tableName = "same_name_index_rebuild";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", true).named("rebuild_code_lookup"));
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table, MigrationOptions.strict());
        db.execute("insert into " + tableName + " values ('SAME')");
        table.dropIndexByName("rebuild_code_lookup")
                .addColumn(Column.of("extra").setType(ColumnType.VARCHAR).setLength(64));

        OrmException error = assertThrows(OrmException.class, () -> manager.ensureTable(table, MigrationOptions.execute()));
        assertEquals(OrmException.Code.INVALID_MAPPING, error.getCode());
        assertFalse(db.getDBInfo().getSchema("public").getTable(tableName).contains("extra"));
        assertThrows(RuntimeException.class, () -> db.execute("insert into " + tableName + " values ('SAME')"));

        table.getDroppedIndexes().clear();
        table.getIndexes().clear();
        table.addIndex(new Index("code", false).named("rebuild_code_lookup"));
        MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRun());
        assertEquals(1, preview.getChanges().stream().filter(change -> change.getType() == MigrationChange.Type.DROP_INDEX).count());
        assertEquals(1, preview.getChanges().stream().filter(change -> change.getType() == MigrationChange.Type.CREATE_INDEX).count());
        assertThrows(OrmException.class, () -> manager.ensureTable(table, MigrationOptions.strict()));
        assertEquals(preview.getStatements(), manager.ensureTable(table, MigrationOptions.execute()).getStatements());
        db.execute("insert into " + tableName + "(code) values ('SAME')");
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void indexReplacementShouldPrecedeDroppingItsOldColumn() {
        String tableName = "replace_index_drop_column";
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", false).named("replace_column_lookup")));
        TableWrapper target = TableWrapper.withName(tableName)
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(64))
                .dropColumn("code").addIndex(new Index("tenant_id", false).named("replace_column_lookup"));

        MigrationResult preview = manager.ensureTable(target, MigrationOptions.dryRun());
        assertEquals(List.of(MigrationChange.Type.DROP_INDEX, MigrationChange.Type.CREATE_INDEX, MigrationChange.Type.DROP_COLUMN),
                preview.getChanges().stream().map(MigrationChange::getType).toList());
        assertEquals(preview.getStatements(), manager.ensureTable(target, MigrationOptions.execute()).getStatements());
        assertFalse(manager.ensureTable(target, MigrationOptions.dryRunStrict()).isChanged());
    }

    @Test
    void uniqueIndexOnExistingDataShouldRequireValidationAndStrictShouldNotExecute() {
        String tableName = "validate_existing_unique_index";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64));
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table);
        db.execute("insert into " + tableName + " values ('SAME'), ('SAME')");
        table.addIndex(new Index("code", true).named("validate_unique_code"));

        MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRun());
        assertEquals(MigrationChange.Risk.DATA_VALIDATION_REQUIRED, preview.getChanges().getFirst().getRisk());
        OrmException error = assertThrows(OrmException.class, () -> manager.ensureTable(table, MigrationOptions.strict()));
        assertEquals(OrmException.Code.STRICT_MIGRATION_REJECTED, error.getCode());
        assertThrows(OrmException.class, () -> manager.ensureTable(table, MigrationOptions.dryRunStrict()));
        assertThrows(RuntimeException.class, () -> manager.ensureTable(table, MigrationOptions.execute()));
        assertEquals(0, db.getDBInfo().getSchema("public").getTable(tableName).getIndexList().size());
        assertEquals(2, db.query("select code from " + tableName, List.of()).size());
    }

    @Test
    void explicitIndexDropShouldPreserveOtherIndexesOnTheSameColumn() {
        String tableName = "explicit_index_drop";
        TableWrapper table = TableWrapper.withName(tableName)
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex(new Index("code", true).named("legacy_unique_code"))
                .addIndex(new Index("code", false).named("other_code_lookup"));
        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(table);
        db.execute("insert into explicit_index_drop(code) values ('SAME')");
        assertThrows(RuntimeException.class,
                () -> db.execute("insert into explicit_index_drop(code) values ('SAME')"));

        table.getIndexes().removeIf(Index::isUnique);
        table.dropIndexByName("legacy_unique_code");
        MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRun());
        assertTrue(preview.hasNonAdditiveChanges());
        assertThrows(net.ximatai.muyun.database.core.orm.OrmException.class,
                () -> manager.ensureTable(table, MigrationOptions.strict()));
        assertTrue(db.getDBInfo().getSchema("public").getTable(tableName).getIndexList().stream()
                .anyMatch(index -> "legacy_unique_code".equals(index.getName())));

        MigrationResult applied = manager.ensureTable(table, MigrationOptions.execute());
        assertEquals(preview.getStatements(), applied.getStatements());
        assertEquals(List.of("other_code_lookup"), db.getDBInfo().getSchema("public").getTable(tableName).getIndexList().stream()
                .map(index -> index.getName()).toList());
        db.execute("insert into explicit_index_drop(code) values ('SAME')");
        assertFalse(manager.ensureTable(table, MigrationOptions.dryRun()).isChanged());
    }

    @Test
    void testGovernedTechnicalSchemaIsIdempotent() {
        String parentName = "governed_session";
        String childName = "governed_participant";
        TableWrapper parent = TableWrapper.withName(parentName)
                .setPrimaryKey(Column.of("session_id").setType(ColumnType.UUID).setPrimaryKey())
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(128).setNullable(false))
                .addColumn(Column.of("created_at").setType(ColumnType.TIMESTAMP_WITH_TIME_ZONE).setNullable(false))
                .addColumn(Column.of("latitude").setType(ColumnType.DOUBLE))
                .addColumn(Column.of("status").setType(ColumnType.VARCHAR).setLength(32).setNullable(false))
                .addUniqueConstraint(UniqueConstraint.named("uk_governed_session_scope", "tenant_id", "session_id"))
                .addIndex(Index.of(List.of(IndexColumn.asc("tenant_id"), IndexColumn.desc("created_at")), false)
                        .named("ix_governed_session_created"))
                .addIndex(new Index(List.of("tenant_id", "status"), true)
                        .named("ux_governed_session_active")
                        .predicate("status in ('ACTIVE', 'WAITING')"));
        TableWrapper child = TableWrapper.withName(childName)
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR).setLength(128).setNullable(false))
                .addColumn(Column.of("session_id").setType(ColumnType.UUID).setNullable(false))
                .addColumn(Column.of("participant_id").setType(ColumnType.VARCHAR).setLength(128).setNullable(false))
                .setPrimaryKey(PrimaryKeyConstraint.named(
                        "pk_governed_participant", "tenant_id", "session_id", "participant_id"))
                .addForeignKey(ForeignKeyConstraint.named(
                        "fk_governed_participant_session",
                        List.of("session_id"), parentName, List.of("session_id"), ForeignKeyAction.CASCADE));

        SchemaManager manager = new SchemaManager(db);
        manager.ensureTable(parent, MigrationOptions.execute());
        db.resetDBInfo();
        manager.ensureTable(child, MigrationOptions.execute());
        db.resetDBInfo();

        MigrationResult parentPlan = manager.ensureTable(parent, MigrationOptions.dryRun());
        MigrationResult childPlan = manager.ensureTable(child, MigrationOptions.dryRun());
        assertFalse(parentPlan.isChanged(), parentPlan.getStatements() + " metadata="
                + loader.getIndexList("public", parentName).stream()
                .map(index -> index.getName() + ":" + index.getPredicate()).toList());
        assertFalse(childPlan.isChanged(), childPlan.getStatements().toString());
    }

    @Test
    void testPostgresArrayCriteriaAgainstDatabase() {
        orm.ensureTable(OrmPgArrayCriteriaEntity.class);
        db.resetDBInfo();
        MigrationResult idempotent = orm.ensureTable(OrmPgArrayCriteriaEntity.class, MigrationOptions.dryRunStrict());
        assertFalse(idempotent.isChanged());
        db.execute("delete from orm_pg_array_criteria_entity");
        orm.insert(arrayRow("pg_array_1", "array_marker", List.of("red", "blue"), List.of(1, 2)));
        orm.insert(arrayRow("pg_array_2", "array_marker", List.of("green"), List.of(3)));
        orm.insert(arrayRow("pg_array_3", "array_marker", List.of(), List.of()));

        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").contains("tags", "red")
        ));
        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").containsAny("scores", List.of(2, 9))
        ));
        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").containsAll("tags", List.of("red", "blue"))
        ));
        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").isEmpty("tags")
        ));

        OrmPgArrayCriteriaEntity loaded = orm.findById(OrmPgArrayCriteriaEntity.class, "pg_array_1");
        assertEquals(List.of("red", "blue"), loaded.tags);
        assertEquals(List.of(1, 2), loaded.scores);
    }

    @Test
    void testRuntimeTableGatewayPostgresArrayCriteriaAgainstDatabase() {
        String tableName = "runtime_pg_array_record";
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_marker").setType(ColumnType.VARCHAR).setLength(64))
                .addColumn(Column.of("labels").setType(ColumnType.ARRAY).setElementType(ColumnType.VARCHAR))
                .addColumn(Column.of("scores").setType(ColumnType.ARRAY).setElementType(ColumnType.INT));
        new TableBuilder(db).build(table);
        db.execute("delete from " + tableName);

        String marker = "runtime_array_" + UUID.randomUUID().toString().substring(0, 12);
        RuntimeTableGateway gateway = new RuntimeTableGateway(
                db,
                TableMeta.builder(db.getDefaultSchemaName(), tableName)
                        .id("id", "id", ColumnType.VARCHAR, Object.class)
                        .field("marker", "v_marker", ColumnType.VARCHAR, String.class)
                        .array("labels", "labels", ColumnType.VARCHAR, List.class, String.class)
                        .array("scores", "scores", ColumnType.INT, List.class, Integer.class)
                        .build()
        );

        gateway.insert(Map.of(
                "marker", marker,
                "labels", List.of("red", "blue"),
                "scores", List.of(1, 2)
        ));
        gateway.insert(Map.of(
                "marker", marker,
                "labels", List.of("green"),
                "scores", List.of(3)
        ));
        gateway.insert(Map.of(
                "marker", marker,
                "labels", List.of(),
                "scores", List.of()
        ));

        assertEquals(1L, gateway.count(Criteria.of().eq("marker", marker).contains("labels", "red")));
        assertEquals(1L, gateway.count(Criteria.of().eq("marker", marker).containsAny("scores", List.of(2, 9))));
        assertEquals(1L, gateway.count(Criteria.of().eq("marker", marker).containsAll("labels", List.of("red", "blue"))));
        assertEquals(1L, gateway.count(Criteria.of().eq("marker", marker).isEmpty("labels")));

        List<Map<String, Object>> decoded = gateway.query(
                Criteria.of().eq("marker", marker).contains("scores", 1),
                PageRequest.of(1, 10)
        );
        assertEquals(1, decoded.size());
        assertEquals(List.of("red", "blue"), decoded.getFirst().get("labels"));
        assertEquals(List.of(1, 2), decoded.getFirst().get("scores"));
    }

    private OrmPgArrayCriteriaEntity arrayRow(String id, String marker, List<String> tags, List<Integer> scores) {
        OrmPgArrayCriteriaEntity entity = new OrmPgArrayCriteriaEntity();
        entity.id = id;
        entity.marker = marker;
        entity.tags = tags;
        entity.scores = scores;
        return entity;
    }

    @Table(name = "orm_pg_array_criteria_entity")
    public static class OrmPgArrayCriteriaEntity {
        @Id
        @net.ximatai.muyun.database.core.annotation.Column(length = 64)
        public String id;

        @net.ximatai.muyun.database.core.annotation.Column(name = "v_marker", length = 32)
        public String marker;

        @net.ximatai.muyun.database.core.annotation.Column(type = ColumnType.ARRAY, elementType = ColumnType.VARCHAR)
        public List<String> tags;

        @net.ximatai.muyun.database.core.annotation.Column(type = ColumnType.ARRAY)
        public List<Integer> scores;
    }
}
