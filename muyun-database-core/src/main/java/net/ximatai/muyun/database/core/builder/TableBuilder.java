package net.ximatai.muyun.database.core.builder;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.AnnotationProcessor;
import net.ximatai.muyun.database.core.orm.SchemaManager;

import java.util.Objects;

/**
 * Backwards-compatible facade for schema reconciliation.
 *
 * <p>Planning and execution are intentionally delegated to {@link SchemaManager}
 * so dry-run output, strict validation and executed SQL share one source of truth.</p>
 */
public class TableBuilder {
    private final IDatabaseOperations<?> database;

    public TableBuilder(IDatabaseOperations<?> database) {
        this.database = Objects.requireNonNull(database, "database must not be null");
    }

    public boolean build(Class<?> entityClass) {
        return build(AnnotationProcessor.fromEntityClass(entityClass));
    }

    public boolean build(TableWrapper wrapper) {
        return new SchemaManager(database).ensureTable(wrapper);
    }
}
