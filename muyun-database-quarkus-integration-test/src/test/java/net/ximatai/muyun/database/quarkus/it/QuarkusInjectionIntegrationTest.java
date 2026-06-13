package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import net.ximatai.muyun.database.quarkus.MuYunRepositoryFactory;
import net.ximatai.muyun.database.quarkus.MuYunSchemaManager;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class QuarkusInjectionIntegrationTest {

    @Inject
    Jdbi jdbi;

    @Inject
    @SuppressWarnings("rawtypes")
    IDatabaseOperations operations;

    @Inject
    SimpleEntityManager entityManager;

    @Inject
    MuYunSchemaManager schemaManager;

    @Inject
    MuYunRepositoryFactory repositoryFactory;

    @Inject
    InjectionTestRepository repository;

    @Test
    void quarkusBootstrapsExtensionBeansAndRepositoryProxy() {
        assertNotNull(jdbi);
        assertNotNull(operations);
        assertNotNull(entityManager);
        assertNotNull(schemaManager);
        assertNotNull(repositoryFactory);
        assertNotNull(repository);
        assertTrue(repository instanceof InjectionTestRepository);
        assertEquals("pong", repository.ping());
    }

    @MuYunRepository
    interface InjectionTestRepository extends EntityDao<InjectionTestEntity, String> {
        default String ping() {
            return "pong";
        }
    }

    @Table(name = "quarkus_injection_test")
    public static class InjectionTestEntity {
        @Id
        public String id;
    }

}
