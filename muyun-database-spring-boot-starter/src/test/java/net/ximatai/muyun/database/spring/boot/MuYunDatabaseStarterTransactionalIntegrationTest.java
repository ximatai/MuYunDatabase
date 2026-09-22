package net.ximatai.muyun.database.spring.boot;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.orm.MuYunEntitySchemaCustomizer;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeBeanEntity;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeBeanRepository;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeOrmEntity;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.EnableMuYunRepositories;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db")
@Testcontainers(disabledWithoutDocker = true)
class MuYunDatabaseStarterTransactionalIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuYunDatabaseAutoConfiguration.class))
            .withUserConfiguration(PostgresDataSourceConfig.class, TxIntegrationConfig.class);

    @Test
    void shouldRollbackCrudAndCustomSqlWhenUsingUnifiedRepository() {
        contextRunner.run(context -> {
            TxProbeService service = context.getBean(TxProbeService.class);
            service.prepareSchema();

            assertThrows(RuntimeException.class, service::insertBothThenFail);

            assertEquals(0, service.countSqlRows());
            assertEquals(0, service.countOrmRows());
        });
    }

    @Test
    void shouldMapEntityFromSqlQueryWithoutRegisterBeanMapper() {
        contextRunner.run(context -> {
            TxProbeService service = context.getBean(TxProbeService.class);
            service.prepareSchema();

            String id = UUID.randomUUID().toString();
            service.insertBean(id, "bean_row");
            TxProbeBeanEntity loaded = service.findBeanByIdViaSql(id);

            assertNotNull(loaded);
            assertEquals(id, loaded.getId());
            assertEquals("bean_row", loaded.getName());
        });
    }

    @Test
    void shouldSerializeConcurrentSchemaMigrationsWithPostgresSessionLock() {
        contextRunner.run(context -> {
            MuYunSchemaMigrationCoordinator coordinator = context.getBean(MuYunSchemaMigrationCoordinator.class);
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> first = executor.submit(() -> coordinator.runMigration(() -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                }));
                assertTrue(awaitWithin(firstEntered, 5, TimeUnit.SECONDS));

                Future<?> second = executor.submit(() -> {
                    secondStarted.countDown();
                    coordinator.runMigration(secondEntered::countDown);
                });
                assertTrue(awaitWithin(secondStarted, 5, TimeUnit.SECONDS));
                assertFalse(awaitWithin(secondEntered, 500, TimeUnit.MILLISECONDS));

                releaseFirst.countDown();
                awaitFuture(first);
                assertTrue(awaitWithin(secondEntered, 5, TimeUnit.SECONDS));
                awaitFuture(second);
            }
        });
    }

    @Test
    void shouldApplyAdvancedSchemaCustomizationToAnOrmEntity() {
        contextRunner.run(context -> {
            JdbiMetaDataLoader loader = context.getBean(JdbiMetaDataLoader.class);

            assertTrue(loader.getIndexList("public", "tx_probe_orm").stream()
                    .anyMatch(index -> "idx_tx_probe_named_rows".equals(index.getName())
                            && index.getPredicate() != null));
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for migration test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for migration test latch", e);
        }
    }

    private static boolean awaitWithin(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for migration test latch", e);
        }
    }

    private static void awaitFuture(Future<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Concurrent migration failed", e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PostgresDataSourceConfig {
        @Bean(destroyMethod = "close")
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(POSTGRES.getJdbcUrl());
            config.setUsername(POSTGRES.getUsername());
            config.setPassword(POSTGRES.getPassword());
            config.setDriverClassName(POSTGRES.getDriverClassName());
            return new HikariDataSource(config);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableMuYunRepositories(basePackageClasses = TxProbeRepository.class)
    static class TxIntegrationConfig {

        @Bean
        MuYunEntitySchemaCustomizer<TxProbeOrmEntity> txProbeOrmSchema() {
            return MuYunEntitySchemaCustomizer.forEntity(
                    TxProbeOrmEntity.class,
                    table -> table.addIndex(
                            new Index("v_name", false)
                                    .named("idx_tx_probe_named_rows")
                                    .predicate("v_name is not null"))
            );
        }

        @Bean
        TxProbeService txProbeService(IDatabaseOperations<?> operations,
                                      SimpleEntityManager entityManager,
                                      TxProbeRepository repository,
                                      TxProbeBeanRepository beanRepository) {
            return new TxProbeService(operations, entityManager, repository, beanRepository);
        }
    }

    static class TxProbeService {
        private final IDatabaseOperations<?> operations;
        private final SimpleEntityManager entityManager;
        private final TxProbeRepository repository;
        private final TxProbeBeanRepository beanRepository;

        TxProbeService(IDatabaseOperations<?> operations,
                       SimpleEntityManager entityManager,
                       TxProbeRepository repository,
                       TxProbeBeanRepository beanRepository) {
            this.operations = operations;
            this.entityManager = entityManager;
            this.repository = repository;
            this.beanRepository = beanRepository;
        }

        void prepareSchema() {
            operations.execute("create table if not exists tx_probe_sql(id varchar(64) primary key, v_name varchar(64))");
            entityManager.ensureTable(TxProbeOrmEntity.class);
            entityManager.ensureTable(TxProbeBeanEntity.class);
            operations.execute("delete from tx_probe_sql");
            operations.execute("delete from tx_probe_orm");
            operations.execute("delete from tx_probe_bean");
        }

        @Transactional
        public void insertBothThenFail() {
            repository.insertSql(UUID.randomUUID().toString(), "sql_row");

            TxProbeOrmEntity entity = new TxProbeOrmEntity();
            entity.id = UUID.randomUUID().toString();
            entity.name = "orm_row";
            repository.insert(entity);

            throw new RuntimeException("force rollback");
        }

        int countSqlRows() {
            return repository.countSqlRows();
        }

        int countOrmRows() {
            Map<String, Object> row = operations.row("select count(*) as c from tx_probe_orm", Map.of());
            return ((Number) row.get("c")).intValue();
        }

        void insertBean(String id, String name) {
            TxProbeBeanEntity entity = new TxProbeBeanEntity();
            entity.setId(id);
            entity.setName(name);
            beanRepository.insert(entity);
        }

        TxProbeBeanEntity findBeanByIdViaSql(String id) {
            return beanRepository.findBeanByIdViaSql(id);
        }
    }
}
