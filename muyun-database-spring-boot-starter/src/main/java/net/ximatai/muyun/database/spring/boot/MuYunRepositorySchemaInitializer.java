package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.orm.ManagedTable;
import net.ximatai.muyun.database.core.orm.MuYunSchemaContributor;
import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog;
import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog.RepositoryEntityBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MuYunRepositorySchemaInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MuYunRepositorySchemaInitializer.class);

    private final List<MuYunRepositoryCatalog> catalogs;
    private final MuYunSchemaManager schemaManager;
    private final MuYunDatabaseProperties properties;
    private final ClassLoader classLoader;
    private final List<MuYunSchemaContributor> contributors;
    private final MuYunSchemaMigrationCoordinator coordinator;

    public MuYunRepositorySchemaInitializer(List<MuYunRepositoryCatalog> catalogs,
                                            MuYunSchemaManager schemaManager,
                                            MuYunDatabaseProperties properties,
                                            ClassLoader classLoader) {
        this.catalogs = catalogs;
        this.schemaManager = schemaManager;
        this.properties = properties;
        this.classLoader = classLoader;
        this.contributors = List.of();
        this.coordinator = null;
    }

    public MuYunRepositorySchemaInitializer(List<MuYunRepositoryCatalog> catalogs,
                                            MuYunSchemaManager schemaManager,
                                            MuYunDatabaseProperties properties,
                                            ClassLoader classLoader,
                                            List<MuYunSchemaContributor> contributors,
                                            MuYunSchemaMigrationCoordinator coordinator) {
        this.catalogs = catalogs;
        this.schemaManager = schemaManager;
        this.properties = properties;
        this.classLoader = classLoader;
        this.contributors = contributors;
        this.coordinator = coordinator;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<ManagedTable> managedTables = new ArrayList<>();
        for (MuYunSchemaContributor contributor : contributors) {
            managedTables.addAll(contributor.tables());
        }
        Set<RepositoryEntityBinding> bindings = new LinkedHashSet<>();
        for (MuYunRepositoryCatalog catalog : catalogs) {
            bindings.addAll(catalog.resolveEntityBindings(classLoader));
        }
        if (managedTables.isEmpty() && bindings.stream().noneMatch(binding -> shouldAlign(binding.alignTable()))) {
            return;
        }
        Runnable initialization = () -> initializeSchemas(managedTables, bindings);
        if (coordinator != null && !schemaManager.isDryRun()) {
            coordinator.runMigration(initialization);
        } else {
            initialization.run();
        }
    }

    private void initializeSchemas(List<ManagedTable> managedTables, Set<RepositoryEntityBinding> bindings) {
        if (!managedTables.isEmpty()) {
            schemaManager.ensureTables(managedTables);
            log.info("MuYun managed schema ensured for {} contributed tables", managedTables.size());
        }

        for (RepositoryEntityBinding binding : bindings) {
            if (!shouldAlign(binding.alignTable())) {
                continue;
            }
            Class<?> entityClass = binding.entityClass();
            schemaManager.ensureTable(entityClass);
            log.info("MuYun repository schema ensured for entity {}", entityClass.getName());
        }
    }

    private boolean shouldAlign(net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository.AlignTable alignTable) {
        return switch (alignTable) {
            case ENABLED -> true;
            case DISABLED -> false;
            case DEFAULT -> properties.getRepositorySchemaMode() == MuYunDatabaseProperties.RepositorySchemaMode.ENSURE;
        };
    }
}
