package net.ximatai.muyun.database.spring.boot.sql.internal;

import net.ximatai.muyun.database.core.orm.EntityDao;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityDaoTypeResolverTest {

    @Test
    void shouldResolveDirectEntityDaoInterface() {
        EntityDaoTypeResolver.EntityDaoTypes types = EntityDaoTypeResolver.resolve(DirectRepository.class);

        assertEquals(DemoEntity.class, types.entityType());
        assertEquals(String.class, types.idType());
    }

    @Test
    void shouldResolveMultiLevelGenericInterface() {
        EntityDaoTypeResolver.EntityDaoTypes types = EntityDaoTypeResolver.resolve(MultiLevelRepository.class);

        assertEquals(DemoEntity.class, types.entityType());
        assertEquals(Long.class, types.idType());
    }

    @Test
    void shouldReturnNullForNonEntityDaoInterface() {
        assertNull(EntityDaoTypeResolver.resolve(PlainRepository.class));
    }

    @Test
    void shouldRejectUnboundTypeVariables() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> EntityDaoTypeResolver.resolve(OpenGenericRepository.class)
        );

        assertEquals(
                "EntityDao type arguments must be concrete classes: net.ximatai.muyun.database.core.orm.EntityDao<T, ID>",
                ex.getMessage()
        );
    }

    @Test
    void shouldReturnNullForRawEntityDaoInterface() {
        assertNull(EntityDaoTypeResolver.resolve(RawRepository.class));
    }

    @Test
    void shouldIgnoreAdditionalSqlObjectMethods() {
        EntityDaoTypeResolver.EntityDaoTypes types = EntityDaoTypeResolver.resolve(HybridRepository.class);

        assertEquals(DemoEntity.class, types.entityType());
        assertEquals(String.class, types.idType());
    }

    interface DirectRepository extends EntityDao<DemoEntity, String> {
    }

    interface BaseRepository<T, ID> extends EntityDao<T, ID> {
    }

    interface MiddleRepository<T, ID> extends BaseRepository<T, ID> {
    }

    interface MultiLevelRepository extends MiddleRepository<DemoEntity, Long> {
    }

    interface PlainRepository {
        String id();
    }

    interface OpenGenericRepository<T, ID> extends EntityDao<T, ID> {
    }

    @SuppressWarnings("rawtypes")
    interface RawRepository extends EntityDao {
    }

    interface HybridRepository extends EntityDao<DemoEntity, String> {
        @SqlQuery("select count(*) from demo_entity")
        int countAll();
    }

    static class DemoEntity {
    }
}
