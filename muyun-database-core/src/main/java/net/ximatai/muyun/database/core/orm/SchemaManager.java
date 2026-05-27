package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;

import java.util.Objects;

public class SchemaManager {
    private final IDatabaseOperations<?> operations;

    public SchemaManager(IDatabaseOperations<?> operations) {
        this.operations = Objects.requireNonNull(operations, "operations must not be null");
    }

    public boolean ensureTable(TableWrapper table) {
        return new TableBuilder(operations).build(table);
    }

    public MigrationResult ensureTable(TableWrapper table, MigrationOptions options) {
        Objects.requireNonNull(table, "table must not be null");
        MigrationOptions safeOptions = options == null ? MigrationOptions.execute() : options;
        SchemaMigrationPlanner.Plan plan = new SchemaMigrationPlanner(operations).plan(table);
        if (!plan.isChanged()) {
            return MigrationResult.empty(safeOptions);
        }
        if (safeOptions.isStrict() && plan.hasNonAdditive()) {
            throw new OrmException(
                    OrmException.Code.STRICT_MIGRATION_REJECTED,
                    "Strict migration rejected non-additive changes for table " + tableName(table)
            );
        }
        if (safeOptions.isDryRun()) {
            return new MigrationResult(true, true, plan.hasNonAdditive(), plan.getStatements());
        }
        ensureTable(table);
        return new MigrationResult(true, false, plan.hasNonAdditive(), plan.getStatements());
    }

    private String tableName(TableWrapper table) {
        String schema = table.getSchema() == null || table.getSchema().isBlank()
                ? operations.getDefaultSchemaName()
                : table.getSchema();
        return schema + "." + table.getName();
    }
}
