package net.ximatai.muyun.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public abstract class MuYunDatabaseTxAndPluginTestBase extends MuYunDatabaseOrmTestBase {

    private void ensureSqlObjectBasicTable(String tableName) {
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name").setLength(64))
                .addColumn(Column.of("i_age"));
        new TableBuilder(db).build(table);
    }

    private String sqlObjectTableName(String methodName) {
        return "sql_object_basic_" + Math.abs(methodName.hashCode());
    }

    @Test
    void testSqlObjectDaoWorksWithMuYunDatabase(TestInfo testInfo) {
        String tableName = sqlObjectTableName(testInfo.getTestMethod().orElseThrow().getName());
        ensureSqlObjectBasicTable(tableName);
        SqlObjectBasicDao dao = jdbi.onDemand(SqlObjectBasicDao.class);
        String id = dao.insert(tableName, "sql_obj", 19);

        assertNotNull(id);
        assertEquals("sql_obj", dao.findNameById(tableName, id));
    }

    @Test
    void testTxRunnerRollbackAcrossOrmAndSqlObject(TestInfo testInfo) {
        String tableName = sqlObjectTableName(testInfo.getTestMethod().orElseThrow().getName());
        ensureSqlObjectBasicTable(tableName);
        orm.ensureTable(OrmPatchEntity.class);
        String patchId = UUID.randomUUID().toString();

        assertThrows(RuntimeException.class, () ->
                txRunner.inTransactionVoid(ctx -> {
                    SqlObjectBasicDao dao = ctx.attachDao(SqlObjectBasicDao.class);
                    dao.insert(tableName, "tx_rb", 30);

                    OrmPatchEntity patch = new OrmPatchEntity();
                    patch.id = patchId;
                    patch.name = "rollback_name";
                    patch.age = 30;
                    ctx.getEntityManager().insert(patch);

                    throw new RuntimeException("force rollback");
                })
        );

        SqlObjectBasicDao dao = jdbi.onDemand(SqlObjectBasicDao.class);
        assertEquals(0, dao.countByName(tableName, "tx_rb"));
        assertNull(orm.findById(OrmPatchEntity.class, patchId));
    }

    @Test
    void testSqlObjectJsonPluginWorks() {
        orm.ensureTable(OrmJsonEntity.class);
        SqlObjectJsonDao dao = jdbi.onDemand(SqlObjectJsonDao.class);
        String id = UUID.randomUUID().toString();

        Map<String, Object> payload = Map.of(
                "name", "json_user",
                "age", 9
        );

        dao.insert(id, payload);
        String loaded = dao.findPayloadText(id);
        assertNotNull(loaded);
        try {
            Map<String, Object> decoded = new ObjectMapper().readValue(loaded, Map.class);
            assertEquals("json_user", decoded.get("name"));
            assertEquals(9, ((Number) decoded.get("age")).intValue());
        } catch (Exception e) {
            fail("json payload parse failed: " + e.getMessage());
        }
    }
}
