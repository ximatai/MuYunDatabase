package net.ximatai.muyun.database.samples.quarkusminimal;

import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@MuYunRepository
public interface DemoUserRepository extends EntityDao<DemoUser, String> {
    @SqlUpdate("update \"PUBLIC\".\"demo_user\" set \"v_name\" = :name where \"id\" = :id")
    int rename(@Bind("id") String id, @Bind("name") String name);

    @SqlQuery("select \"id\", \"v_name\" as name from \"PUBLIC\".\"demo_user\" where \"id\" = :id")
    @RegisterBeanMapper(DemoUser.class)
    DemoUser findViaSql(@Bind("id") String id);
}
