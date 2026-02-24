package net.ximatai.muyun.database.samples.starterminimal;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@MuYunRepository
public interface DemoUserRepository extends EntityDao<DemoOrmEntity, String> {

    @SqlUpdate("insert into sample_tx_sql(id, v_name) values(:id, :name)")
    int insertSqlRow(@Bind("id") String id, @Bind("name") String name);

    @SqlQuery("select count(*) from sample_tx_sql")
    Integer countSqlRows();
}
