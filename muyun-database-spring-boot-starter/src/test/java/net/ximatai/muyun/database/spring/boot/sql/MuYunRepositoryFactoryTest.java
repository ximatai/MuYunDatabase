package net.ximatai.muyun.database.spring.boot.sql;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.orm.Criteria;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.core.orm.PageResult;
import net.ximatai.muyun.database.core.orm.PageRequest;
import net.ximatai.muyun.database.core.orm.Sort;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Delete;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Insert;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Param;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Select;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Update;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MuYunRepositoryFactoryTest {

    @Test
    void shouldRenderSchemaPlaceholderAndMapEntityForSelect() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("id", "r-1", "roleName", "admin"));

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        DemoRole role = dao.findById("r-1");

        assertNotNull(role);
        assertEquals("r-1", role.getId());
        assertEquals("admin", role.getRoleName());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operations, times(1)).row(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals("select id, roleName from sample_schema.sample_role where id = :__p0", sqlCaptor.getValue());
        assertEquals("r-1", paramCaptor.getValue().get("__p0"));
    }

    @Test
    void shouldBindNamedParametersForUpdate() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.update(anyString(), anyMap())).thenReturn(1);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        int affected = dao.rename("r-1", "manager");
        assertEquals(1, affected);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operations, times(1)).update(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals(
                "update sample_schema.sample_role set roleName = :__p0 where id = :__p1",
                sqlCaptor.getValue()
        );
        List<Object> values = List.copyOf(paramCaptor.getValue().values());
        assertEquals("manager", values.get(0));
        assertEquals("r-1", values.get(1));
    }

    @Test
    void shouldSupportInsertAndDeleteAnnotations() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.update(anyString(), anyMap())).thenReturn(1);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        int inserted = dao.create("r-2", "viewer");
        int deleted = dao.remove("r-2");

        assertEquals(1, inserted);
        assertEquals(1, deleted);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(operations, times(2)).update(sqlCaptor.capture(), anyMap());
        assertEquals("insert into sample_schema.sample_role (id, roleName) values (:__p0, :__p1)", sqlCaptor.getAllValues().get(0));
        assertEquals("delete from sample_schema.sample_role where id = :__p0", sqlCaptor.getAllValues().get(1));
    }

    @Test
    void shouldReturnNullForScalarWhenFirstColumnIsNull() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("maxOrder", null);
        when(operations.row(anyString(), anyMap())).thenReturn(row);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        Long maxOrder = dao.maxOrder();
        assertNull(maxOrder);
    }

    @Test
    void shouldExpandArrayParameterForInClause() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.query(anyString(), anyMap())).thenReturn(List.of());

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        dao.findNamesByIds(new String[]{"r-1", "r-2"});

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramCaptor = ArgumentCaptor.forClass(Map.class);
        verify(operations, times(1)).query(sqlCaptor.capture(), paramCaptor.capture());
        assertEquals(
                "select roleName from sample_schema.sample_role where id in (:__p0, :__p1)",
                sqlCaptor.getValue()
        );
        List<Object> values = List.copyOf(paramCaptor.getValue().values());
        assertEquals("r-1", values.get(0));
        assertEquals("r-2", values.get(1));
    }

    @Test
    void shouldFailFastOnEmptyArrayParameter() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dao.findNamesByIds(new String[0])
        );
        assertEquals("Empty collection/array is not allowed for token #{ids}", ex.getMessage());
    }

    @Test
    void shouldConvertNumericStringToIntegerScalar() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("cnt", "7"));

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        Integer count = dao.countByRoleName("admin");
        assertEquals(7, count);
    }

    @Test
    void shouldConvertLargeNumericStringToLongScalarWithoutPrecisionLoss() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("total", "9223372036854775806"));

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        Long total = dao.totalByRoleName("admin");
        assertEquals(9223372036854775806L, total);
    }

    @Test
    void shouldConvertNumericStringToBooleanScalar() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("enabled", "1"));

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        Boolean enabled = dao.enabledById("r-1");
        assertEquals(Boolean.TRUE, enabled);
    }

    @Test
    void shouldConvertNoTextToBooleanFalse() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("enabled", "no"));

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        Boolean enabled = dao.enabledById("r-1");
        assertEquals(Boolean.FALSE, enabled);
    }

    @Test
    void shouldConvertNumericZeroToBooleanFalse() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("enabled", 0));

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
        DemoDao dao = factory.create(DemoDao.class);

        Boolean enabled = dao.enabledById("r-1");
        assertEquals(Boolean.FALSE, enabled);
    }

    @Test
    void shouldSupportEntityDaoCrudAndCustomSqlMixed() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);
        net.ximatai.muyun.database.core.metadata.DBInfo dbInfo = mock(net.ximatai.muyun.database.core.metadata.DBInfo.class);
        when(dbInfo.getDatabaseType()).thenReturn(net.ximatai.muyun.database.core.metadata.DBInfo.Type.POSTGRESQL);
        when(operations.getDBInfo()).thenReturn(dbInfo);
        when(operations.insertItem(anyString(), anyString(), anyMap())).thenReturn("ignored");
        when(operations.getItem(anyString(), anyString(), any())).thenReturn(Map.of(
                "id", "r-10",
                "roleName", "hybrid-role"
        ));
        when(operations.row(anyString(), anyMap())).thenReturn(Map.of("total_count", 1), Map.of("total_count", 1));
        when(operations.query(anyString(), anyMap())).thenReturn(List.of(Map.of("id", "r-10", "roleName", "hybrid-role")));
        when(operations.update(anyString(), anyMap())).thenReturn(1);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);
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
        verify(operations, times(1)).update(anyString(), anyMap());
    }

    @Test
    void shouldFailFastWhenEntityDaoContainsMethodWithoutSqlAnnotation() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> factory.create(BrokenHybridDao.class)
        );
        assertTrue(ex.getMessage().contains("Non-CRUD method must use @Select/@Insert/@Update/@Delete"));
    }

    @Test
    void shouldFailFastWhenReservedCrudMethodUsesInvalidSignature() {
        @SuppressWarnings("unchecked")
        IDatabaseOperations<Object> operations = (IDatabaseOperations<Object>) mock(IDatabaseOperations.class);

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);

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

        MockEnvironment environment = new MockEnvironment().withProperty("sample.db.schema", "sample_schema");
        MuYunRepositoryFactory factory = new MuYunRepositoryFactory(operations, environment);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> factory.create(SqlAnnotatedCrudMethodDao.class)
        );
        assertTrue(ex.getMessage().contains("CRUD method is reserved by EntityDao"));
    }

    @MuYunRepository
    interface DemoDao {

        @Select("select id, roleName from ${sample.db.schema}.sample_role where id = #{id}")
        DemoRole findById(String id);

        @Update("update ${sample.db.schema}.sample_role set roleName = #{name} where id = #{id}")
        int rename(@Param("id") String id, @Param("name") String name);

        @Insert("insert into ${sample.db.schema}.sample_role (id, roleName) values (#{id}, #{name})")
        int create(@Param("id") String id, @Param("name") String name);

        @Delete("delete from ${sample.db.schema}.sample_role where id = #{id}")
        int remove(@Param("id") String id);

        @Select("select max(nOrder) as maxOrder from ${sample.db.schema}.sample_role")
        Long maxOrder();

        @Select("select roleName from ${sample.db.schema}.sample_role where id in (#{ids})")
        List<String> findNamesByIds(@Param("ids") String[] ids);

        @Select("select count(*) as cnt from ${sample.db.schema}.sample_role where roleName = #{name}")
        Integer countByRoleName(@Param("name") String name);

        @Select("select count(*) as total from ${sample.db.schema}.sample_role where roleName = #{name}")
        Long totalByRoleName(@Param("name") String name);

        @Select("select isEnabled as enabled from ${sample.db.schema}.sample_role where id = #{id}")
        Boolean enabledById(@Param("id") String id);
    }

    @MuYunRepository
    interface HybridDao extends EntityDao<DemoRole, String> {
        @Update("update ${sample.db.schema}.sample_role set roleName = #{name} where id = #{id}")
        int rename(@Param("id") String id, @Param("name") String name);
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
        @Select("select id, roleName from ${sample.db.schema}.sample_role where id = #{id}")
        DemoRole findById(String id);
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
