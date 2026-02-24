package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import net.ximatai.muyun.database.jdbi.JdbiTransactionRunner;
import net.ximatai.muyun.database.spring.boot.sql.MuYunRepositoryFactory;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MuYunDatabaseAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuYunDatabaseAutoConfiguration.class))
            .withUserConfiguration(MockDataSourceConfig.class);

    @Test
    void shouldAutoConfigureCoreBeans() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean(Jdbi.class));
            assertNotNull(context.getBean(JdbiMetaDataLoader.class));
            assertNotNull(context.getBean(IDatabaseOperations.class));
            assertNotNull(context.getBean(SimpleEntityManager.class));
            assertNotNull(context.getBean(JdbiTransactionRunner.class));
            assertNotNull(context.getBean(MuYunTransactionBridge.class));
            assertNotNull(context.getBean(MuYunRepositoryFactory.class));
            assertNotNull(context.getBean(PlatformTransactionManager.class));
        });
    }

    @Test
    void shouldBindPrimaryKeyProperties() {
        contextRunner
                .withPropertyValues(
                        "muyun.database.primary-key-name=custom_id",
                        "muyun.database.primary-key-type=UUID",
                        "muyun.database.default-schema=custom_schema",
                        "muyun.database.migration-mode=DRY_RUN_STRICT",
                        "muyun.database.repository-schema-mode=NONE"
                )
                .run(context -> {
                    IDatabaseOperations<?> operations = context.getBean(IDatabaseOperations.class);
                    assertEquals("custom_id", operations.getPKName());
                    assertEquals("custom_schema", operations.getDefaultSchemaName());
                    assertInstanceOf(JdbiDatabaseOperations.class, operations);
                    JdbiDatabaseOperations<?> jdbiOps = (JdbiDatabaseOperations<?>) operations;
                    assertEquals(java.util.UUID.class, jdbiOps.getPkType());

                    MigrationOptions migrationOptions = context.getBean(MigrationOptions.class);
                    assertTrue(migrationOptions.isDryRun());
                    assertTrue(migrationOptions.isStrict());

                    MuYunDatabaseProperties properties = context.getBean(MuYunDatabaseProperties.class);
                    assertEquals(MuYunDatabaseProperties.RepositorySchemaMode.NONE, properties.getRepositorySchemaMode());
                });
    }

    @Test
    void shouldApplyExternalJdbiConfigurer() {
        contextRunner
                .withUserConfiguration(JdbiConfigurerConfig.class)
                .run(context -> {
                    AtomicBoolean configured = context.getBean("jdbiConfigured", AtomicBoolean.class);
                    assertTrue(configured.get());
                });
    }

    @Test
    void shouldBackOffWhenTransactionManagerProvided() {
        contextRunner
                .withUserConfiguration(CustomTxManagerConfig.class)
                .run(context -> {
                    PlatformTransactionManager manager = context.getBean(PlatformTransactionManager.class);
                    assertSame(context.getBean("customTxManager"), manager);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MockDataSourceConfig {
        @Bean
        DataSource dataSource() {
            return Mockito.mock(DataSource.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbiConfigurerConfig {
        @Bean("jdbiConfigured")
        AtomicBoolean jdbiConfigured() {
            return new AtomicBoolean(false);
        }

        @Bean
        JdbiConfigurer jdbiConfigurer(AtomicBoolean jdbiConfigured) {
            return jdbi -> jdbiConfigured.set(true);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTxManagerConfig {
        @Bean("customTxManager")
        PlatformTransactionManager customTxManager() {
            return Mockito.mock(PlatformTransactionManager.class);
        }
    }
}
