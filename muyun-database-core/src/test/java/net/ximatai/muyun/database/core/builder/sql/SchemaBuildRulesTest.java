package net.ximatai.muyun.database.core.builder.sql;

import org.junit.jupiter.api.Test;

import static net.ximatai.muyun.database.core.metadata.DBInfo.Type.MYSQL;
import static net.ximatai.muyun.database.core.metadata.DBInfo.Type.POSTGRESQL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaBuildRulesTest {

    @Test
    void shouldTreatMysqlDecimalAsNumeric() {
        assertTrue(SchemaBuildRules.sameColumnType("NUMERIC", "DECIMAL"));
    }

    @Test
    void shouldTreatMysqlBooleanCompatibleTypesAsBooleanWhenExpectedBoolean() {
        assertTrue(SchemaBuildRules.sameColumnType("BOOLEAN", "TINYINT", MYSQL, 1));
        assertTrue(SchemaBuildRules.sameColumnType("BOOLEAN", "TINYINT", MYSQL, 3));
        assertTrue(SchemaBuildRules.sameColumnType("BOOLEAN", "BIT", MYSQL, 1));
    }

    @Test
    void shouldNotTreatTinyintAsInt() {
        assertFalse(SchemaBuildRules.sameColumnType("INT", "TINYINT"));
    }

    @Test
    void shouldNotApplyMysqlBooleanCompatibilityOutsideMysql() {
        assertFalse(SchemaBuildRules.sameColumnType("BOOLEAN", "BIT", POSTGRESQL, 1));
        assertFalse(SchemaBuildRules.sameColumnType("BOOLEAN", "TINYINT", POSTGRESQL, 1));
    }

    @Test
    void shouldTreatMysqlBooleanDefaultsAsEquivalent() {
        assertTrue(SchemaBuildRules.sameColumnDefault("BOOLEAN", "TINYINT", MYSQL, 1, "FALSE", "0"));
        assertTrue(SchemaBuildRules.sameColumnDefault("BOOLEAN", "TINYINT", MYSQL, 3, "FALSE", "0"));
        assertTrue(SchemaBuildRules.sameColumnDefault("BOOLEAN", "TINYINT", MYSQL, 1, "TRUE", "1"));
        assertTrue(SchemaBuildRules.sameColumnDefault("BOOLEAN", "BIT", MYSQL, 1, "false", "b'0'"));
        assertTrue(SchemaBuildRules.sameColumnDefault("BOOLEAN", "BIT", MYSQL, 1, "true", "b'1'"));
    }

    @Test
    void shouldNotApplyMysqlBooleanDefaultCompatibilityOutsideMysql() {
        assertFalse(SchemaBuildRules.sameColumnDefault("BOOLEAN", "BIT", POSTGRESQL, 1, "false", "b'0'"));
    }

    @Test
    void shouldCompareNonBooleanDefaultsCaseInsensitively() {
        assertTrue(SchemaBuildRules.sameColumnDefault("VARCHAR", "VARCHAR", "CURRENT_TIMESTAMP", "current_timestamp"));
    }

    @Test
    void shouldCompareQuotedStringDefaultsCaseSensitively() {
        assertFalse(SchemaBuildRules.sameColumnDefault("VARCHAR", "VARCHAR", "'A'", "'a'"));
    }

    @Test
    void shouldTreatPostgresCastedStringLiteralDefaultsAsEquivalent() {
        assertTrue(SchemaBuildRules.sameColumnDefault("VARCHAR", "character varying", POSTGRESQL, 32, "'guest'", "'guest'::character varying"));
        assertTrue(SchemaBuildRules.sameColumnDefault("VARCHAR", "character varying", POSTGRESQL, 32, "'guest'", "('guest'::character varying)"));
    }

    @Test
    void shouldComparePostgresCastedStringLiteralDefaultsCaseSensitively() {
        assertFalse(SchemaBuildRules.sameColumnDefault("VARCHAR", "character varying", POSTGRESQL, 32, "'Guest'", "'guest'::character varying"));
    }
}
