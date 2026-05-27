package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.metadata.DBColumn;
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
    void shouldPlanAndExecuteObsoleteUniqueIndexDropWhenReplacedByWiderUniqueIndex() {
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

        assertTrue(dryRun.hasNonAdditiveChanges());
        assertTrue(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("drop index")));
        assertTrue(dryRun.getStatements().stream().anyMatch(sql -> sql.contains("create unique index")));

        new SchemaManager(operations).ensureTable(table, MigrationOptions.execute());

        assertTrue(operations.executedSql.stream().anyMatch(sql -> sql.contains("drop index")));
        assertTrue(operations.executedSql.stream().anyMatch(sql -> sql.contains("create unique index")));
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

        private FakeMetaDataLoader(DBInfo info) {
            this.info = info;
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
            return indexes.getOrDefault(schema + "." + table, List.of());
        }

        @Override
        public Map<String, DBColumn> getColumnMap(String schema, String table) {
            return columns.getOrDefault(schema + "." + table, Map.of());
        }
    }

    private static class FakeOperations implements IDatabaseOperations<Object> {
        private final FakeMetaDataLoader loader;
        private final List<String> executedSql = new ArrayList<>();

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
