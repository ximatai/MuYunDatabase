package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.sql.SqlDialectExpressions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlDialectExpressionsUnitTest {

    @Test
    void shouldBuildMysqlCsvContainsExpression() {
        String sql = SqlDialectExpressions.csvContains(DBInfo.Type.MYSQL, "?", "groupRoleIds");
        assertEquals("FIND_IN_SET(?, groupRoleIds) > 0", sql);
    }

    @Test
    void shouldBuildPostgresCsvContainsExpression() {
        String sql = SqlDialectExpressions.csvContains(DBInfo.Type.POSTGRESQL, "?", "groupRoleIds");
        assertEquals("POSITION(',' || ? || ',' IN ',' || COALESCE(groupRoleIds, '') || ',') > 0", sql);
    }
}
