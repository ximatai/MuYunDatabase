package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.builder.*;
import net.ximatai.muyun.database.core.builder.sql.SchemaBuildRules;
import net.ximatai.muyun.database.core.metadata.*;
import net.ximatai.muyun.database.core.orm.sql.MigrationSqlDialect;
import net.ximatai.muyun.database.core.orm.sql.MySqlMigrationSqlDialect;
import net.ximatai.muyun.database.core.orm.sql.PostgresMigrationSqlDialect;

import java.util.*;

class SchemaMigrationPlanner {

    private final IDatabaseOperations<?> operations;
    private final DBInfo info;
    private final MigrationSqlDialect dialect;

    SchemaMigrationPlanner(IDatabaseOperations<?> operations) {
        this.operations = operations;
        this.info = operations.getDBInfo();
        this.dialect = createDialect(info.getDatabaseType());
    }

    Plan plan(TableWrapper wrapper) {
        String schema = wrapper.getSchema() == null || wrapper.getSchema().isBlank()
                ? operations.getDefaultSchemaName()
                : wrapper.getSchema();

        String table = wrapper.getName();
        assertValidIdentifier(schema, "schema");
        assertValidIdentifier(table, "table");

        PlanBuilder builder = new PlanBuilder();

        DBSchema dbSchema = info.getSchema(schema);
        if (dbSchema == null) {
            builder.addAdditive(dialect.createSchemaIfNotExists(SchemaBuildRules.quoteIdentifier(schema, getDatabaseType())));
        }

        if (dbSchema == null || !dbSchema.containsTable(table)) {
            planForNewTable(schema, table, wrapper, builder);
            return builder.build();
        }

        planForExistingTable(schema, table, wrapper, builder);
        return builder.build();
    }

    private void planForNewTable(String schema, String table, TableWrapper wrapper, PlanBuilder builder) {
        String schemaDotTable = SchemaBuildRules.qualifiedName(schema, table, getDatabaseType());
        builder.addAdditive(dialect.createTableWithTempColumn(schemaDotTable));

        if (wrapper.getComment() != null) {
            builder.addAdditive(dialect.setTableComment(schemaDotTable, wrapper.getComment()));
        }

        if (wrapper.getPrimaryKey() != null) {
            String type = resolveColumnType(wrapper.getPrimaryKey());
            builder.addAdditive(dialect.addColumn(schemaDotTable, buildColumnString(wrapper.getPrimaryKey(), type)));
        }

        for (Column column : wrapper.getColumns()) {
            String type = resolveColumnType(column);
            builder.addAdditive(dialect.addColumn(schemaDotTable, buildColumnString(column, type)));
        }

        for (Index index : wrapper.getIndexes()) {
            builder.addAdditive(buildCreateIndexSql(schemaDotTable, table, index));
        }

        builder.addAdditive(dialect.dropTempColumn(schemaDotTable));
    }

    private void planForExistingTable(String schema, String tableName, TableWrapper wrapper, PlanBuilder builder) {
        DBTable table = info.getSchema(schema).getTable(tableName);
        String schemaDotTable = SchemaBuildRules.qualifiedName(schema, tableName, getDatabaseType());

        if (wrapper.getComment() != null) {
            builder.addAdditive(dialect.setTableComment(schemaDotTable, wrapper.getComment()));
        }

        if (wrapper.getPrimaryKey() != null) {
            checkAndPlanColumn(table, wrapper.getPrimaryKey(), builder);
        }

        for (Column column : wrapper.getColumns()) {
            checkAndPlanColumn(table, column, builder);
        }

        for (Index index : wrapper.getIndexes()) {
            checkAndPlanIndex(table, index, builder);
        }
    }

    private void checkAndPlanColumn(DBTable table, Column column, PlanBuilder builder) {
        String schemaDotTable = SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType());
        String quotedColumnName = SchemaBuildRules.quoteIdentifier(column.getName(), getDatabaseType());
        assertValidIdentifier(column.getName(), "column");

        String type = resolveColumnType(column);

        String baseColumnString = buildColumnString(column, type);

        if (!table.contains(column.getName())) {
            builder.addAdditive(dialect.addColumn(schemaDotTable, baseColumnString));
            return;
        }

        DBColumn dbColumn = table.getColumn(column.getName());

        if (!type.equalsIgnoreCase(dbColumn.getType()) || (column.getLength() != null && !column.getLength().equals(dbColumn.getLength()))) {
            builder.addNonAdditive(dialect.alterColumnType(schemaDotTable, quotedColumnName, type + SchemaBuildRules.columnLength(column), baseColumnString));
        }

        if (column.isPrimaryKey() && !dbColumn.isPrimaryKey()) {
            builder.addNonAdditive("alter table " + schemaDotTable + " add primary key (" + quotedColumnName + ")");
        }

        if (dbColumn.isNullable() != column.isNullable()) {
            builder.addNonAdditive(dialect.alterColumnNullable(schemaDotTable, quotedColumnName, column.isNullable(), baseColumnString));
        }

        if (!dbColumn.isSequence() && !Objects.equals(dbColumn.getDefaultValueWithString(), column.getDefaultValue())) {
            builder.addNonAdditive(dialect.alterColumnDefault(schemaDotTable, quotedColumnName, column.getDefaultValue(), baseColumnString));
        }

        if (dbColumn.isSequence() != column.isSequence()) {
            dialect.alterColumnSequence(schemaDotTable, table.getSchema(), table.getName(), column.getName(), column.isSequence())
                    .forEach(builder::addNonAdditive);
        }

        if (column.getComment() != null && !Objects.equals(dbColumn.getDescription(), column.getComment())) {
            builder.addAdditive(dialect.setColumnComment(schemaDotTable, quotedColumnName, column.getComment(), baseColumnString));
        }
    }

    private void checkAndPlanIndex(DBTable table, Index index, PlanBuilder builder) {
        Set<String> targetColumns = new LinkedHashSet<>(index.getColumns());
        targetColumns.forEach(name -> assertValidIdentifier(name, "index column"));
        Optional<DBIndex> hit = table.getIndexList().stream()
                .filter(i -> new HashSet<>(i.getColumns()).equals(targetColumns))
                .findFirst();

        if (hit.isPresent()) {
            DBIndex dbIndex = hit.get();
            if (dbIndex.isUnique() == index.isUnique()) {
                return;
            }

            builder.addNonAdditive(dialect.dropIndex(
                    SchemaBuildRules.quoteIdentifier(table.getSchema(), getDatabaseType()),
                    SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType()),
                    SchemaBuildRules.quoteIdentifier(dbIndex.getName(), getDatabaseType())
            ));
        }

        builder.addAdditive(buildCreateIndexSql(
                SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType()),
                table.getName(),
                index
        ));
    }

    private String buildCreateIndexSql(String schemaDotTable, String tableName, Index index) {
        List<String> columns = new ArrayList<>(index.getColumns());
        columns.forEach(name -> assertValidIdentifier(name, "index column"));
        String indexName = SchemaBuildRules.indexName(tableName, index);
        assertValidIdentifier(indexName, "index");

        List<String> quotedColumns = columns.stream()
                .map(column -> SchemaBuildRules.quoteIdentifier(column, getDatabaseType()))
                .toList();
        return dialect.createIndex(
                schemaDotTable,
                SchemaBuildRules.quoteIdentifier(indexName, getDatabaseType()),
                quotedColumns,
                index.isUnique()
        );
    }

    private String buildColumnString(Column column, String type) {
        String name = column.getName();
        assertValidIdentifier(name, "column");
        return SchemaBuildRules.columnDefinition(column, type, getDatabaseType());
    }

    private String resolveColumnType(Column column) {
        String type = SchemaBuildRules.columnTypeTransform(getDatabaseType()).transform(column.getType());
        if (ColumnType.UNKNOWN.name().equals(type) || type == null) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "column type not provided: " + column.getName());
        }
        return type;
    }

    private void assertValidIdentifier(String identifier, String type) {
        if (!SchemaBuildRules.isValidIdentifier(identifier)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Invalid " + type + " identifier: " + identifier);
        }
    }

    private DBInfo.Type getDatabaseType() {
        return info.getDatabaseType();
    }

    private MigrationSqlDialect createDialect(DBInfo.Type databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> new PostgresMigrationSqlDialect();
            default -> new MySqlMigrationSqlDialect();
        };
    }

    static class Plan {
        private final List<String> statements;
        private final boolean hasNonAdditive;

        Plan(List<String> statements, boolean hasNonAdditive) {
            this.statements = statements;
            this.hasNonAdditive = hasNonAdditive;
        }

        public List<String> getStatements() {
            return statements;
        }

        public boolean hasNonAdditive() {
            return hasNonAdditive;
        }

        public boolean isChanged() {
            return !statements.isEmpty();
        }
    }

    private static class PlanBuilder {
        private final List<String> statements = new ArrayList<>();
        private boolean hasNonAdditive;

        void addAdditive(String sql) {
            statements.add(sql);
        }

        void addNonAdditive(String sql) {
            hasNonAdditive = true;
            statements.add(sql);
        }

        Plan build() {
            return new Plan(List.copyOf(statements), hasNonAdditive);
        }
    }
}
