package net.ximatai.muyun.database.core.sql;

import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlPlanBuilderTest {

    @Test
    void shouldExcludePrimaryKeyFromConditionalPatchIgnoringCase() {
        Map<String, DBColumn> columns = new LinkedHashMap<>();
        columns.put("ID", column("ID"));
        columns.put("name", column("name"));

        SqlPlanBuilder.PreparedSql sql = SqlPlanBuilder.preparePatchUpdateSql(
                "public",
                "sample",
                linkedMap("ID", "new-id", "name", "updated"),
                linkedMap("ID", "old-id"),
                columns,
                "id",
                DBInfo.Type.POSTGRESQL
        );

        assertFalse(sql.sql().contains("\"ID\"=:p_"));
        assertTrue(sql.sql().contains("\"name\"=:p_0"));
        assertEquals(Map.of("p_0", "updated", "w_0", "old-id"), sql.params());
    }

    @Test
    void shouldAllowSameColumnInSetAndWhereForCasUpdate() {
        Map<String, DBColumn> columns = new LinkedHashMap<>();
        columns.put("id", column("id"));
        columns.put("version", column("version"));

        SqlPlanBuilder.PreparedSql sql = SqlPlanBuilder.preparePatchUpdateSql(
                "public",
                "sample",
                linkedMap("version", 2),
                linkedMap("id", "r-1", "version", 1),
                columns,
                "id",
                DBInfo.Type.POSTGRESQL
        );

        assertTrue(sql.sql().contains("\"version\"=:p_0"));
        assertTrue(sql.sql().contains("\"version\"=:w_"));
        assertEquals(2, sql.params().get("p_0"));
    }

    private static DBColumn column(String name) {
        DBColumn column = new DBColumn();
        column.setName(name);
        return column;
    }

    private static Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
