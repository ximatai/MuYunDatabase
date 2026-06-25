package net.ximatai.muyun.database.quarkus;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.PageResult;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import net.ximatai.muyun.database.core.orm.Sort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MuYunRepositoryFactoryTest {

    @Test
    void shouldDelegateUnpagedListToEntityManagerList() {
        CapturingEntityManager entityManager = new CapturingEntityManager();
        DemoRole role = new DemoRole();
        role.setId("r-42");
        role.setRoleName("unpaged");
        entityManager.listResult = List.of(role);
        Criteria criteria = Criteria.of().eq("roleName", "unpaged");
        Sort sort = Sort.asc("roleName");

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(fakeOperations(), null, entityManager);
        PureEntityDao dao = factory.create(PureEntityDao.class);

        List<DemoRole> records = dao.list(criteria, sort);

        assertEquals(List.of(role), records);
        assertSame(DemoRole.class, entityManager.listEntityClass);
        assertSame(criteria, entityManager.listCriteria);
        assertEquals(List.of(sort), List.of(entityManager.listSorts));
        assertEquals(0, entityManager.queryCalls);
    }

    @SuppressWarnings("unchecked")
    private static IDatabaseOperations<Object> fakeOperations() {
        return (IDatabaseOperations<Object>) Proxy.newProxyInstance(
                IDatabaseOperations.class.getClassLoader(),
                new Class<?>[]{IDatabaseOperations.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeOperations";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "getPKName" -> "id";
                    default -> null;
                }
        );
    }

    @MuYunRepository
    interface PureEntityDao extends EntityDao<DemoRole, String> {
    }

    @Table(name = "sample_role", schema = "sample_schema")
    static class DemoRole {
        @Id
        @Column(length = 32)
        private String id;

        @Column(length = 64)
        private String roleName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }

    private static class CapturingEntityManager implements SimpleEntityManager {
        private List<DemoRole> listResult = List.of();
        private Class<?> listEntityClass;
        private Criteria listCriteria;
        private Sort[] listSorts;
        private int queryCalls;

        @Override
        public <T> boolean ensureTable(Class<T> entityClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, ID> ID insert(T entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> int update(T entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> int update(T entity, Map<String, Object> conditions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> int upsert(T entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, ID> T findById(Class<T> entityClass, ID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, ID> int deleteById(Class<T> entityClass, ID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, ID> int deleteById(Class<T> entityClass, ID id, Map<String, Object> conditions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> findAll(Class<T> entityClass, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> query(Class<T> entityClass, Criteria criteria, PageRequest pageRequest) {
            queryCalls++;
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> query(Class<T> entityClass, Criteria criteria, PageRequest pageRequest, Sort... sorts) {
            queryCalls++;
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> list(Class<T> entityClass, Criteria criteria, Sort... sorts) {
            listEntityClass = entityClass;
            listCriteria = criteria;
            listSorts = sorts;
            return (List<T>) listResult;
        }

        @Override
        public <T> PageResult<T> pageQuery(Class<T> entityClass, Criteria criteria, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> long count(Class<T> entityClass, Criteria criteria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, ID> boolean exists(Class<T> entityClass, ID id) {
            throw new UnsupportedOperationException();
        }
    }
}
