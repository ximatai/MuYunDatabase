package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

public final class MuYunSchemaMigrationCoordinator {
    private static final long POSTGRES_LOCK_KEY = 0x4d7559756e44424cL;

    private final IDatabaseOperations<?> operations;
    private final TransactionTemplate transactionTemplate;
    private final boolean transactionAwareDataSource;

    public MuYunSchemaMigrationCoordinator(IDatabaseOperations<?> operations,
                                           TransactionTemplate transactionTemplate,
                                           boolean transactionAwareDataSource) {
        this.operations = Objects.requireNonNull(operations);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.transactionAwareDataSource = transactionAwareDataSource;
    }

    public void runMigration(Runnable action) {
        Objects.requireNonNull(action, "migration action must not be null");
        if (!transactionAwareDataSource) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            DBInfo databaseInfo = operations.getDBInfo();
            if ("PostgreSQL".equalsIgnoreCase(databaseInfo.getTypeName())) {
                operations.row("select pg_advisory_xact_lock(?)", List.of(POSTGRES_LOCK_KEY));
            }
            action.run();
        });
    }
}
