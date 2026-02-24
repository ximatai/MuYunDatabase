package net.ximatai.muyun.database.spring.boot.txprobe;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@MuYunRepository
public interface TxProbeBeanRepository extends EntityDao<TxProbeBeanEntity, String> {

    @SqlQuery("select id, v_name as name from tx_probe_bean where id = :id")
    TxProbeBeanEntity findBeanByIdViaSql(@Bind("id") String id);
}
