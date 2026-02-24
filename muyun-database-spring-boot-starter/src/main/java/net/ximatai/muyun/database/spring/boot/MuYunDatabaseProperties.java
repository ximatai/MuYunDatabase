package net.ximatai.muyun.database.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "muyun.database")
public class MuYunDatabaseProperties {

    private String primaryKeyName = "id";
    private PrimaryKeyType primaryKeyType = PrimaryKeyType.STRING;
    private String defaultSchema;
    private MigrationMode migrationMode = MigrationMode.APPLY;
    private RepositorySchemaMode repositorySchemaMode = RepositorySchemaMode.ENSURE;
    private boolean installCommonPlugins = true;
    private boolean installPostgresPlugins = true;
    private boolean transactionAwareDataSource = true;

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

    public String getPrimaryKeyName() {
        return primaryKeyName;
    }

    public void setPrimaryKeyName(String primaryKeyName) {
        this.primaryKeyName = primaryKeyName;
    }

    public PrimaryKeyType getPrimaryKeyType() {
        return primaryKeyType;
    }

    public void setPrimaryKeyType(PrimaryKeyType primaryKeyType) {
        this.primaryKeyType = primaryKeyType;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public MigrationMode getMigrationMode() {
        return migrationMode;
    }

    public void setMigrationMode(MigrationMode migrationMode) {
        this.migrationMode = migrationMode;
    }

    public RepositorySchemaMode getRepositorySchemaMode() {
        return repositorySchemaMode;
    }

    public void setRepositorySchemaMode(RepositorySchemaMode repositorySchemaMode) {
        this.repositorySchemaMode = repositorySchemaMode;
    }

    public boolean isInstallCommonPlugins() {
        return installCommonPlugins;
    }

    public void setInstallCommonPlugins(boolean installCommonPlugins) {
        this.installCommonPlugins = installCommonPlugins;
    }

    public boolean isInstallPostgresPlugins() {
        return installPostgresPlugins;
    }

    public void setInstallPostgresPlugins(boolean installPostgresPlugins) {
        this.installPostgresPlugins = installPostgresPlugins;
    }

    public boolean isTransactionAwareDataSource() {
        return transactionAwareDataSource;
    }

    public void setTransactionAwareDataSource(boolean transactionAwareDataSource) {
        this.transactionAwareDataSource = transactionAwareDataSource;
    }
}
