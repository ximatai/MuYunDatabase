package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.orm.ManagedTable;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MuYunRepositorySchemaInitializerTest {

    @Test
    void shouldInitializeContributedTablesWithoutRepositoryEntity() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunDatabaseProperties properties = new MuYunDatabaseProperties();
        ManagedTable managedTable = ManagedTable.of("technical", TableWrapper.withName("technical"));
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(), schemaManager, properties, getClass().getClassLoader(),
                List.of(() -> List.of(managedTable)), null
        );

        initializer.afterSingletonsInstantiated();

        verify(schemaManager).ensureTables(List.of(managedTable));
        verify(schemaManager, never()).ensureTable(org.mockito.ArgumentMatchers.<Class<Object>>any());
    }

    @Test
    void shouldAggregateContributedAndRepositoryTablesBeforeMigration() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunDatabaseProperties properties = new MuYunDatabaseProperties();
        ManagedTable contributed = ManagedTable.of("technical", TableWrapper.withName("technical"));
        ManagedTable repository = ManagedTable.of("repository:" + DemoEntity.class.getName(),
                TableWrapper.withName("demo"));
        when(schemaManager.managedTableFor(DemoEntity.class)).thenReturn(repository);
        MuYunRepositoryCatalog catalog = new MuYunRepositoryCatalog(Set.of(DemoRepository.class.getName()));
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(catalog), schemaManager, properties, getClass().getClassLoader(),
                List.of(() -> List.of(contributed)), null
        );

        initializer.afterSingletonsInstantiated();

        verify(schemaManager).ensureTables(List.of(contributed, repository));
        verify(schemaManager, never()).ensureTable(DemoEntity.class);
    }

    @Test
    void shouldReportContributorReturningNullTables() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(), schemaManager, new MuYunDatabaseProperties(), getClass().getClassLoader(),
                List.of(() -> null), null
        );

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                initializer::afterSingletonsInstantiated
        );

        assertTrue(failure.getMessage().contains("Schema contributor returned null tables"));
    }

    @Test
    void shouldEnsureSchemaForAllRepositoryEntitiesWhenModeIsEnsure() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunDatabaseProperties properties = new MuYunDatabaseProperties();
        properties.setRepositorySchemaMode(MuYunDatabaseProperties.RepositorySchemaMode.ENSURE);

        MuYunRepositoryCatalog catalog = new MuYunRepositoryCatalog(Set.of(DemoRepository.class.getName()));
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(catalog),
                schemaManager,
                properties,
                getClass().getClassLoader()
        );

        initializer.afterSingletonsInstantiated();

        verify(schemaManager, times(1)).ensureTable(DemoEntity.class);
    }

    @Test
    void shouldEnsureSchemaForRepositoryThroughIntermediateGenericInterface() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunDatabaseProperties properties = new MuYunDatabaseProperties();
        properties.setRepositorySchemaMode(MuYunDatabaseProperties.RepositorySchemaMode.ENSURE);

        MuYunRepositoryCatalog catalog = new MuYunRepositoryCatalog(Set.of(IndirectRepository.class.getName()));
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(catalog),
                schemaManager,
                properties,
                getClass().getClassLoader()
        );

        initializer.afterSingletonsInstantiated();

        verify(schemaManager, times(1)).ensureTable(IndirectEntity.class);
    }


    @Test
    void shouldSkipSchemaEnsureWhenModeIsNone() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunDatabaseProperties properties = new MuYunDatabaseProperties();
        properties.setRepositorySchemaMode(MuYunDatabaseProperties.RepositorySchemaMode.NONE);

        MuYunRepositoryCatalog catalog = new MuYunRepositoryCatalog(Set.of(DemoRepository.class.getName()));
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(catalog),
                schemaManager,
                properties,
                getClass().getClassLoader()
        );

        initializer.afterSingletonsInstantiated();

        verifyNoInteractions(schemaManager);
    }

    @Test
    void shouldAllowRepositoryLevelOverride() {
        MuYunSchemaManager schemaManager = mock(MuYunSchemaManager.class);
        MuYunDatabaseProperties properties = new MuYunDatabaseProperties();
        properties.setRepositorySchemaMode(MuYunDatabaseProperties.RepositorySchemaMode.NONE);

        MuYunRepositoryCatalog catalog = new MuYunRepositoryCatalog(Set.of(
                EnabledRepository.class.getName(),
                DisabledRepository.class.getName()
        ));
        MuYunRepositorySchemaInitializer initializer = new MuYunRepositorySchemaInitializer(
                List.of(catalog),
                schemaManager,
                properties,
                getClass().getClassLoader()
        );

        initializer.afterSingletonsInstantiated();

        verify(schemaManager, times(1)).ensureTable(EnabledEntity.class);
        verify(schemaManager, never()).ensureTable(DisabledEntity.class);
    }

    @MuYunRepository
    interface DemoRepository extends EntityDao<DemoEntity, String> {
    }

    static class DemoEntity {
    }

    interface IntermediateRepository<T, ID> extends EntityDao<T, ID> {
    }

    @MuYunRepository
    interface IndirectRepository extends IntermediateRepository<IndirectEntity, String> {
    }

    static class IndirectEntity {
    }

    @MuYunRepository(alignTable = MuYunRepository.AlignTable.ENABLED)
    interface EnabledRepository extends EntityDao<EnabledEntity, String> {
    }

    static class EnabledEntity {
    }

    @MuYunRepository(alignTable = MuYunRepository.AlignTable.DISABLED)
    interface DisabledRepository extends EntityDao<DisabledEntity, String> {
    }

    static class DisabledEntity {
    }
}
