package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("quarkus-it")
@QuarkusTest
class QuarkusRepositorySchemaDisabledIntegrationTest {

    @Inject
    DisabledSchemaRepository repository;

    @Test
    void disabledRepositoryOverridesGlobalEnsureAndDoesNotEnsureTable() {
        DisabledSchemaEntity entity = new DisabledSchemaEntity("schema-disabled-1", "disabled");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> repository.insert(entity));
        assertTrue(
                hasMessageContaining(exception, "quarkus_schema_disabled_test"),
                () -> "Expected table-not-found failure for disabled repository, got: " + exception
        );
    }

    @MuYunRepository(alignTable = MuYunRepository.AlignTable.DISABLED)
    public interface DisabledSchemaRepository extends EntityDao<DisabledSchemaEntity, String> {
    }

    @Table(name = "quarkus_schema_disabled_test", schema = "public")
    public static class DisabledSchemaEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public DisabledSchemaEntity() {
        }

        DisabledSchemaEntity(String id, String name) {
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
