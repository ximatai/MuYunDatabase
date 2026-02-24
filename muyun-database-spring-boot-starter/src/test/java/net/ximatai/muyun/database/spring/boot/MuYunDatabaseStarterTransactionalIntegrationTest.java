package net.ximatai.muyun.database.spring.boot;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeBeanEntity;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeBeanRepository;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeOrmEntity;
import net.ximatai.muyun.database.spring.boot.txprobe.TxProbeRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.EnableMuYunRepositories;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
