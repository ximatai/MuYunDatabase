package net.ximatai.muyun.database.spring.boot.txprobe;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Insert;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Param;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Select;

@MuYunRepository
public interface TxProbeRepository extends EntityDao<TxProbeOrmEntity, String> {

    @Insert("insert into tx_probe_sql(id, v_name) values(#{id}, #{name})")
    int insertSql(@Param("id") String id, @Param("name") String name);

    @Select("select count(*) as c from tx_probe_sql")
    Integer countSqlRows();
}
