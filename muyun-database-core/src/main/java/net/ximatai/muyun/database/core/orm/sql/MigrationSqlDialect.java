package net.ximatai.muyun.database.core.orm.sql;

import java.util.List;

public interface MigrationSqlDialect {

    String createSchemaIfNotExists(String schema);

    String createTableWithTempColumn(String schemaDotTable);

    String setTableComment(String schemaDotTable, String comment);

    String addColumn(String schemaDotTable, String columnDefinition);

    String alterColumnType(String schemaDotTable, String columnName, String typeWithLength, String columnDefinition);

    String alterColumnNullable(String schemaDotTable, String columnName, boolean nullable, String columnDefinition);

    String alterColumnDefault(String schemaDotTable, String columnName, String defaultValue, String columnDefinition);

    List<String> alterColumnSequence(String schemaDotTable, String schema, String tableName, String columnName, boolean sequence);

    String setColumnComment(String schemaDotTable, String columnName, String comment, String columnDefinition);

    String dropIndex(String schema, String schemaDotTable, String indexName);

    String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique);

    String dropTempColumn(String schemaDotTable);
}
