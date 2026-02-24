package net.ximatai.muyun.database.samples.starterminimal;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Insert;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Param;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Select;

@MuYunRepository
public interface DemoUserRepository extends EntityDao<DemoOrmEntity, String> {

    @Insert("insert into sample_tx_sql(id, v_name) values(#{id}, #{name})")
    int insertSqlRow(@Param("id") String id, @Param("name") String name);

    @Select("select count(*) as c from sample_tx_sql")
    Integer countSqlRows();
}
