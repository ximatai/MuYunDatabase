package net.ximatai.muyun.database.spring.boot;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MuYunSchemaMigrationCoordinatorTest {

    @Test
    void shouldHoldPostgresSessionLockAroundTransactionalSchemaWork() throws Exception {
        Fixture fixture = postgresFixture();
        Runnable action = mock(Runnable.class);

        fixture.coordinator(true).runMigration(action);

        InOrder order = inOrder(fixture.lockStatement, action, fixture.unlockStatement);
        order.verify(fixture.lockStatement).execute();
        order.verify(action).run();
        order.verify(fixture.unlockStatement).execute();
        verify(fixture.transactionManager).commit(any());
        verify(fixture.lockConnection).close();
    }

    @Test
    void shouldReleasePostgresSessionLockWhenMigrationFails() throws Exception {
        Fixture fixture = postgresFixture();
        Runnable action = mock(Runnable.class);
        org.mockito.Mockito.doThrow(new RuntimeException("migration failed")).when(action).run();

        assertThrows(RuntimeException.class, () -> fixture.coordinator(true).runMigration(action));

        InOrder order = inOrder(fixture.lockStatement, action, fixture.unlockStatement);
        order.verify(fixture.lockStatement).execute();
        order.verify(action).run();
        order.verify(fixture.unlockStatement).execute();
        verify(fixture.transactionManager).rollback(any());
        verify(fixture.lockConnection).close();
    }

    @Test
    void shouldKeepSessionLockWhenTransactionAwareDataSourceIsDisabled() throws Exception {
        Fixture fixture = postgresFixture();
        Runnable action = mock(Runnable.class);

        fixture.coordinator(false).runMigration(action);

        InOrder order = inOrder(fixture.lockStatement, action, fixture.unlockStatement);
        order.verify(fixture.lockStatement).execute();
        order.verify(action).run();
        order.verify(fixture.unlockStatement).execute();
        verifyNoInteractions(fixture.transactionManager);
    }

    private Fixture postgresFixture() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement unlock = mock(PreparedStatement.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.prepareStatement(contains("pg_advisory_lock"))).thenReturn(lock);
        when(connection.prepareStatement(contains("pg_advisory_unlock"))).thenReturn(unlock);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new Fixture(dataSource, connection, lock, unlock, transactionManager);
    }

    private record Fixture(DataSource dataSource,
                           Connection lockConnection,
                           PreparedStatement lockStatement,
                           PreparedStatement unlockStatement,
                           PlatformTransactionManager transactionManager) {
        MuYunSchemaMigrationCoordinator coordinator(boolean transactionAware) {
            return new MuYunSchemaMigrationCoordinator(
                    dataSource,
                    new TransactionTemplate(transactionManager),
                    transactionAware
            );
        }
    }
}
