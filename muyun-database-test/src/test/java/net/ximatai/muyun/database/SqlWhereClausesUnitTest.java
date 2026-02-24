package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.sql.SqlWhereClauses;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlWhereClausesUnitTest {

    @Test
    void shouldAppendPositionalPlaceholders() {
        StringBuilder sql = new StringBuilder("id in (");
        List<Object> params = new ArrayList<>();
        SqlWhereClauses.appendPositionalPlaceholders(sql, params, List.of("a", "b", "c"));
        sql.append(")");

        assertEquals("id in (?, ?, ?)", sql.toString());
        assertEquals(List.of("a", "b", "c"), params);
    }

    @Test
    void shouldRejectEmptyPlaceholderValues() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SqlWhereClauses.appendPositionalPlaceholders(new StringBuilder(), new ArrayList<>(), List.of())
        );
        assertEquals("values must not be empty when building SQL placeholders", ex.getMessage());
    }

    @Test
    void shouldAppendInCondition() {
        StringBuilder sql = new StringBuilder("where ");
        List<Object> params = new ArrayList<>();
        SqlWhereClauses.appendInCondition(sql, params, "roleId", List.of("r1", "r2"));

        assertEquals("where roleId IN (?, ?)", sql.toString());
        assertEquals(List.of("r1", "r2"), params);
    }

    @Test
    void shouldAppendEqualsConditionIfPresent() {
        StringBuilder sql = new StringBuilder("where 1=1");
        List<Object> params = new ArrayList<>();
        boolean appended1 = SqlWhereClauses.appendEqualsConditionIfPresent(sql, params, "moduleAlias", "crm");
        boolean appended2 = SqlWhereClauses.appendEqualsConditionIfPresent(sql, params, "actionAlias", " ");
        boolean appended3 = SqlWhereClauses.appendEqualsConditionIfPresent(sql, params, "isEnabled", null);
        boolean appended4 = SqlWhereClauses.appendEqualsConditionIfPresent(sql, params, "isEnabled", true);

        assertTrue(appended1);
        assertFalse(appended2);
        assertFalse(appended3);
        assertTrue(appended4);
        assertEquals("where 1=1 AND moduleAlias = ? AND isEnabled = ?", sql.toString());
        assertEquals(List.of("crm", true), params);
    }
}
