package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class QuarkusRepositoryCrudIntegrationTest {

    @Inject
    CrudRepository repository;

    @Test
    void repositorySupportsEntityDaoCrudAndSqlObjectMethods() {
        String id = "crud-1";
        repository.ensureTable();
        repository.deleteById(id);

        CrudEntity entity = new CrudEntity(id, "created", 18);
        assertEquals(id, repository.insert(entity));
        assertTrue(repository.existsById(id));

        CrudEntity created = repository.findById(id);
        assertEquals("created", created.getName());
        assertEquals(18, created.getAge());

        entity.setName("updated");
        entity.setAge(19);
        assertEquals(1, repository.updateById(entity));
        assertEquals("updated", repository.findById(id).getName());

        assertEquals(1, repository.renameBySql(id, "sql-object"));
        assertEquals("sql-object", repository.findNameById(id));

        CrudEntity mappedBySqlObject = repository.findBySqlObject(id);
        assertEquals(id, mappedBySqlObject.getId());
        assertEquals("sql-object", mappedBySqlObject.getName());
        assertEquals(19, mappedBySqlObject.getAge());

        Criteria criteria = Criteria.of().eq("name", "sql-object");
        List<CrudEntity> rows = repository.query(criteria, PageRequest.of(1, 10), Sort.asc("age"));
        assertEquals(1, rows.size());
        assertEquals(id, rows.getFirst().getId());
        assertEquals(1L, repository.count(criteria));

        assertEquals(1, repository.deleteById(id));
        assertFalse(repository.existsById(id));
    }

    @MuYunRepository
    public interface CrudRepository extends EntityDao<CrudEntity, String> {
        @SqlUpdate("update public.quarkus_crud_test set v_name = :name where id = :id")
        int renameBySql(@Bind("id") String id, @Bind("name") String name);

        @SqlQuery("select v_name from public.quarkus_crud_test where id = :id")
        String findNameById(@Bind("id") String id);

        @SqlQuery("select id, v_name as name, i_age as age from public.quarkus_crud_test where id = :id")
        CrudEntity findBySqlObject(@Bind("id") String id);
    }

    @Table(name = "quarkus_crud_test")
    public static class CrudEntity {
        @Id
        @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
        private String id;

        @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
        private String name;

        @Column(name = "i_age", type = ColumnType.INT)
        private Integer age;

        public CrudEntity() {
        }

        CrudEntity(String id, String name, Integer age) {
            this.id = id;
            this.name = name;
            this.age = age;
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

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }
}
