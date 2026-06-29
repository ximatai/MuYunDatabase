package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.MigrationChange;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.quarkus.MuYunSchemaManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("quarkus-it")
@QuarkusTest
class QuarkusSchemaMigrationIntegrationTest {

    @Inject
    MuYunSchemaManager schemaManager;

    @Inject
    @SuppressWarnings("rawtypes")
    IDatabaseOperations operations;

    @Test
    void schemaManagerCreatesMigratesAndSkipsWhenAligned() {
        operations.execute("drop table if exists public.quarkus_schema_migration_test");

        MigrationResult created = schemaManager.ensureTable(SchemaEntityV1.class);
        assertTrue(created.isChanged());
        assertTrue(created.getChanges().stream()
                .anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_TABLE));

        MigrationResult migrated = schemaManager.ensureTable(SchemaEntityV2.class);
        assertTrue(migrated.isChanged());
        assertTrue(migrated.getChanges().stream()
                .anyMatch(change -> change.getType() == MigrationChange.Type.ADD_COLUMN
                        && "i_age".equals(change.getTarget())));

        MigrationResult aligned = schemaManager.ensureTable(SchemaEntityV2.class);
        assertFalse(aligned.isChanged(), () -> "Expected aligned schema, got changes: " + describe(aligned));
    }

    private static String describe(MigrationResult result) {
        return result.getChanges().stream()
                .map(change -> change.getType() + ":" + change.getTarget() + ":" + change.getSql())
                .toList()
                .toString();
    }

    @Table(name = "quarkus_schema_migration_test", schema = "public")
    public static class SchemaEntityV1 {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;
    }

    @Table(name = "quarkus_schema_migration_test", schema = "public")
    public static class SchemaEntityV2 {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        @Column(name = "i_age", type = ColumnType.INT)
        private Integer age;
    }
}
