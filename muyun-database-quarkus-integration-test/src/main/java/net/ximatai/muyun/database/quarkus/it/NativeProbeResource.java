package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.jdbi.v3.json.Json;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Path("/muyun/native-probe")
public class NativeProbeResource {

    @Inject
    NativeProbeRepository repository;

    @Inject
    NativeProbeService service;

    @GET
    @Path("/repository")
    public String repositoryCrud() {
        String id = "native-probe-1";
        repository.deleteById(id);

        NativeProbeEntity entity = new NativeProbeEntity(id, "created");
        repository.insert(entity);
        repository.rename(id, "renamed");
        String name = repository.findViaSql(id).getName();
        repository.deleteById(id);
        return "repository:" + name;
    }

    @GET
    @Path("/transaction")
    public String transactionRollback() {
        String id = "native-probe-tx-1";
        repository.deleteById(id);
        try {
            service.insertAndRollback(id);
        } catch (IllegalStateException expected) {
            // expected rollback probe
        }
        return repository.findById(id) == null ? "transaction:rolled-back" : "transaction:leaked";
    }

    @GET
    @Path("/postgres-plugin")
    public String postgresPlugin() {
        String id = "native-probe-json-1";
        repository.createPluginProbeTable();
        repository.deletePluginProbe(id);
        repository.insertJson(id, new NativeProbePayload("jsonb", 7));
        NativeProbePayload payload = repository.findJson(id);
        repository.deletePluginProbe(id);
        return "postgres-plugin:" + payload.name() + ":" + payload.count();
    }

    @MuYunRepository
    public interface NativeProbeRepository extends EntityDao<NativeProbeEntity, String> {
        @SqlUpdate("update public.quarkus_native_probe set v_name = :name where id = :id")
        int rename(@Bind("id") String id, @Bind("name") String name);

        @SqlQuery("select id, v_name as name from public.quarkus_native_probe where id = :id")
        @RegisterBeanMapper(NativeProbeEntity.class)
        NativeProbeEntity findViaSql(@Bind("id") String id);

        @SqlUpdate("create table if not exists public.quarkus_native_plugin_probe (id varchar(64) primary key, payload jsonb not null)")
        void createPluginProbeTable();

        @SqlUpdate("delete from public.quarkus_native_plugin_probe where id = :id")
        int deletePluginProbe(@Bind("id") String id);

        @SqlUpdate("insert into public.quarkus_native_plugin_probe (id, payload) values (:id, :payload)")
        int insertJson(@Bind("id") String id, @Bind("payload") @Json NativeProbePayload payload);

        @SqlQuery("select payload from public.quarkus_native_plugin_probe where id = :id")
        @Json
        NativeProbePayload findJson(@Bind("id") String id);
    }

    @RegisterForReflection
    public record NativeProbePayload(String name, int count) {
    }

    @Table(name = "quarkus_native_probe", schema = "public")
    public static class NativeProbeEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        public NativeProbeEntity() {
        }

        NativeProbeEntity(String id, String name) {
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
