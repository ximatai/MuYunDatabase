package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import net.ximatai.muyun.database.jdbi.JdbiRecommendedPlugins;
import net.ximatai.muyun.database.jdbi.JdbiTransactionRunner;
import net.ximatai.muyun.database.spring.boot.sql.MuYunRepositoryFactory;
import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog;
import org.jdbi.v3.core.Jdbi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass({Jdbi.class, DataSource.class})
@EnableConfigurationProperties(MuYunDatabaseProperties.class)
public class MuYunDatabaseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Jdbi jdbi(DataSource dataSource,
                     MuYunDatabaseProperties properties,
                     ObjectProvider<JdbiConfigurer> configurers) {
        DataSource effectiveDataSource = properties.isTransactionAwareDataSource()
                ? new TransactionAwareDataSourceProxy(dataSource)
                : dataSource;
        Jdbi jdbi = Jdbi.create(effectiveDataSource);

        if (properties.isInstallCommonPlugins()) {
            JdbiRecommendedPlugins.installCommon(jdbi);
        }
        if (properties.isInstallPostgresPlugins() && ClassUtils.isPresent("org.postgresql.util.PGobject", getClass().getClassLoader())) {
            JdbiRecommendedPlugins.installPostgres(jdbi);
        }

        configurers.orderedStream().forEach(configurer -> configurer.configure(jdbi));
        return jdbi;
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbiMetaDataLoader jdbiMetaDataLoader(Jdbi jdbi) {
        return new JdbiMetaDataLoader(jdbi);
    }

    @Bean
    @ConditionalOnMissingBean(IDatabaseOperations.class)
    public IDatabaseOperations<?> databaseOperations(Jdbi jdbi,
                                                     JdbiMetaDataLoader metaDataLoader,
                                                     MuYunDatabaseProperties properties) {
        String pkName = properties.getPrimaryKeyName();
        String defaultSchema = properties.getDefaultSchema();
        return switch (properties.getPrimaryKeyType()) {
            case STRING -> new StarterJdbiDatabaseOperations<>(jdbi, metaDataLoader, String.class, pkName, defaultSchema);
            case LONG -> new StarterJdbiDatabaseOperations<>(jdbi, metaDataLoader, Long.class, pkName, defaultSchema);
            case INTEGER -> new StarterJdbiDatabaseOperations<>(jdbi, metaDataLoader, Integer.class, pkName, defaultSchema);
            case UUID -> new StarterJdbiDatabaseOperations<>(jdbi, metaDataLoader, java.util.UUID.class, pkName, defaultSchema);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public SimpleEntityManager simpleEntityManager(IDatabaseOperations<?> operations) {
        return new DefaultSimpleEntityManager(operations);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbiTransactionRunner<?> jdbiTransactionRunner(Jdbi jdbi,
                                                          JdbiMetaDataLoader metaDataLoader,
                                                          MuYunDatabaseProperties properties) {
        String pkName = properties.getPrimaryKeyName();
        return switch (properties.getPrimaryKeyType()) {
            case STRING -> new JdbiTransactionRunner<>(jdbi, metaDataLoader, String.class, pkName);
            case LONG -> new JdbiTransactionRunner<>(jdbi, metaDataLoader, Long.class, pkName);
            case INTEGER -> new JdbiTransactionRunner<>(jdbi, metaDataLoader, Integer.class, pkName);
            case UUID -> new JdbiTransactionRunner<>(jdbi, metaDataLoader, java.util.UUID.class, pkName);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public MigrationOptions migrationOptions(MuYunDatabaseProperties properties) {
        return switch (properties.getMigrationMode()) {
            case APPLY -> MigrationOptions.execute();
            case DRY_RUN -> MigrationOptions.dryRun();
            case DRY_RUN_STRICT -> MigrationOptions.dryRunStrict();
        };
    }

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public MuYunTransactionBridge muYunTransactionBridge(TransactionTemplate transactionTemplate) {
        return new MuYunTransactionBridge(transactionTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public MuYunSchemaManager muYunSchemaManager(SimpleEntityManager entityManager, MigrationOptions migrationOptions) {
        return new MuYunSchemaManager(entityManager, migrationOptions);
    }

    @Bean
    @ConditionalOnMissingBean
    public MuYunRepositoryFactory muYunRepositoryFactory(IDatabaseOperations<?> operations, Environment environment, Jdbi jdbi) {
        return new MuYunRepositoryFactory(operations, environment, jdbi);
    }

    @Bean
    @ConditionalOnMissingBean
    public MuYunRepositorySchemaInitializer muYunRepositorySchemaInitializer(
            ObjectProvider<MuYunRepositoryCatalog> catalogsProvider,
            MuYunSchemaManager muYunSchemaManager,
            MuYunDatabaseProperties properties
    ) {
        List<MuYunRepositoryCatalog> catalogs = catalogsProvider.orderedStream().toList();
        return new MuYunRepositorySchemaInitializer(
                catalogs,
                muYunSchemaManager,
                properties,
                getClass().getClassLoader()
        );
    }
}
