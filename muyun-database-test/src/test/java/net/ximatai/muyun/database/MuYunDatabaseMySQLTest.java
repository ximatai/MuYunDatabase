package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.PredefinedColumn;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.RuntimeColumnMapper;
import net.ximatai.muyun.database.core.orm.RuntimeTableGateway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("db")
@Testcontainers
public class MuYunDatabaseMySQLTest extends MuYunDatabaseUsageExamplesTestBase {

    @Container
//    private static final JdbcDatabaseContainer postgresContainer = new MySQLContainer("mysql:8.4.5")
    private static final JdbcDatabaseContainer container = new MySQLContainer()
            .withDatabaseName("testdb")
            .withUsername("root")
            .withPassword("testpass");

    @Override
    DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    Column getPrimaryKey() {
        return PredefinedColumn.Id.MYSQL.toColumn();
    }

    @Override
    JdbcDatabaseContainer getContainer() {
        return container;
    }

    @Override
    Class<?> getEntityClass() {
        return TestEntityForMysql.class;
    }

    @Test
    void testLikeIgnoreCaseAgainstCaseSensitiveCollation() {
        String tableName = "runtime_case_sensitive_record";
        TableWrapper table = TableWrapper.withName(tableName)
                .setPrimaryKey(getPrimaryKey())
                .addColumn(Column.of("v_name").setType(ColumnType.VARCHAR).setLength(64));
        new TableBuilder(db).build(table);
        db.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `v_name` "
                + "VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        db.execute("delete from " + tableName);

        RuntimeTableGateway gateway = new RuntimeTableGateway(
                db,
                db.getDefaultSchemaName(),
                tableName,
                RuntimeColumnMapper.of(Map.of("id", "id", "name", "v_name"))
        );
        gateway.insert(Map.of("name", "CaseSensitiveValue"));

        String lowercasePattern = "%casesensitivevalue%";
        assertEquals(0L, gateway.count(Criteria.of().like("name", lowercasePattern)));
        assertEquals(1L, gateway.count(Criteria.of().likeIgnoreCase("name", lowercasePattern)));
    }
}
