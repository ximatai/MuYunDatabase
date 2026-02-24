package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.SimpleOrm;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.core.sql.SqlDialectExpressions;
import net.ximatai.muyun.database.core.sql.SqlWhereClauses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Usage-oriented tests that developers can copy as integration examples.
 */
public abstract class MuYunDatabaseUsageExamplesTestBase extends MuYunDatabaseTxAndPluginTestBase {

    private String usageTableName(String methodName) {
        return "usage_demo_" + Math.abs(methodName.hashCode());
    }

    private void ensureUsageTable(String tableName) {
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name").setLength(64))
                .addColumn(Column.of("i_age"))
                .addColumn(Column.of("csv_tags").setType(ColumnType.VARCHAR).setLength(128));
        new TableBuilder(db).build(table);
    }

    @Test
    void exampleCoreLayer_mapCrudAndPatch(TestInfo testInfo) {
        String tableName = usageTableName(testInfo.getTestMethod().orElseThrow().getName());
        ensureUsageTable(tableName);

        String id = db.insertItem(tableName, Map.of(
                "v_name", "core-user",
                "i_age", 18,
                "csv_tags", "a,b,c"
        ));
        assertNotNull(id);

        Map<String, Object> loaded = db.getItem(tableName, id);
        assertNotNull(loaded);
        assertEquals("core-user", loaded.get("v_name"));

        int updated = db.patchUpdateItem(tableName, id, Map.of("v_name", "core-user-v2"));
        assertEquals(1, updated);

        Map<String, Object> patched = db.getItem(tableName, id);
        assertNotNull(patched);
        assertEquals("core-user-v2", patched.get("v_name"));
    }

    @Test
    void exampleOrmLayer_entityManagerWithCriteriaAndRepository() {
        orm.ensureTable(OrmPatchEntity.class);

        OrmPatchEntity entity = new OrmPatchEntity();
        entity.id = UUID.randomUUID().toString();
        entity.name = "orm-demo";
        entity.age = 20;
        orm.insert(entity);

        var repository = SimpleOrm.repository(OrmPatchEntity.class, orm);
        OrmPatchEntity loaded = repository.findById(entity.id);
        assertNotNull(loaded);
        assertEquals("orm-demo", loaded.name);

        List<OrmPatchEntity> rows = orm.query(
                OrmPatchEntity.class,
                Criteria.of().eq("v_name", "orm-demo"),
                PageRequest.of(1, 10),
                Sort.asc("i_age")
        );
        assertFalse(rows.isEmpty());
    }

    @Test
    void exampleJdbiLayer_transactionRunnerMixOrmAndSqlObject(TestInfo testInfo) {
        String tableName = usageTableName(testInfo.getTestMethod().orElseThrow().getName());
        ensureUsageTable(tableName);
        orm.ensureTable(OrmPatchEntity.class);
        String ormId = UUID.randomUUID().toString();

        assertThrows(RuntimeException.class, () ->
                txRunner.inTransactionVoid(ctx -> {
                    SqlObjectBasicDao dao = ctx.attachDao(SqlObjectBasicDao.class);
                    dao.insert(tableName, "tx-demo", 26);

                    OrmPatchEntity ormEntity = new OrmPatchEntity();
                    ormEntity.id = ormId;
                    ormEntity.name = "tx-orm";
                    ormEntity.age = 26;
                    ctx.getEntityManager().insert(ormEntity);

                    throw new RuntimeException("rollback-demo");
                })
        );

        SqlObjectBasicDao dao = jdbi.onDemand(SqlObjectBasicDao.class);
        assertEquals(0, dao.countByName(tableName, "tx-demo"));
        assertNull(orm.findById(OrmPatchEntity.class, ormId));
    }

    @Test
    void exampleCoreHelpers_sqlHelpersForBusinessQueryAssembly() {
        String dialectExpr = SqlDialectExpressions.csvContains(
                db.getDBInfo().getDatabaseType(),
                ":tag",
                "csv_tags"
        );
        assertNotNull(dialectExpr);
        assertFalse(dialectExpr.isBlank());

        StringBuilder sql = new StringBuilder("select * from basic where 1=1");
        List<Object> params = new ArrayList<>();
        boolean appended = SqlWhereClauses.appendEqualsConditionIfPresent(sql, params, "i_age", 18);

        assertTrue(appended);
        assertTrue(sql.toString().contains("i_age = ?"));
        assertEquals(1, params.size());
        assertEquals(18, params.getFirst());
    }
}
