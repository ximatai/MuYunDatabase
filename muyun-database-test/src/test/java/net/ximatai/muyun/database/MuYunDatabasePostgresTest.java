package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.PredefinedColumn;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
public class MuYunDatabasePostgresTest extends MuYunDatabaseUsageExamplesTestBase {

    @Container
//    private static final JdbcDatabaseContainer postgresContainer = new MySQLContainer("mysql:8.4.5")
    private static final JdbcDatabaseContainer container = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Override
    DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    Column getPrimaryKey() {
        return PredefinedColumn.Id.POSTGRES.toColumn();
    }

    @Override
    JdbcDatabaseContainer getContainer() {
        return container;
    }

    @Override
    Class<?> getEntityClass() {
        return TestEntityForPG.class;
    }

    @Test
    void testPostgresArrayCriteriaAgainstDatabase() {
        orm.ensureTable(OrmPgArrayCriteriaEntity.class);
        db.resetDBInfo();
        MigrationResult idempotent = orm.ensureTable(OrmPgArrayCriteriaEntity.class, MigrationOptions.dryRunStrict());
        assertFalse(idempotent.isChanged());
        db.execute("delete from orm_pg_array_criteria_entity");
        orm.insert(arrayRow("pg_array_1", "array_marker", List.of("red", "blue"), List.of(1, 2)));
        orm.insert(arrayRow("pg_array_2", "array_marker", List.of("green"), List.of(3)));
        orm.insert(arrayRow("pg_array_3", "array_marker", List.of(), List.of()));

        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").contains("tags", "red")
        ));
        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").containsAny("scores", List.of(2, 9))
        ));
        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").containsAll("tags", List.of("red", "blue"))
        ));
        assertEquals(1, orm.count(
                OrmPgArrayCriteriaEntity.class,
                Criteria.of().eq("marker", "array_marker").isEmpty("tags")
        ));

        OrmPgArrayCriteriaEntity loaded = orm.findById(OrmPgArrayCriteriaEntity.class, "pg_array_1");
        assertEquals(List.of("red", "blue"), loaded.tags);
        assertEquals(List.of(1, 2), loaded.scores);
    }

    private OrmPgArrayCriteriaEntity arrayRow(String id, String marker, List<String> tags, List<Integer> scores) {
        OrmPgArrayCriteriaEntity entity = new OrmPgArrayCriteriaEntity();
        entity.id = id;
        entity.marker = marker;
        entity.tags = tags;
        entity.scores = scores;
        return entity;
    }

    @Table(name = "orm_pg_array_criteria_entity")
    public static class OrmPgArrayCriteriaEntity {
        @Id
        @net.ximatai.muyun.database.core.annotation.Column(length = 64)
        public String id;

        @net.ximatai.muyun.database.core.annotation.Column(name = "v_marker", length = 32)
        public String marker;

        @net.ximatai.muyun.database.core.annotation.Column(type = ColumnType.ARRAY, elementType = ColumnType.VARCHAR)
        public List<String> tags;

        @net.ximatai.muyun.database.core.annotation.Column(type = ColumnType.ARRAY)
        public List<Integer> scores;
    }
}
