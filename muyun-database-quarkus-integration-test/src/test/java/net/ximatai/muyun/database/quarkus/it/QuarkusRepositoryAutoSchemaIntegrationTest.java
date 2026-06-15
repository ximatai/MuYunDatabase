package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class QuarkusRepositoryAutoSchemaIntegrationTest {

    @Inject
    AutoSchemaRepository repository;

    @Test
    void startupEnsuresRepositoryEntityTable() {
        String id = "auto-schema-1";
        repository.deleteById(id);

        AutoSchemaEntity entity = new AutoSchemaEntity(id, "created");
        assertEquals(id, repository.insert(entity));
        assertEquals("created", repository.findById(id).getName());

        repository.deleteById(id);
    }

    @MuYunRepository
    public interface AutoSchemaRepository extends EntityDao<AutoSchemaEntity, String> {
    }

    @Table(name = "quarkus_auto_schema_test", schema = "public")
    public static class AutoSchemaEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public AutoSchemaEntity() {
        }

        AutoSchemaEntity(String id, String name) {
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
}
