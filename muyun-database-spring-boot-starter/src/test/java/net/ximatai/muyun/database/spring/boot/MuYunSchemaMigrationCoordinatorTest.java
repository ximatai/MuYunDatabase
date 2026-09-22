package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MuYunSchemaMigrationCoordinatorTest {

    @Test
    void shouldHoldPostgresTransactionLockAroundSchemaWork() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = mock(IDatabaseOperations.class);
        when(operations.getDBInfo()).thenReturn(new DBInfo("POSTGRESQL"));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AtomicBoolean invoked = new AtomicBoolean();

        new MuYunSchemaMigrationCoordinator(operations, new TransactionTemplate(transactionManager), true)
                .runMigration(() -> invoked.set(true));

        assertTrue(invoked.get());
        verify(operations).row(contains("pg_advisory_xact_lock"), org.mockito.ArgumentMatchers.<List<Object>>any());
        verify(transactionManager).commit(any());
    }

    @Test
    void shouldNotPretendToCoordinateWhenJdbiIsNotTransactionAware() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = mock(IDatabaseOperations.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        AtomicBoolean invoked = new AtomicBoolean();

        new MuYunSchemaMigrationCoordinator(operations, new TransactionTemplate(transactionManager), false)
                .runMigration(() -> invoked.set(true));

        assertTrue(invoked.get());
        verifyNoInteractions(operations, transactionManager);
    }
}
