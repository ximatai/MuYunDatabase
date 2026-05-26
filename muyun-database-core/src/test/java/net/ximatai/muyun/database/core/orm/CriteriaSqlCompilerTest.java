package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriteriaSqlCompilerTest {

    private final CriteriaSqlCompiler compiler = new CriteriaSqlCompiler();

    @Test
    void shouldCompileWithPublicColumnResolver() {
        CompiledCriteria compiled = compiler.compile(
                Criteria.of().eq("code", "A001"),
                field -> "code".equals(field) ? "v_code" : null,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("\"v_code\" = :p0", compiled.getSql());
        assertEquals(Map.of("p0", "A001"), compiled.getParams());
    }

    @Test
    void shouldRejectUnknownFieldFromColumnResolver() {
        OrmException exception = assertThrows(
                OrmException.class,
                () -> compiler.compile(Criteria.of().eq("missing", "A001"), field -> null, DBInfo.Type.POSTGRESQL)
        );

        assertEquals(OrmException.Code.INVALID_CRITERIA, exception.getCode());
    }

    @Test
    void shouldKeepRawAndSubQueryCompilationSemantics() {
        Criteria criteria = Criteria.of()
                .raw(SqlRawCondition.of("status = :status", Map.of("status", "ENABLED")))
                .inSubQuery(
                        "code",
                        SqlSubQuery.of("select code from allowed_codes where tenant = :tenant", Map.of("tenant", "t1"))
                );

        CompiledCriteria compiled = compiler.compile(
                criteria,
                field -> "code".equals(field) ? "v_code" : null,
                DBInfo.Type.MYSQL
        );

        assertEquals("status = :sq0 AND `v_code` IN (select code from allowed_codes where tenant = :sq1)", compiled.getSql());
        assertEquals(Map.of("sq0", "ENABLED", "sq1", "t1"), compiled.getParams());
    }

    @Test
    void shouldKeepEntityMetaCompilationBehavior() throws NoSuchFieldException {
        EntityFieldMeta id = fieldMeta("id", "id", true);
        EntityFieldMeta code = fieldMeta("code", "v_code", false);
        EntityMeta meta = new EntityMeta(
                StaticEntity.class,
                "test_entity",
                null,
                null,
                List.of(id, code),
                id
        );

        CompiledCriteria compiled = compiler.compile(Criteria.of().eq("code", "A001"), meta, DBInfo.Type.POSTGRESQL);

        assertEquals("\"v_code\" = :p0", compiled.getSql());
        assertEquals(Map.of("p0", "A001"), compiled.getParams());
    }

    private EntityFieldMeta fieldMeta(String fieldName, String columnName, boolean id) throws NoSuchFieldException {
        Field field = StaticEntity.class.getDeclaredField(fieldName);
        return new EntityFieldMeta(field, columnName, ColumnType.VARCHAR, id);
    }

    private static class StaticEntity {
        private String id;
        private String code;
    }
}
