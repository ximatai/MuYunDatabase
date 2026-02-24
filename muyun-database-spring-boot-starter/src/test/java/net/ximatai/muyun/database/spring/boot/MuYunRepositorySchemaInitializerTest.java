package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class MuYunRepositorySchemaInitializerTest {

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
