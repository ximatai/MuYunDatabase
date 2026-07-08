package net.ximatai.muyun.database.core.builder;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.metadata.*;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableBuilderMetadataCacheTest {

    @Test
    void reusesColumnMetadataWhenNoDdlIsExecuted() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader("app", "demo")
                .withColumn(column("id", "VARCHAR", false, true))
                .withColumn(column("name", "VARCHAR", true, false));
        FakeDatabaseOperations db = new FakeDatabaseOperations(loader);

        TableWrapper wrapper = TableWrapper.withName("demo")
                .setSchema("app")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setPrimaryKey())
                .addColumn(Column.of("name").setType(ColumnType.VARCHAR));

        new TableBuilder(db).build(wrapper);

        assertEquals(1, loader.columnMapLoadCount(), "primary key and normal columns should share cached metadata");
        assertTrue(db.executedSql().isEmpty(), "matching table metadata must not emit DDL");
    }

    @Test
    void refreshesColumnMetadataAfterAddingColumnBeforeContinuingChecks() {
        FakeMetaDataLoader loader = new FakeMetaDataLoader("app", "demo")
                .withColumn(column("id", "VARCHAR", false, true));
        FakeDatabaseOperations db = new FakeDatabaseOperations(loader);
        db.onExecute(sql -> {
            String normalizedSql = sql.toLowerCase(Locale.ROOT);
            if (isAddColumnSql(normalizedSql) && normalizedSql.contains("name")) {
                loader.withColumn(column("name", "VARCHAR", true, false));
            }
        });

        TableWrapper wrapper = TableWrapper.withName("demo")
                .setSchema("app")
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR).setPrimaryKey())
                .addColumn(Column.of("name").setType(ColumnType.VARCHAR));

        new TableBuilder(db).build(wrapper);

        assertEquals(2, loader.columnMapLoadCount(), "DDL must invalidate column metadata before reading added column");
        assertEquals(1, db.executedSql().stream()
                .filter(sql -> isAddColumnSql(sql.toLowerCase(Locale.ROOT)))
                .count());
    }

    private static boolean isAddColumnSql(String normalizedSql) {
        return normalizedSql.contains("alter table") && normalizedSql.contains(" add ");
    }

    private static DBColumn column(String name, String type, boolean nullable, boolean primaryKey) {
        DBColumn column = new DBColumn();
        column.setName(name);
        column.setType(type);
        column.setLength(255);
        column.setNullable(nullable);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    private static final class FakeMetaDataLoader implements IMetaDataLoader {
        private final DBInfo info;
        private final Map<String, DBColumn> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private final AtomicInteger columnMapLoadCount = new AtomicInteger();

        private FakeMetaDataLoader(String schema, String table) {
            info = new DBInfo("MYSQL").setName(schema);
            DBSchema dbSchema = new DBSchema(schema);
            dbSchema.addTable(new DBTable(this).setSchema(schema).setName(table));
            info.addSchema(dbSchema);
        }

        private FakeMetaDataLoader withColumn(DBColumn column) {
            columns.put(column.getName(), column);
            return this;
        }

        private int columnMapLoadCount() {
            return columnMapLoadCount.get();
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
            return List.of();
        }

        @Override
        public Map<String, DBColumn> getColumnMap(String schema, String table) {
            columnMapLoadCount.incrementAndGet();
            return new TreeMap<>(columns);
        }
    }

    private static final class FakeDatabaseOperations implements IDatabaseOperations<Object> {
        private final FakeMetaDataLoader loader;
        private final List<String> executedSql = new ArrayList<>();
        private java.util.function.Consumer<String> onExecute = sql -> {
        };

        private FakeDatabaseOperations(FakeMetaDataLoader loader) {
            this.loader = loader;
        }

        private void onExecute(java.util.function.Consumer<String> onExecute) {
            this.onExecute = onExecute;
        }

        private List<String> executedSql() {
            return executedSql;
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
            onExecute.accept(sql);
            return 1;
        }

        @Override
        public int execute(String sql, Object... params) {
            return execute(sql);
        }

        @Override
        public int execute(String sql, List<Object> params) {
            return execute(sql);
        }

        @Override
        public Array createArray(List<Object> list, String type) {
            throw new UnsupportedOperationException();
        }
    }
}
