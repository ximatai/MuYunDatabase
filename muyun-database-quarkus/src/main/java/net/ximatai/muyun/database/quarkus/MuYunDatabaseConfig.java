package net.ximatai.muyun.database.quarkus;

import org.eclipse.microprofile.config.Config;

import java.util.Locale;
import java.util.Optional;

public final class MuYunDatabaseConfig {

    private static final String PREFIX = "muyun.database.";

    private final String primaryKeyName;
    private final PrimaryKeyType primaryKeyType;
    private final Optional<String> defaultSchema;
    private final MigrationMode migrationMode;
    private final RepositorySchemaMode repositorySchemaMode;
    private final boolean installCommonPlugins;
    private final boolean installPostgresPlugins;

    private MuYunDatabaseConfig(String primaryKeyName,
                                PrimaryKeyType primaryKeyType,
                                Optional<String> defaultSchema,
                                MigrationMode migrationMode,
                                RepositorySchemaMode repositorySchemaMode,
                                boolean installCommonPlugins,
                                boolean installPostgresPlugins) {
        this.primaryKeyName = primaryKeyName;
        this.primaryKeyType = primaryKeyType;
        this.defaultSchema = defaultSchema;
        this.migrationMode = migrationMode;
        this.repositorySchemaMode = repositorySchemaMode;
        this.installCommonPlugins = installCommonPlugins;
        this.installPostgresPlugins = installPostgresPlugins;
    }

    public static MuYunDatabaseConfig from(Config config) {
        return new MuYunDatabaseConfig(
                read(config, "primary-key-name", "id"),
                readEnum(config, "primary-key-type", PrimaryKeyType.STRING),
                readOptional(config, "default-schema").filter(value -> !value.isBlank()),
                readEnum(config, "migration-mode", MigrationMode.APPLY),
                readEnum(config, "repository-schema-mode", RepositorySchemaMode.ENSURE),
                readBoolean(config, "install-common-plugins", true),
                readBoolean(config, "install-postgres-plugins", true)
        );
    }

    public String getPrimaryKeyName() {
        return primaryKeyName;
    }

    public PrimaryKeyType getPrimaryKeyType() {
        return primaryKeyType;
    }

    public Optional<String> getDefaultSchema() {
        return defaultSchema;
    }

    public MigrationMode getMigrationMode() {
        return migrationMode;
    }

    public RepositorySchemaMode getRepositorySchemaMode() {
        return repositorySchemaMode;
    }

    public boolean isInstallCommonPlugins() {
        return installCommonPlugins;
    }

    public boolean isInstallPostgresPlugins() {
        return installPostgresPlugins;
    }

    private static String read(Config config, String name, String defaultValue) {
        return readOptional(config, name).orElse(defaultValue);
    }

    private static boolean readBoolean(Config config, String name, boolean defaultValue) {
        return config.getOptionalValue(PREFIX + name, Boolean.class).orElse(defaultValue);
    }

    private static Optional<String> readOptional(Config config, String name) {
        return config.getOptionalValue(PREFIX + name, String.class);
    }

    private static <E extends Enum<E>> E readEnum(Config config, String name, E defaultValue) {
        return readOptional(config, name)
                .map(value -> Enum.valueOf(defaultValue.getDeclaringClass(), normalizeEnumValue(value)))
                .orElse(defaultValue);
    }

    private static String normalizeEnumValue(String value) {
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public enum PrimaryKeyType {
        STRING(String.class),
        LONG(Long.class),
        INTEGER(Integer.class),
        UUID(java.util.UUID.class);

        private final Class<?> javaType;

        PrimaryKeyType(Class<?> javaType) {
            this.javaType = javaType;
        }

        public Class<?> getJavaType() {
            return javaType;
        }
    }

    public enum MigrationMode {
        APPLY,
        DRY_RUN,
        DRY_RUN_STRICT
    }

    public enum RepositorySchemaMode {
        NONE,
        ENSURE
    }
}
