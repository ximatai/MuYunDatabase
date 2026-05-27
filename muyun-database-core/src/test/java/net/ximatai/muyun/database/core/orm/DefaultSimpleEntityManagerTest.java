package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSimpleEntityManagerTest {

    @Test
    void shouldResolveConditionalUpdateFieldsToColumns() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        SampleRole role = new SampleRole();
        role.setId("r-1");
        role.setTenantId("t-1");
        role.setRoleName("manager");

        assertEquals(1, manager.update(role, Map.of("tenantId", "t-1", "role_name", "manager")));

        assertEquals("sample_schema", operations.schema);
        assertEquals("sample_role", operations.table);
        assertEquals(Map.of("tenant_id", "t-1", "role_name", "manager", "id", "r-1"), operations.where);
    }

    @Test
    void shouldResolveConditionalDeleteFieldsToColumns() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        assertEquals(1, manager.deleteById(SampleRole.class, "r-1", Map.of("tenantId", "t-1")));

        assertEquals("sample_schema", operations.schema);
        assertEquals("sample_role", operations.table);
        assertEquals(Map.of("tenant_id", "t-1", "id", "r-1"), operations.where);
    }

    @Test
    void shouldRejectUnknownConditionalField() {
        CapturingOperations operations = new CapturingOperations();
        DefaultSimpleEntityManager manager = new DefaultSimpleEntityManager(operations);

        SampleRole role = new SampleRole();
        role.setId("r-1");

        OrmException ex = assertThrows(OrmException.class, () -> manager.update(role, Map.of("missing", "x")));
        assertEquals(OrmException.Code.INVALID_CRITERIA, ex.getCode());
    }

    @Table(name = "sample_role", schema = "sample_schema")
    static class SampleRole {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tenant_id", length = 32)
        private String tenantId;

        @Column(name = "role_name", length = 64)
        private String roleName;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
    }

    static class CapturingOperations implements IDatabaseOperations<Object> {
        private String schema;
        private String table;
        private Map<String, Object> where;

        @Override
        public IMetaDataLoader getMetaDataLoader() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPKName() {
            return "id";
        }

        @Override
        public int patchUpdateItemWhere(String schema, String tableName, Map<String, Object> patchParams, Map<String, Object> whereParams) {
            this.schema = schema;
            this.table = tableName;
            this.where = Map.copyOf(whereParams);
            return 1;
        }

        @Override
        public int deleteItemWhere(String schema, String tableName, Map<String, Object> whereParams) {
            this.schema = schema;
            this.table = tableName;
            this.where = Map.copyOf(whereParams);
            return 1;
        }

        @Override
        public Object insert(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object insertWithPK(String sql, Map<String, Object> params, Object pk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Object> batchInsert(String sql, List<Map<String, Object>> paramsList) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> row(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> row(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(String sql, Object... params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int execute(String sql, List<Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Array createArray(List<Object> list, String type) {
            throw new UnsupportedOperationException();
        }
    }
}
