package net.ximatai.muyun.database.spring.boot.txprobe;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@MuYunRepository
public interface TxProbeRepository extends EntityDao<TxProbeOrmEntity, String> {

    @SqlUpdate("insert into tx_probe_sql(id, v_name) values(:id, :name)")
    int insertSql(@Bind("id") String id, @Bind("name") String name);

    @SqlQuery("select count(*) from tx_probe_sql")
    Integer countSqlRows();
}
