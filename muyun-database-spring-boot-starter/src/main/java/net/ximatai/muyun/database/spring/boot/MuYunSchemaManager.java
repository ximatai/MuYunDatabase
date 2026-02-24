package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;

import java.util.Objects;

public class MuYunSchemaManager {

    private final SimpleEntityManager entityManager;
    private final MigrationOptions migrationOptions;

    public MuYunSchemaManager(SimpleEntityManager entityManager, MigrationOptions migrationOptions) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.migrationOptions = Objects.requireNonNull(migrationOptions);
    }

    public <T> MigrationResult ensureTable(Class<T> entityClass) {
        return entityManager.ensureTable(entityClass, migrationOptions);
    }
}
