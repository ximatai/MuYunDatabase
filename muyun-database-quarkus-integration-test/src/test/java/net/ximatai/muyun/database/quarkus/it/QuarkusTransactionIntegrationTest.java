package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("quarkus-it")
@QuarkusTest
class QuarkusTransactionIntegrationTest {

    @Inject
    TxProbeService service;

    @Test
    void transactionalServiceRollsBackEntityDaoAndSqlObjectWrites() {
        service.prepareSchema();

        assertThrows(RuntimeException.class, service::insertBothThenFail);

        assertEquals(0, service.countSqlRows());
        assertEquals(0, service.countOrmRows());
    }

    @ApplicationScoped
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class TxProbeService {
        @Inject
        TxProbeRepository repository;

        @Inject
        IDatabaseOperations operations;

        void prepareSchema() {
            operations.execute("create table if not exists public.tx_probe_sql(id varchar(64) primary key, v_name varchar(64))");
            repository.ensureTable();
            operations.execute("delete from public.tx_probe_sql");
            operations.execute("delete from public.tx_probe_orm");
        }

        @Transactional
        public void insertBothThenFail() {
            repository.insertSql(UUID.randomUUID().toString(), "sql_row");
            repository.insert(new TxProbeEntity(UUID.randomUUID().toString(), "orm_row"));
            throw new RuntimeException("force rollback");
        }

        int countSqlRows() {
            return repository.countSqlRows();
        }

        int countOrmRows() {
            Map<String, Object> row = operations.row("select count(*) as c from public.tx_probe_orm", Map.of());
            return row.values().stream()
                    .findFirst()
                    .map(Number.class::cast)
                    .map(Number::intValue)
                    .orElse(0);
        }
    }

    @MuYunRepository
    public interface TxProbeRepository extends EntityDao<TxProbeEntity, String> {
        @SqlUpdate("insert into public.tx_probe_sql(id, v_name) values(:id, :name)")
        int insertSql(@Bind("id") String id, @Bind("name") String name);

        @SqlQuery("select count(*) from public.tx_probe_sql")
        Integer countSqlRows();
    }

    @Table(name = "tx_probe_orm", schema = "public")
    public static class TxProbeEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public TxProbeEntity() {
        }

        TxProbeEntity(String id, String name) {
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
