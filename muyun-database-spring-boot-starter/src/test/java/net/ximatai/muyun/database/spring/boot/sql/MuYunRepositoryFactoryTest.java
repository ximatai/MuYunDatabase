package net.ximatai.muyun.database.spring.boot.sql;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.PageResult;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.jdbi.v3.core.mapper.RowMapperFactory;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MuYunRepositoryFactoryTest {

    @Test
    void shouldDelegateJdbiSqlObjectMethods() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        Jdbi jdbi = mock(Jdbi.class);
        JdbiSqlDao extension = mock(JdbiSqlDao.class);

        when(extension.countByRoleName("admin")).thenReturn(3);
        when(extension.rename("r-1", "manager")).thenReturn(1);
        when(jdbi.withExtension(eq(JdbiSqlDao.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ExtensionCallback<Object, JdbiSqlDao, RuntimeException> callback = invocation.getArgument(1);
            return callback.withExtension(extension);
        });

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);
        JdbiSqlDao dao = factory.create(JdbiSqlDao.class);

        assertEquals(3, dao.countByRoleName("admin"));
        assertEquals(1, dao.rename("r-1", "manager"));
        verifyNoInteractions(operations);
        verify(jdbi, times(2)).withExtension(eq(JdbiSqlDao.class), any());
    }

    @Test
    void shouldSupportEntityDaoCrudAndJdbiSqlMixed() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        net.ximatai.muyun.database.core.metadata.DBInfo dbInfo = mock(net.ximatai.muyun.database.core.metadata.DBInfo.class);
        when(dbInfo.getDatabaseType()).thenReturn(net.ximatai.muyun.database.core.metadata.DBInfo.Type.POSTGRESQL);
        when(operations.getDBInfo()).thenReturn(dbInfo);
        when(operations.insertItem(anyString(), anyString(), anyMap())).thenReturn("r-10");
        when(operations.getItem(anyString(), anyString(), any())).thenReturn(Map.of("id", "r-10", "roleName", "hybrid-role"));
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("total_count", 1), Map.of("total_count", 1));
        when(operations.query(anyString(), anyMap())).thenReturn(List.of(Map.of("id", "r-10", "roleName", "hybrid-role")));

        Jdbi jdbi = mock(Jdbi.class);
        Handle handle = mock(Handle.class);
        HybridDao extension = mock(HybridDao.class);
        when(extension.rename("r-10", "hybrid-role-v2")).thenReturn(1);
        when(handle.attach(HybridDao.class)).thenReturn(extension);
        when(jdbi.withHandle(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            HandleCallback<Object, RuntimeException> callback = invocation.getArgument(0);
            return callback.withHandle(handle);
        });

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);
        HybridDao dao = factory.create(HybridDao.class);

        DemoRole entity = new DemoRole();
        entity.setId("r-10");
        entity.setRoleName("hybrid-role");

        String id = dao.insert(entity);
        DemoRole loaded = dao.findById("r-10");
        boolean exists = dao.existsById("r-10");
        List<DemoRole> queried = dao.query(Criteria.of(), PageRequest.of(1, 10));
        PageResult<DemoRole> paged = dao.pageQuery(Criteria.of(), PageRequest.of(1, 10));
        long count = dao.count(Criteria.of());
        int affected = dao.rename("r-10", "hybrid-role-v2");

        assertEquals("r-10", id);
        assertNotNull(loaded);
        assertEquals("r-10", loaded.getId());
        assertEquals("hybrid-role", loaded.getRoleName());
        assertTrue(exists);
        assertEquals(1, queried.size());
        assertEquals(1, paged.getRecords().size());
        assertEquals(1L, count);
        assertEquals(1, affected);

        verify(operations, times(1)).insertItem(anyString(), anyString(), anyMap());
        verify(operations, times(2)).getItem(anyString(), anyString(), any());
        verify(jdbi, times(1)).withHandle(any());
    }

    @Test
    void shouldAutoRegisterEntityBeanMapperForHybridEntityDaoSqlMethods() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        Jdbi jdbi = mock(Jdbi.class);
        Handle handle = mock(Handle.class);
        HybridDao extension = mock(HybridDao.class);

        when(extension.rename("r-20", "name-2")).thenReturn(1);
        when(handle.attach(HybridDao.class)).thenReturn(extension);
        when(jdbi.withHandle(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            HandleCallback<Object, RuntimeException> callback = invocation.getArgument(0);
            return callback.withHandle(handle);
        });

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);
        HybridDao dao = factory.create(HybridDao.class);

        int affected = dao.rename("r-20", "name-2");
        assertEquals(1, affected);

        verify(handle, times(1)).registerRowMapper(any(RowMapperFactory.class));
        verify(handle, times(1)).attach(HybridDao.class);
        verify(jdbi, times(1)).withHandle(any());
    }

    @Test
    void shouldFailFastWhenEntityDaoContainsMethodWithoutSqlAnnotation() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        Jdbi jdbi = mock(Jdbi.class);

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> factory.create(BrokenHybridDao.class)
        );
        assertTrue(ex.getMessage().contains("Non-CRUD method must use Jdbi SQL annotations"));
    }

    @Test
    void shouldFailFastWhenReservedCrudMethodUsesInvalidSignature() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        Jdbi jdbi = mock(Jdbi.class);

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> factory.create(BrokenCrudSignatureDao.class)
        );
        assertTrue(ex.getMessage().contains("Invalid EntityDao reserved method signature"));
        assertTrue(ex.getMessage().contains("list"));
    }

    @Test
    void shouldFailFastWhenReservedCrudMethodDeclaresSqlAnnotation() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        Jdbi jdbi = mock(Jdbi.class);

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> factory.create(SqlAnnotatedCrudMethodDao.class)
        );
        assertTrue(ex.getMessage().contains("CRUD method is reserved by EntityDao"));
    }

    @Test
    void shouldFailFastWhenJdbiBeanIsMissing() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);

        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> factory.create(JdbiSqlDao.class)
        );
        assertTrue(ex.getMessage().contains("require Jdbi bean"));
    }

    @Test
    void shouldNotCallJdbiForPureEntityDao() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        net.ximatai.muyun.database.core.metadata.DBInfo dbInfo = mock(net.ximatai.muyun.database.core.metadata.DBInfo.class);
        when(dbInfo.getDatabaseType()).thenReturn(net.ximatai.muyun.database.core.metadata.DBInfo.Type.POSTGRESQL);
        when(operations.getDBInfo()).thenReturn(dbInfo);
        when(operations.insertItem(anyString(), anyString(), anyMap())).thenReturn("r-20");

        Jdbi jdbi = mock(Jdbi.class);
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, new MockEnvironment(), jdbi);
        PureEntityDao dao = factory.create(PureEntityDao.class);

        DemoRole role = new DemoRole();
        role.setId("r-20");
        role.setRoleName("only-entity");
        assertEquals("r-20", dao.insert(role));
        verify(jdbi, never()).withExtension(any(), any());
    }

    @MuYunRepository
    interface JdbiSqlDao {
        @SqlQuery("select count(*) from sample_role where roleName = :name")
        Integer countByRoleName(@Bind("name") String name);

        @SqlUpdate("update sample_role set roleName = :name where id = :id")
        int rename(@Bind("id") String id, @Bind("name") String name);
    }

    @MuYunRepository
    interface HybridDao extends EntityDao<DemoRole, String> {
        @SqlUpdate("update sample_role set roleName = :name where id = :id")
        int rename(@Bind("id") String id, @Bind("name") String name);
    }

    @MuYunRepository
    interface PureEntityDao extends EntityDao<DemoRole, String> {
    }

    @MuYunRepository
    interface BrokenHybridDao extends EntityDao<DemoRole, String> {
        int broken(String id);
    }

    @MuYunRepository
    interface BrokenCrudSignatureDao extends EntityDao<DemoRole, String> {
        List<DemoRole> list(Criteria criteria, PageRequest pageRequest, Sort sort);
    }

    @MuYunRepository
    interface SqlAnnotatedCrudMethodDao extends EntityDao<DemoRole, String> {
        @SqlQuery("select id, roleName from sample_role where id = :id")
        DemoRole findById(@Bind("id") String id);
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
}
