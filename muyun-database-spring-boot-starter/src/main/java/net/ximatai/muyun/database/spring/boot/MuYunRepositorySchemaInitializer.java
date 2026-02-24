package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog;
import net.ximatai.muyun.database.spring.boot.sql.repository.MuYunRepositoryCatalog.RepositoryEntityBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MuYunRepositorySchemaInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MuYunRepositorySchemaInitializer.class);

    private final List<MuYunRepositoryCatalog> catalogs;
    private final MuYunSchemaManager schemaManager;
    private final MuYunDatabaseProperties properties;
    private final ClassLoader classLoader;

    public MuYunRepositorySchemaInitializer(List<MuYunRepositoryCatalog> catalogs,
                                            MuYunSchemaManager schemaManager,
                                            MuYunDatabaseProperties properties,
                                            ClassLoader classLoader) {
        this.catalogs = catalogs;
        this.schemaManager = schemaManager;
        this.properties = properties;
        this.classLoader = classLoader;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<RepositoryEntityBinding> bindings = new LinkedHashSet<>();
        for (MuYunRepositoryCatalog catalog : catalogs) {
            bindings.addAll(catalog.resolveEntityBindings(classLoader));
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
