package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.sql.SqlPlanBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlPlanBuilderUnitTest {

    @Test
    void shouldBuildMysqlAtomicUpsertWithSafeBindNamesForSpecialColumns() {
        SqlPlanBuilder.PreparedSql plan = SqlPlanBuilder.prepareAtomicUpsertSql(
                "testdb",
                "测试-Order_表-01",
                List.of("姓名-Name", "年龄_Age"),
                "id",
                Map.of("姓名-Name", "alice", "年龄_Age", 18),
                DBInfo.Type.MYSQL
        );

        assertTrue(plan.sql().contains("`testdb`.`测试-Order_表-01`"));
        assertTrue(plan.sql().contains("`姓名-Name`"));
        assertTrue(plan.sql().contains(":p_0"));
        assertTrue(plan.sql().contains(":p_1"));
        assertFalse(plan.sql().contains(":姓名-Name"));
        assertEquals("alice", plan.params().get("p_0"));
        assertEquals(18, plan.params().get("p_1"));
    }

    @Test
    void shouldBuildPostgresAtomicUpsertWithSafeBindNamesForSpecialColumns() {
        SqlPlanBuilder.PreparedSql plan = SqlPlanBuilder.prepareAtomicUpsertSql(
                "public",
                "测试-Order_表-01",
                List.of("姓名-Name", "年龄_Age"),
                "id",
                Map.of("姓名-Name", "alice", "年龄_Age", 18),
                DBInfo.Type.POSTGRESQL
        );

        assertTrue(plan.sql().contains("\"public\".\"测试-Order_表-01\""));
        assertTrue(plan.sql().contains("\"姓名-Name\""));
        assertTrue(plan.sql().contains("on conflict (\"id\") do update set"));
        assertFalse(plan.sql().contains(":姓名-Name"));
        assertTrue(plan.sql().contains(":p_0"));
        assertTrue(plan.sql().contains(":p_1"));
    }
}
