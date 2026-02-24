package net.ximatai.muyun.database.core.builder.sql;

import java.util.List;

public class MySqlTableBuilderSqlDialect implements TableBuilderSqlDialect {

    @Override
    public String createSchemaIfNotExists(String schema) {
        return "create schema if not exists " + schema;
    }

    @Override
    public String createTableWithTempColumn(String schemaDotTable, String inheritClause) {
        return "create table " + schemaDotTable + "(a_temp_column int)";
    }

    @Override
    public String setTableComment(String schemaDotTable, String comment) {
        return "alter table " + schemaDotTable + " comment '" + comment + "'";
    }

    @Override
    public String addColumn(String schemaDotTable, String columnDefinition) {
        return "alter table " + schemaDotTable + " add " + columnDefinition;
    }

    @Override
    public String alterColumnType(String schemaDotTable, String columnName, String typeWithLength, String columnDefinition) {
        return "alter table " + schemaDotTable + " modify column " + columnDefinition;
    }

    @Override
    public String alterColumnNullable(String schemaDotTable, String columnName, boolean nullable, String columnDefinition) {
        return "alter table " + schemaDotTable + " modify column " + columnDefinition;
    }

    @Override
    public String alterColumnDefault(String schemaDotTable, String columnName, String defaultValue, String columnDefinition) {
        return "alter table " + schemaDotTable + " modify column " + columnDefinition;
    }

    @Override
    public List<String> alterColumnSequence(String schemaDotTable, String schema, String tableName, String columnName, boolean sequence) {
        return List.of();
    }

    @Override
    public String setColumnComment(String schemaDotTable, String columnName, String comment, String columnDefinition) {
        return "alter table " + schemaDotTable + " modify column " + columnDefinition + " COMMENT '" + comment + "'";
    }

    @Override
    public String dropIndex(String schema, String schemaDotTable, String indexName) {
        return "drop index " + indexName + " on " + schemaDotTable + ";";
    }

    @Override
    public String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique) {
        String uniqueSql = unique ? "unique " : "";
        return "create " + uniqueSql + "index " + indexName + " on " + schemaDotTable + "(" + String.join(",", columns) + ");";
    }

    @Override
    public String dropTempColumn(String schemaDotTable) {
        return "alter table " + schemaDotTable + " drop column a_temp_column;";
    }
}
