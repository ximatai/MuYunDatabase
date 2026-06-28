package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void shouldCompileEqNullableAsIsNullWhenValueIsNull() {
        CompiledCriteria compiled = compiler.compile(
                Criteria.of().eqNullable("tenant", null),
                this::resolveColumn,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("\"tenant_id\" IS NULL", compiled.getSql());
        assertEquals(Map.of(), compiled.getParams());
    }

    @Test
    void shouldCompileEqNullableAsEqWhenValueIsPresent() {
        CompiledCriteria compiled = compiler.compile(
                Criteria.of().eqNullable("tenant", "t1"),
                this::resolveColumn,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("\"tenant_id\" = :p0", compiled.getSql());
        assertEquals(Map.of("p0", "t1"), compiled.getParams());
    }

    @Test
    void shouldBindEnumNameByDefault() {
        CompiledCriteria compiled = compiler.compile(
                Criteria.of()
                        .eq("status", TestStatus.ENABLED)
                        .in("code", List.of(TestStatus.DISABLED, "custom")),
                this::resolveColumn,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("\"status\" = :p0 AND \"v_code\" IN (:p1_0, :p1_1)", compiled.getSql());
        assertEquals(Map.of("p0", "ENABLED", "p1_0", "DISABLED", "p1_1", "custom"), compiled.getParams());
    }

    @Test
    void shouldUseInjectedValueConverter() {
        CriteriaSqlCompiler customCompiler = new CriteriaSqlCompiler(new TestStatusCodeConverter());

        CompiledCriteria compiled = customCompiler.compile(
                Criteria.of()
                        .eq("status", TestStatus.ENABLED)
                        .raw(SqlRawCondition.of("status <> :status", Map.of("status", TestStatus.DISABLED))),
                this::resolveColumn,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("\"status\" = :p0 AND status <> :sq1", compiled.getSql());
        assertEquals(Map.of("p0", "enabled", "sq1", "disabled"), compiled.getParams());
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

    @Test
    void shouldUseFieldCodecWhenCompilingWithEntityMeta() throws NoSuchFieldException {
        EntityFieldMeta id = fieldMeta("id", "id", ColumnType.VARCHAR, true);
        EntityFieldMeta statuses = fieldMeta("statuses", "statuses", ColumnType.SET, false);
        EntityMeta meta = new EntityMeta(
                StaticEntity.class,
                "test_entity",
                null,
                null,
                List.of(id, statuses),
                id
        );
        CriteriaSqlCompiler customCompiler = new CriteriaSqlCompiler(new TestStatusCodeConverter());

        CompiledCriteria compiled = customCompiler.compile(
                Criteria.of().eq("statuses", new LinkedHashSet<>(List.of(TestStatus.ENABLED, TestStatus.DISABLED))),
                meta,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("\"statuses\" = :p0", compiled.getSql());
        assertEquals(Map.of("p0", "enabled,disabled"), compiled.getParams());
    }

    @Test
    void copyOfShouldSnapshotCriteria() {
        Criteria source = Criteria.of().eq("code", "A001");
        Criteria copy = Criteria.copyOf(source);

        source.eq("status", "disabled");

        CompiledCriteria compiled = compiler.compile(copy, this::resolveColumn, DBInfo.Type.POSTGRESQL);
        assertEquals("(\"v_code\" = :p0)", compiled.getSql());
        assertEquals(Map.of("p0", "A001"), compiled.getParams());
    }

    @Test
    void copyOfEmptyCriteriaShouldRemainEmpty() {
        Criteria copy = Criteria.copyOf(Criteria.of());

        assertEquals(true, copy.isEmpty());
        CompiledCriteria compiled = compiler.compile(copy, this::resolveColumn, DBInfo.Type.POSTGRESQL);
        assertEquals("", compiled.getSql());
        assertEquals(Map.of(), compiled.getParams());
    }

    @Test
    void andShouldCombineCriteriaWithoutSharingMutableSource() {
        Criteria source = Criteria.of().eq("code", "A001");
        Criteria combined = Criteria.of().eq("tenant", "t1").and(source);

        source.eq("status", "disabled");

        CompiledCriteria compiled = compiler.compile(combined, this::resolveColumn, DBInfo.Type.POSTGRESQL);
        assertEquals("\"tenant_id\" = :p0 AND (\"v_code\" = :p1)", compiled.getSql());
        assertEquals(Map.of("p0", "t1", "p1", "A001"), compiled.getParams());
    }

    @Test
    void orShouldCombineCriteriaWithoutSharingMutableSource() {
        Criteria source = Criteria.of().eq("code", "A001");
        Criteria combined = Criteria.of().eq("tenant", "t1").or(source);

        source.eq("status", "disabled");

        CompiledCriteria compiled = compiler.compile(combined, this::resolveColumn, DBInfo.Type.POSTGRESQL);
        assertEquals("\"tenant_id\" = :p0 OR (\"v_code\" = :p1)", compiled.getSql());
        assertEquals(Map.of("p0", "t1", "p1", "A001"), compiled.getParams());
    }

    private String resolveColumn(String field) {
        return switch (field) {
            case "code" -> "v_code";
            case "tenant" -> "tenant_id";
            case "status" -> "status";
            default -> null;
        };
    }

    private EntityFieldMeta fieldMeta(String fieldName, String columnName, boolean id) throws NoSuchFieldException {
        return fieldMeta(fieldName, columnName, ColumnType.VARCHAR, id);
    }

    private EntityFieldMeta fieldMeta(String fieldName,
                                      String columnName,
                                      ColumnType columnType,
                                      boolean id) throws NoSuchFieldException {
        Field field = StaticEntity.class.getDeclaredField(fieldName);
        return new EntityFieldMeta(field, columnName, columnType, id);
    }

    private static class StaticEntity {
        private String id;
        private String code;
        private Set<TestStatus> statuses;
    }

    private enum TestStatus {
        ENABLED("enabled"),
        DISABLED("disabled");

        private final String code;

        TestStatus(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private static class TestStatusCodeConverter implements DatabaseValueConverter {
        @Override
        public Object toDatabaseValue(Object value) {
            if (value instanceof TestStatus status) {
                return status.getCode();
            }
            return DatabaseValueConverter.DEFAULT.toDatabaseValue(value);
        }

        @Override
        public Object fromDatabaseValue(Object value, Class<?> targetType) {
            return DatabaseValueConverter.DEFAULT.fromDatabaseValue(value, targetType);
        }
    }
}
