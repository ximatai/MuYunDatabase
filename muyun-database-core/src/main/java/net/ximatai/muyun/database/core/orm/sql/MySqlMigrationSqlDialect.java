package net.ximatai.muyun.database.core.orm.sql;

import java.util.List;

public class MySqlMigrationSqlDialect implements MigrationSqlDialect {

    @Override
    public String createSchemaIfNotExists(String schema) {
        return "create schema if not exists " + schema;
    }

    @Override
    public String createTableWithTempColumn(String schemaDotTable) {
        return "create table " + schemaDotTable + "(a_temp_column int)";
    }

    @Override
    public String setTableComment(String schemaDotTable, String comment) {
        return "alter table " + schemaDotTable + " comment " + MigrationSqlDialect.stringLiteral(comment);
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
        return "alter table " + schemaDotTable + " modify column " + columnDefinition + " COMMENT "
                + MigrationSqlDialect.stringLiteral(comment);
    }

    @Override
    public String dropColumn(String schemaDotTable, String columnName) {
        return "alter table " + schemaDotTable + " drop column " + columnName + ";";
    }

    @Override
    public String dropIndex(String schema, String schemaDotTable, String indexName) {
        return "drop index " + indexName + " on " + schemaDotTable + ";";
    }

    @Override
    public String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique) {
        String uniqueString = unique ? "unique " : "";
        return "create " + uniqueString + "index " + indexName + " on " + schemaDotTable + "(" + String.join(",", columns) + ");";
    }

    @Override
    public String addConstraint(String schemaDotTable, String constraintName, String definition) {
        return "alter table " + schemaDotTable + " add constraint " + constraintName + " " + definition;
    }

    @Override
    public String dropPrimaryKey(String schemaDotTable, String constraintName) {
        return "alter table " + schemaDotTable + " drop primary key";
    }

    @Override
    public String dropUniqueConstraint(String schemaDotTable, String constraintName) {
        return "alter table " + schemaDotTable + " drop index " + constraintName;
    }

    @Override
    public String dropForeignKey(String schemaDotTable, String constraintName) {
        return "alter table " + schemaDotTable + " drop foreign key " + constraintName;
    }

    @Override
    public String dropTempColumn(String schemaDotTable) {
        return "alter table " + schemaDotTable + " drop column a_temp_column;";
    }
}
