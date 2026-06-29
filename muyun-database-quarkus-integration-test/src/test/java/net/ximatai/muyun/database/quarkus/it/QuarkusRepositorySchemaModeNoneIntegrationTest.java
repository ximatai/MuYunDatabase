package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("quarkus-it")
@QuarkusTest
@TestProfile(QuarkusRepositorySchemaModeNoneIntegrationTest.SchemaNoneProfile.class)
class QuarkusRepositorySchemaModeNoneIntegrationTest {

    @Inject
    EnabledSchemaRepository enabledRepository;

    @Inject
    DefaultSchemaRepository defaultRepository;

    @Test
    void enabledRepositoryOverridesGlobalNoneAndEnsuresTable() {
        String id = "schema-none-enabled-1";
        enabledRepository.deleteById(id);

        EnabledSchemaEntity entity = new EnabledSchemaEntity(id, "enabled");
        assertEquals(id, enabledRepository.insert(entity));
        assertEquals("enabled", enabledRepository.findById(id).getName());

        enabledRepository.deleteById(id);
    }

    @Test
    void defaultRepositoryFollowsGlobalNoneAndDoesNotEnsureTable() {
        DefaultSchemaEntity entity = new DefaultSchemaEntity("schema-none-default-1", "default");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> defaultRepository.insert(entity));
        assertTrue(
                hasMessageContaining(exception, "quarkus_schema_none_default_test"),
                () -> "Expected table-not-found failure for default repository, got: " + exception
        );
    }

    public static class SchemaNoneProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "muyun.database.repository-schema-mode", "NONE",
                    "quarkus.datasource.jdbc.url",
                    "jdbc:h2:mem:muyun_quarkus_schema_none_it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
            );
        }
    }

    @MuYunRepository(alignTable = MuYunRepository.AlignTable.ENABLED)
    public interface EnabledSchemaRepository extends EntityDao<EnabledSchemaEntity, String> {
    }

    @MuYunRepository
    public interface DefaultSchemaRepository extends EntityDao<DefaultSchemaEntity, String> {
    }

    @Table(name = "quarkus_schema_none_enabled_test", schema = "public")
    public static class EnabledSchemaEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public EnabledSchemaEntity() {
        }

        EnabledSchemaEntity(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Table(name = "quarkus_schema_none_default_test", schema = "public")
    public static class DefaultSchemaEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public DefaultSchemaEntity() {
        }

        DefaultSchemaEntity(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static boolean hasMessageContaining(Throwable exception, String text) {
        String expected = text.toLowerCase();
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
