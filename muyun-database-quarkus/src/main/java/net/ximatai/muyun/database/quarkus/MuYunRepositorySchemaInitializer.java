package net.ximatai.muyun.database.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class MuYunRepositorySchemaInitializer {

    public static final String RESOURCE = "META-INF/muyun-database-quarkus-repositories.list";

    private static final Logger log = Logger.getLogger(MuYunRepositorySchemaInitializer.class);

    @Inject
    MuYunSchemaManager schemaManager;

    @Inject
    MuYunDatabaseConfig config;

    void onStart(@Observes StartupEvent event) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (RepositoryEntityBinding binding : loadBindings(classLoader)) {
            if (!shouldAlign(binding.alignTable())) {
                continue;
            }
            Class<?> entityClass = loadEntityClass(classLoader, binding.entityClassName());
            MigrationResult result = schemaManager.ensureTable(entityClass);
            log.infof(
                    "MuYun repository schema ensured for entity %s, changed=%s",
                    entityClass.getName(),
                    result.isChanged()
            );
        }
    }

    private boolean shouldAlign(MuYunRepository.AlignTable alignTable) {
        return switch (alignTable) {
            case ENABLED -> true;
            case DISABLED -> false;
            case DEFAULT -> config.getRepositorySchemaMode() == MuYunDatabaseConfig.RepositorySchemaMode.ENSURE;
        };
    }

    private Class<?> loadEntityClass(ClassLoader classLoader, String entityClassName) {
        try {
            return Class.forName(entityClassName, false, classLoader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Failed to load MuYun repository entity: " + entityClassName, ex);
        }
    }

    private Set<RepositoryEntityBinding> loadBindings(ClassLoader classLoader) {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return Set.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                Set<RepositoryEntityBinding> bindings = new LinkedHashSet<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    bindings.add(RepositoryEntityBinding.parse(line));
                }
                return bindings;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read MuYun repository schema resource: " + RESOURCE, ex);
        }
    }

    record RepositoryEntityBinding(String entityClassName, MuYunRepository.AlignTable alignTable) {
        RepositoryEntityBinding {
            Objects.requireNonNull(entityClassName, "entityClassName must not be null");
            Objects.requireNonNull(alignTable, "alignTable must not be null");
        }

        static RepositoryEntityBinding parse(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException("Invalid MuYun repository schema binding: " + line);
            }
            return new RepositoryEntityBinding(parts[0], MuYunRepository.AlignTable.valueOf(parts[1]));
        }
    }
}
