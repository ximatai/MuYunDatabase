package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.orm.ManagedTable;
import net.ximatai.muyun.database.core.orm.ManagedTableSorter;
import net.ximatai.muyun.database.core.orm.SchemaManager;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class MuYunSchemaManager {

    private final SimpleEntityManager entityManager;
    private final MigrationOptions migrationOptions;
    private final IDatabaseOperations<?> operations;

    public MuYunSchemaManager(SimpleEntityManager entityManager, MigrationOptions migrationOptions) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.migrationOptions = Objects.requireNonNull(migrationOptions);
        this.operations = null;
    }

    public MuYunSchemaManager(SimpleEntityManager entityManager,
                              MigrationOptions migrationOptions,
                              IDatabaseOperations<?> operations) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.migrationOptions = Objects.requireNonNull(migrationOptions);
        this.operations = Objects.requireNonNull(operations);
    }

    public <T> MigrationResult ensureTable(Class<T> entityClass) {
        return entityManager.ensureTable(entityClass, migrationOptions);
    }

    public MigrationResult ensureTable(TableWrapper table) {
        if (operations == null) {
            throw new IllegalStateException("TableWrapper schema management requires IDatabaseOperations");
        }
        return new SchemaManager(operations).ensureTable(table, migrationOptions);
    }

    public Map<String, MigrationResult> ensureTables(Collection<ManagedTable> tables) {
        Map<String, MigrationResult> results = new LinkedHashMap<>();
        for (ManagedTable table : ManagedTableSorter.sort(tables)) {
            results.put(table.id(), ensureTable(table.table()));
        }
        return Collections.unmodifiableMap(results);
    }

    public boolean isDryRun() {
        return migrationOptions.isDryRun();
    }
}
