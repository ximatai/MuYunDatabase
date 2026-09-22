package net.ximatai.muyun.database.spring.boot;

import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public final class MuYunSchemaMigrationCoordinator {
    private static final long POSTGRES_LOCK_KEY = 0x4d7559756e44424cL;

    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;
    private final boolean transactionAwareDataSource;

    public MuYunSchemaMigrationCoordinator(DataSource dataSource,
                                           TransactionTemplate transactionTemplate,
                                           boolean transactionAwareDataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.transactionAwareDataSource = transactionAwareDataSource;
    }

    public void runMigration(Runnable action) {
        Objects.requireNonNull(action, "migration action must not be null");
        try (Connection lockConnection = dataSource.getConnection()) {
            if (isPostgreSql(lockConnection)) {
                runWithPostgresLock(lockConnection, action);
                return;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to coordinate schema migration", e);
        }
        // Non-PostgreSQL databases need no lock. Release the probe connection
        // before the transaction manager obtains its own connection.
        executeAction(action);
    }

    private boolean isPostgreSql(Connection connection) throws SQLException {
        return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    }

    private void runWithPostgresLock(Connection connection, Runnable action) throws SQLException {
        acquireLock(connection);
        Throwable actionFailure = null;
        try {
            executeAction(action);
        } catch (RuntimeException | Error failure) {
            actionFailure = failure;
            throw failure;
        } finally {
            try {
                releaseLock(connection);
            } catch (SQLException unlockFailure) {
                if (actionFailure != null) {
                    actionFailure.addSuppressed(unlockFailure);
                } else {
                    throw unlockFailure;
                }
            }
        }
    }

    private void acquireLock(Connection connection) throws SQLException {
        executeLockStatement(connection, "select pg_advisory_lock(?)");
    }

    private void releaseLock(Connection connection) throws SQLException {
        executeLockStatement(connection, "select pg_advisory_unlock(?)");
    }

    private void executeLockStatement(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, POSTGRES_LOCK_KEY);
            statement.execute();
        }
    }

    private void executeAction(Runnable action) {
        if (transactionAwareDataSource) {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }
}
