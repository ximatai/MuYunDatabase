package net.ximatai.muyun.database.quarkus;

import io.quarkus.arc.DefaultBean;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.DatabaseValueConverter;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.core.orm.EntityMetaResolver;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import net.ximatai.muyun.database.jdbi.JdbiRecommendedPlugins;
import org.eclipse.microprofile.config.Config;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;

@ApplicationScoped
public class MuYunDatabaseProducer {

    @Produces
    @ApplicationScoped
    @DefaultBean
    MuYunDatabaseConfig muYunDatabaseConfig(Config config) {
        return MuYunDatabaseConfig.from(config);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    Jdbi jdbi(AgroalDataSource dataSource,
              MuYunDatabaseConfig config,
              @Any Instance<MuYunJdbiConfigurer> configurers) {
        Jdbi jdbi = Jdbi.create(dataSource);
        if (config.isInstallCommonPlugins()) {
            JdbiRecommendedPlugins.installCommon(jdbi);
        }
        if (config.isInstallPostgresPlugins() && isClassPresent("org.postgresql.util.PGobject")) {
            jdbi.installPlugin(new PostgresPlugin());
        }
        configurers.forEach(configurer -> configurer.configure(jdbi));
        return jdbi;
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    JdbiMetaDataLoader jdbiMetaDataLoader(Jdbi jdbi) {
        return new JdbiMetaDataLoader(jdbi);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    @SuppressWarnings("rawtypes")
    IDatabaseOperations databaseOperations(Jdbi jdbi,
                                           JdbiMetaDataLoader metaDataLoader,
                                           MuYunDatabaseConfig config) {
        String pkName = config.getPrimaryKeyName();
        String defaultSchema = config.getDefaultSchema().orElse(null);
        return switch (config.getPrimaryKeyType()) {
            case STRING -> new QuarkusJdbiDatabaseOperations<>(jdbi, metaDataLoader, String.class, pkName, defaultSchema);
            case LONG -> new QuarkusJdbiDatabaseOperations<>(jdbi, metaDataLoader, Long.class, pkName, defaultSchema);
            case INTEGER -> new QuarkusJdbiDatabaseOperations<>(jdbi, metaDataLoader, Integer.class, pkName, defaultSchema);
            case UUID -> new QuarkusJdbiDatabaseOperations<>(jdbi, metaDataLoader, java.util.UUID.class, pkName, defaultSchema);
        };
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    EntityMetaResolver entityMetaResolver() {
        return new EntityMetaResolver();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    @SuppressWarnings("rawtypes")
    SimpleEntityManager simpleEntityManager(IDatabaseOperations operations,
                                            EntityMetaResolver entityMetaResolver,
                                            Instance<DatabaseValueConverter> valueConverters) {
        DatabaseValueConverter valueConverter = valueConverters.isUnsatisfied()
                ? DatabaseValueConverter.DEFAULT
                : valueConverters.get();
        return new DefaultSimpleEntityManager(operations, entityMetaResolver, valueConverter);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    MigrationOptions migrationOptions(MuYunDatabaseConfig config) {
        return switch (config.getMigrationMode()) {
            case APPLY -> MigrationOptions.execute();
            case DRY_RUN -> MigrationOptions.dryRun();
            case DRY_RUN_STRICT -> MigrationOptions.dryRunStrict();
        };
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    MuYunSchemaManager muYunSchemaManager(SimpleEntityManager entityManager, MigrationOptions migrationOptions) {
        return new MuYunSchemaManager(entityManager, migrationOptions);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    @SuppressWarnings("rawtypes")
    MuYunRepositoryFactory muYunRepositoryFactory(IDatabaseOperations operations,
                                                  Jdbi jdbi,
                                                  SimpleEntityManager entityManager) {
        return new MuYunRepositoryFactory(operations, jdbi, entityManager);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
