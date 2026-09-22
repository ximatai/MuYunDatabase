package net.ximatai.muyun.database.core.orm.sql;

import java.util.List;

public interface MigrationSqlDialect {

    String createSchemaIfNotExists(String schema);

    String createTableWithTempColumn(String schemaDotTable);

    default String createTableWithTempColumn(String schemaDotTable, String inheritClause) {
        if (inheritClause != null && !inheritClause.isBlank()) {
            throw new IllegalArgumentException("table inheritance is not supported by this database dialect");
        }
        return createTableWithTempColumn(schemaDotTable);
    }

    String setTableComment(String schemaDotTable, String comment);

    String addColumn(String schemaDotTable, String columnDefinition);

    String alterColumnType(String schemaDotTable, String columnName, String typeWithLength, String columnDefinition);

    String alterColumnNullable(String schemaDotTable, String columnName, boolean nullable, String columnDefinition);

    String alterColumnDefault(String schemaDotTable, String columnName, String defaultValue, String columnDefinition);

    List<String> alterColumnSequence(String schemaDotTable, String schema, String tableName, String columnName, boolean sequence);

    String setColumnComment(String schemaDotTable, String columnName, String comment, String columnDefinition);

    String dropColumn(String schemaDotTable, String columnName);

    String dropIndex(String schema, String schemaDotTable, String indexName);

    String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique);

    default String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique, String predicate) {
        if (predicate != null) {
            throw new IllegalArgumentException("partial indexes are not supported by this database dialect");
        }
        return createIndex(schemaDotTable, indexName, columns, unique);
    }

    String addConstraint(String schemaDotTable, String constraintName, String definition);

    String dropPrimaryKey(String schemaDotTable, String constraintName);

    String dropUniqueConstraint(String schemaDotTable, String constraintName);

    String dropForeignKey(String schemaDotTable, String constraintName);

    String dropTempColumn(String schemaDotTable);
}
