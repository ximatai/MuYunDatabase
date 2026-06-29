package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriteriaDialectExpressionsTest {

    @Test
    void shouldBuildCsvSetContainsExpressions() {
        assertEquals(
                "FIND_IN_SET(:p0, COALESCE(`tags`, '')) > 0",
                CriteriaDialectExpressions.collectionContains(DBInfo.Type.MYSQL, ColumnType.SET, "`tags`", ":p0")
        );
        assertEquals(
                "POSITION(',' || :p0 || ',' IN ',' || COALESCE(\"tags\", '') || ',') > 0",
                CriteriaDialectExpressions.collectionContains(DBInfo.Type.POSTGRESQL, ColumnType.SET, "\"tags\"", ":p0")
        );
    }

    @Test
    void shouldBuildJsonSetContainsExpressions() {
        assertEquals(
                "JSON_CONTAINS(CAST(`tags` AS JSON), JSON_QUOTE(:p0))",
                CriteriaDialectExpressions.collectionContains(DBInfo.Type.MYSQL, ColumnType.JSON_SET, "`tags`", ":p0")
        );
        assertEquals(
                "jsonb_exists(\"tags\"::jsonb, :p0)",
                CriteriaDialectExpressions.collectionContains(DBInfo.Type.POSTGRESQL, ColumnType.JSON_SET, "\"tags\"", ":p0")
        );
    }

    @Test
    void shouldBuildCollectionEmptyExpressions() {
        assertEquals(
                "(`tags` IS NULL OR `tags` = '')",
                CriteriaDialectExpressions.collectionIsEmpty(DBInfo.Type.MYSQL, ColumnType.SET, "`tags`")
        );
        assertEquals(
                "(\"tags\" IS NULL OR \"tags\" = '')",
                CriteriaDialectExpressions.collectionIsEmpty(DBInfo.Type.POSTGRESQL, ColumnType.SET, "\"tags\"")
        );
        assertEquals(
                "(`tags` IS NULL OR JSON_LENGTH(CAST(`tags` AS JSON)) = 0)",
                CriteriaDialectExpressions.collectionIsEmpty(DBInfo.Type.MYSQL, ColumnType.JSON_SET, "`tags`")
        );
        assertEquals(
                "(\"tags\" IS NULL OR jsonb_array_length(\"tags\"::jsonb) = 0)",
                CriteriaDialectExpressions.collectionIsEmpty(DBInfo.Type.POSTGRESQL, ColumnType.JSON_SET, "\"tags\"")
        );
    }

    @Test
    void shouldRejectNonCollectionTypesAndBlankExpressions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CriteriaDialectExpressions.collectionContains(DBInfo.Type.MYSQL, ColumnType.VARCHAR, "`name`", ":p0")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CriteriaDialectExpressions.collectionContains(DBInfo.Type.MYSQL, ColumnType.SET, "", ":p0")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> CriteriaDialectExpressions.collectionContains(DBInfo.Type.MYSQL, ColumnType.SET, "`tags`", "")
        );
    }
}
