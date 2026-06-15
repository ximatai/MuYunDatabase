package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            if (Boolean.getBoolean("muyun.postgres.it.required")) {
                throw new IllegalStateException("PostgreSQL integration tests are required, but Docker is not available.");
            }
            return Map.of("muyun.test.postgres.enabled", "false");
        }

        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("muyun_quarkus")
                .withUsername("testuser")
                .withPassword("testpass");
        postgres.start();

        Map<String, String> config = new HashMap<>();
        config.put("muyun.test.postgres.enabled", "true");
        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.datasource.jdbc.url", postgres.getJdbcUrl());
        config.put("quarkus.datasource.username", postgres.getUsername());
        config.put("quarkus.datasource.password", postgres.getPassword());
        config.put("quarkus.datasource.devservices.enabled", "false");
        config.put("muyun.database.default-schema", "public");
        config.put("muyun.database.install-postgres-plugins", "true");
        return config;
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
