package net.ximatai.muyun.database.samples.starterminimal;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TxDemoService {

    private final IDatabaseOperations<?> operations;
    private final SimpleEntityManager orm;
    private final DemoUserRepository dao;

    public TxDemoService(IDatabaseOperations<?> operations, SimpleEntityManager orm, DemoUserRepository dao) {
        this.operations = operations;
        this.orm = orm;
        this.dao = dao;
    }

    public void runRollbackDemo() {
        prepareSchema();
        try {
            insertBothThenFail();
        } catch (RuntimeException ignored) {
            // expected rollback
        }

        int sqlCount = dao.countSqlRows();
        Map<String, Object> ormCount = operations.row("select count(*) as c from sample_tx_orm", Map.of());
        System.out.println("sql_count=" + sqlCount + ", orm_count=" + ormCount.get("c"));
    }

    private void prepareSchema() {
        operations.execute("create table if not exists sample_tx_sql(id varchar(64) primary key, v_name varchar(64))");
        orm.ensureTable(DemoOrmEntity.class);
        operations.execute("delete from sample_tx_sql");
        operations.execute("delete from sample_tx_orm");
    }

    @Transactional
    public void insertBothThenFail() {
        dao.insertSqlRow(UUID.randomUUID().toString(), "sql-row");

        DemoOrmEntity entity = new DemoOrmEntity();
        entity.id = UUID.randomUUID().toString();
        entity.name = "orm-row";
        dao.insert(entity);

        throw new RuntimeException("force rollback");
    }
}
