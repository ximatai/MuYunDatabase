package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.MigrationChange;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import net.ximatai.muyun.database.quarkus.MuYunSchemaManager;
import org.eclipse.microprofile.config.Config;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgresTestResource.class, restrictToAnnotatedClass = true)
class QuarkusPostgresMatrixIntegrationTest {

    @Inject
    Config config;

    @Inject
    PostgresRepository repository;

    @Inject
    PostgresTxService txService;

    @Inject
    MuYunSchemaManager schemaManager;

    @Inject
    @SuppressWarnings("rawtypes")
    IDatabaseOperations operations;

    @Test
    void postgresSupportsEntityDaoCrudAndSqlObjectMethods() {
        requirePostgres();

        String id = "pg-crud-1";
        repository.ensureTable();
        repository.deleteById(id);

        PostgresEntity entity = new PostgresEntity(id, "created", 18);
        assertEquals(id, repository.insert(entity));
        assertTrue(repository.existsById(id));
        assertEquals("created", repository.findById(id).getName());

        entity.setName("updated");
        entity.setAge(19);
        assertEquals(1, repository.updateById(entity));
        assertEquals("updated", repository.findById(id).getName());

        assertEquals(1, repository.renameBySql(id, "sql-object"));
        assertEquals("sql-object", repository.findNameById(id));

        PostgresEntity mappedBySqlObject = repository.findBySqlObject(id);
        assertEquals(id, mappedBySqlObject.getId());
        assertEquals("sql-object", mappedBySqlObject.getName());
        assertEquals(19, mappedBySqlObject.getAge());

        Criteria criteria = Criteria.of().eq("name", "sql-object");
        List<PostgresEntity> rows = repository.query(criteria, PageRequest.of(1, 10), Sort.asc("age"));
        assertEquals(1, rows.size());
        assertEquals(id, rows.getFirst().getId());
        assertEquals(1L, repository.count(criteria));

        assertEquals(1, repository.deleteById(id));
        assertFalse(repository.existsById(id));
    }

    @Test
    void postgresRollsBackEntityDaoAndSqlObjectWritesInOneTransaction() {
        requirePostgres();
        txService.prepareSchema();

        assertThrows(RuntimeException.class, txService::insertBothThenFail);

        assertEquals(0, txService.countSqlRows());
        assertEquals(0, txService.countOrmRows());
    }

    @Test
    void postgresSchemaManagerCreatesMigratesAndSkipsWhenAligned() {
        requirePostgres();
        operations.execute("drop table if exists public.pg_schema_migration_test");

        MigrationResult created = schemaManager.ensureTable(PostgresSchemaEntityV1.class);
        assertTrue(created.isChanged());
        assertTrue(created.getChanges().stream()
                .anyMatch(change -> change.getType() == MigrationChange.Type.CREATE_TABLE));

        MigrationResult migrated = schemaManager.ensureTable(PostgresSchemaEntityV2.class);
        assertTrue(migrated.isChanged());
        assertTrue(migrated.getChanges().stream()
                .anyMatch(change -> change.getType() == MigrationChange.Type.ADD_COLUMN
                        && "i_age".equals(change.getTarget())));

        MigrationResult aligned = schemaManager.ensureTable(PostgresSchemaEntityV2.class);
        assertFalse(aligned.isChanged(), () -> "Expected aligned PostgreSQL schema, got changes: " + describe(aligned));
    }

    private void requirePostgres() {
        assumeTrue(
                config.getOptionalValue("muyun.test.postgres.enabled", Boolean.class).orElse(false),
                "Docker is not available; skipping PostgreSQL Testcontainers matrix"
        );
    }

    private static String describe(MigrationResult result) {
        return result.getChanges().stream()
                .map(change -> change.getType() + ":" + change.getTarget() + ":" + change.getSql())
                .toList()
                .toString();
    }

    @ApplicationScoped
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class PostgresTxService {
        @Inject
        PostgresTxRepository repository;

        @Inject
        IDatabaseOperations operations;

        void prepareSchema() {
            operations.execute("create table if not exists public.pg_tx_sql(id varchar(64) primary key, v_name varchar(64))");
            repository.ensureTable();
            operations.execute("delete from public.pg_tx_sql");
            operations.execute("delete from public.pg_tx_orm");
        }

        @Transactional
        public void insertBothThenFail() {
            repository.insertSql(UUID.randomUUID().toString(), "sql_row");
            repository.insert(new PostgresTxEntity(UUID.randomUUID().toString(), "orm_row"));
            throw new RuntimeException("force rollback");
        }

        int countSqlRows() {
            return repository.countSqlRows();
        }

        int countOrmRows() {
            Map<String, Object> row = operations.row("select count(*) as c from public.pg_tx_orm", Map.of());
            return row.values().stream()
                    .findFirst()
                    .map(Number.class::cast)
                    .map(Number::intValue)
                    .orElse(0);
        }
    }

    @MuYunRepository
    public interface PostgresRepository extends EntityDao<PostgresEntity, String> {
        @SqlUpdate("update public.pg_crud_test set v_name = :name where id = :id")
        int renameBySql(@Bind("id") String id, @Bind("name") String name);

        @SqlQuery("select v_name from public.pg_crud_test where id = :id")
        String findNameById(@Bind("id") String id);

        @SqlQuery("select id, v_name as name, i_age as age from public.pg_crud_test where id = :id")
        PostgresEntity findBySqlObject(@Bind("id") String id);
    }

    @MuYunRepository
    public interface PostgresTxRepository extends EntityDao<PostgresTxEntity, String> {
        @SqlUpdate("insert into public.pg_tx_sql(id, v_name) values(:id, :name)")
        int insertSql(@Bind("id") String id, @Bind("name") String name);

        @SqlQuery("select count(*) from public.pg_tx_sql")
        Integer countSqlRows();
    }

    @Table(name = "pg_crud_test", schema = "public")
    public static class PostgresEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        @Column(name = "i_age", type = ColumnType.INT)
        private Integer age;

        public PostgresEntity() {
        }

        PostgresEntity(String id, String name, Integer age) {
            this.id = id;
            this.name = name;
            this.age = age;
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

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    @Table(name = "pg_tx_orm", schema = "public")
    public static class PostgresTxEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public PostgresTxEntity() {
        }

        PostgresTxEntity(String id, String name) {
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

    @Table(name = "pg_schema_migration_test", schema = "public")
    public static class PostgresSchemaEntityV1 {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;
    }

    @Table(name = "pg_schema_migration_test", schema = "public")
    public static class PostgresSchemaEntityV2 {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        @Column(name = "i_age", type = ColumnType.INT)
        private Integer age;
    }
}
