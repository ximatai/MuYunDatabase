package net.ximatai.muyun.database.core.orm.sql;

import java.util.List;

public class PostgresMigrationSqlDialect implements MigrationSqlDialect {

    @Override
    public String createSchemaIfNotExists(String schema) {
        return "create schema if not exists " + schema;
    }

    @Override
    public String createTableWithTempColumn(String schemaDotTable) {
        return "create table " + schemaDotTable + "(a_temp_column int)";
    }

    @Override
    public String createTableWithTempColumn(String schemaDotTable, String inheritClause) {
        return createTableWithTempColumn(schemaDotTable) + (inheritClause == null ? "" : inheritClause);
    }

    @Override
    public String setTableComment(String schemaDotTable, String comment) {
        return "comment on table " + schemaDotTable + " is '" + comment + "'";
    }

    @Override
    public String addColumn(String schemaDotTable, String columnDefinition) {
        return "alter table " + schemaDotTable + " add " + columnDefinition;
    }

    @Override
    public String alterColumnType(String schemaDotTable, String columnName, String typeWithLength, String columnDefinition) {
        return "alter table " + schemaDotTable + " alter column " + columnName + " type " + typeWithLength;
    }

    @Override
    public String alterColumnNullable(String schemaDotTable, String columnName, boolean nullable, String columnDefinition) {
        String flag = nullable ? "drop" : "set";
        return "alter table " + schemaDotTable + " alter column " + columnName + " " + flag + " not null";
    }

    @Override
    public String alterColumnDefault(String schemaDotTable, String columnName, String defaultValue, String columnDefinition) {
        return "alter table " + schemaDotTable + " alter column " + columnName + " set default " + defaultValue;
    }

    @Override
    public List<String> alterColumnSequence(String schemaDotTable, String schema, String tableName, String columnName, boolean sequence) {
        String seq = tableName + "_" + columnName + "_sql";
        String sequenceName = quoteIdentifier(schema) + "." + quoteIdentifier(seq);
        if (sequence) {
            return List.of(
                    "create sequence if not exists " + sequenceName,
                    "alter table " + schemaDotTable + " alter column " + columnName + " set default nextval('" + sequenceName + "')"
            );
        }

        return List.of(
                "alter table " + schemaDotTable + " alter column " + columnName + " drop default",
                "drop sequence if exists " + sequenceName + ";"
        );
    }

    @Override
    public String setColumnComment(String schemaDotTable, String columnName, String comment, String columnDefinition) {
        return "comment on column " + schemaDotTable + "." + columnName + " is '" + comment + "'";
    }

    @Override
    public String dropColumn(String schemaDotTable, String columnName) {
        return "alter table " + schemaDotTable + " drop column " + columnName;
    }

    @Override
    public String dropIndex(String schema, String schemaDotTable, String indexName) {
        return "drop index " + schema + "." + indexName + ";";
    }

    @Override
    public String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique) {
        return createIndex(schemaDotTable, indexName, columns, unique, null);
    }

    @Override
    public String createIndex(String schemaDotTable, String indexName, List<String> columns, boolean unique, String predicate) {
        String uniqueString = unique ? "unique " : "";
        String where = predicate == null ? "" : " where " + predicate;
        return "create " + uniqueString + "index if not exists " + indexName + " on " + schemaDotTable + "(" + String.join(",", columns) + ")" + where + ";";
    }

    @Override
    public String addConstraint(String schemaDotTable, String constraintName, String definition) {
        return "alter table " + schemaDotTable + " add constraint " + constraintName + " " + definition;
    }

    @Override
    public String dropPrimaryKey(String schemaDotTable, String constraintName) {
        return "alter table " + schemaDotTable + " drop constraint " + constraintName;
    }

    @Override
    public String dropUniqueConstraint(String schemaDotTable, String constraintName) {
        return dropPrimaryKey(schemaDotTable, constraintName);
    }

    @Override
    public String dropForeignKey(String schemaDotTable, String constraintName) {
        return dropPrimaryKey(schemaDotTable, constraintName);
    }

    @Override
    public String dropTempColumn(String schemaDotTable) {
        return "alter table " + schemaDotTable + " drop column a_temp_column;";
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
