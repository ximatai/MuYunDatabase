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
    void shouldCompilePostgresJsonSetContainsWithElementCodec() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("jsonStatuses", ColumnType.JSON_SET);
        CriteriaSqlCompiler customCompiler = new CriteriaSqlCompiler(new TestStatusCodeConverter());

        CompiledCriteria compiled = customCompiler.compile(
                Criteria.of().contains("jsonStatuses", TestStatus.ENABLED),
                meta,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals("jsonb_exists(\"json_statuses\"::jsonb, :p0)", compiled.getSql());
        assertEquals(Map.of("p0", "enabled"), compiled.getParams());
    }

    @Test
    void shouldCompileMysqlJsonSetContainsAnyAndEmpty() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("jsonStatuses", ColumnType.JSON_SET);

        CompiledCriteria compiled = compiler.compile(
                Criteria.of()
                        .containsAny("jsonStatuses", List.of(TestStatus.ENABLED, TestStatus.DISABLED))
                        .orIsEmpty("jsonStatuses"),
                meta,
                DBInfo.Type.MYSQL
        );

        assertEquals(
                "(JSON_CONTAINS(CAST(`json_statuses` AS JSON), JSON_QUOTE(:p0)) OR "
                        + "JSON_CONTAINS(CAST(`json_statuses` AS JSON), JSON_QUOTE(:p1))) "
                        + "OR (`json_statuses` IS NULL OR JSON_LENGTH(CAST(`json_statuses` AS JSON)) = 0)",
                compiled.getSql()
        );
        assertEquals(Map.of("p0", "ENABLED", "p1", "DISABLED"), compiled.getParams());
    }

    @Test
    void shouldCompileCsvSetContainsAllForPostgres() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("statuses", ColumnType.SET);
        CriteriaSqlCompiler customCompiler = new CriteriaSqlCompiler(new TestStatusCodeConverter());

        CompiledCriteria compiled = customCompiler.compile(
                Criteria.of().containsAll("statuses", List.of(TestStatus.ENABLED, TestStatus.DISABLED)),
                meta,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals(
                "(POSITION(',' || :p0 || ',' IN ',' || COALESCE(\"statuses\", '') || ',') > 0 AND "
                        + "POSITION(',' || :p1 || ',' IN ',' || COALESCE(\"statuses\", '') || ',') > 0)",
                compiled.getSql()
        );
        assertEquals(Map.of("p0", "enabled", "p1", "disabled"), compiled.getParams());
    }

    @Test
    void shouldCompileCsvSetContainsForMysql() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("statuses", ColumnType.SET);

        CompiledCriteria compiled = compiler.compile(
                Criteria.of().contains("statuses", TestStatus.ENABLED).isNotEmpty("statuses"),
                meta,
                DBInfo.Type.MYSQL
        );

        assertEquals(
                "FIND_IN_SET(:p0, COALESCE(`statuses`, '')) > 0 AND NOT "
                        + "(`statuses` IS NULL OR `statuses` = '')",
                compiled.getSql()
        );
        assertEquals(Map.of("p0", "ENABLED"), compiled.getParams());
    }

    @Test
    void shouldCompilePostgresArrayCollectionCriteria() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("scores", ColumnType.ARRAY, ColumnType.INT);

        CompiledCriteria compiled = compiler.compile(
                Criteria.of()
                        .contains("scores", 1)
                        .containsAny("scores", List.of(2, 3))
                        .containsAll("scores", List.of(4, 5))
                        .isEmpty("scores"),
                meta,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals(
                "\"scores\" @> ARRAY[:p0]::int[] "
                        + "AND \"scores\" && ARRAY[:p1, :p2]::int[] "
                        + "AND \"scores\" @> ARRAY[:p3, :p4]::int[] "
                        + "AND (\"scores\" IS NULL OR cardinality(\"scores\") = 0)",
                compiled.getSql()
        );
        assertEquals(Map.of("p0", 1, "p1", 2, "p2", 3, "p3", 4, "p4", 5), compiled.getParams());
    }

    @Test
    void shouldRejectArrayCriteriaForMysql() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("scores", ColumnType.ARRAY, ColumnType.INT);

        OrmException exception = assertThrows(
                OrmException.class,
                () -> compiler.compile(Criteria.of().contains("scores", 1), meta, DBInfo.Type.MYSQL)
        );

        assertEquals(OrmException.Code.INVALID_CRITERIA, exception.getCode());
    }

    @Test
    void shouldUseExplicitEmptyListSemanticsForCollectionCriteria() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("statuses", ColumnType.SET);

        CompiledCriteria any = compiler.compile(
                Criteria.of().containsAny("statuses", List.of()),
                meta,
                DBInfo.Type.MYSQL
        );
        CompiledCriteria all = compiler.compile(
                Criteria.of().containsAll("statuses", List.of()),
                meta,
                DBInfo.Type.MYSQL
        );

        assertEquals("1 = 0", any.getSql());
        assertEquals(Map.of(), any.getParams());
        assertEquals("1 = 1", all.getSql());
        assertEquals(Map.of(), all.getParams());
    }

    @Test
    void shouldValidateCollectionFieldBeforeEmptyListSemantics() throws NoSuchFieldException {
        EntityFieldMeta id = fieldMeta("id", "id", ColumnType.VARCHAR, true);
        EntityFieldMeta status = fieldMeta("status", "status", ColumnType.VARCHAR, false);
        EntityMeta meta = new EntityMeta(
                StaticEntity.class,
                "test_entity",
                null,
                null,
                List.of(id, status),
                id
        );

        OrmException exception = assertThrows(
                OrmException.class,
                () -> compiler.compile(Criteria.of().containsAll("status", List.of()), meta, DBInfo.Type.MYSQL)
        );

        assertEquals(OrmException.Code.INVALID_CRITERIA, exception.getCode());
    }

    @Test
    void jsonSetContainsShouldPreserveElementWhitespaceAndEmptyString() throws NoSuchFieldException {
        EntityMeta meta = collectionMeta("jsonStatuses", ColumnType.JSON_SET);

        CompiledCriteria compiled = compiler.compile(
                Criteria.of().containsAny("jsonStatuses", List.of(" spaced ", "")),
                meta,
                DBInfo.Type.POSTGRESQL
        );

        assertEquals(
                "(jsonb_exists(\"json_statuses\"::jsonb, :p0) OR jsonb_exists(\"json_statuses\"::jsonb, :p1))",
                compiled.getSql()
        );
        assertEquals(Map.of("p0", " spaced ", "p1", ""), compiled.getParams());
    }

    @Test
    void shouldRejectCollectionCriteriaWithoutEntityMeta() {
        OrmException exception = assertThrows(
                OrmException.class,
                () -> compiler.compile(
                        Criteria.of().contains("status", "ENABLED"),
                        this::resolveColumn,
                        DBInfo.Type.POSTGRESQL
                )
        );

        assertEquals(OrmException.Code.INVALID_CRITERIA, exception.getCode());
    }

    @Test
    void shouldRejectCollectionCriteriaForNonCollectionColumn() throws NoSuchFieldException {
        EntityFieldMeta id = fieldMeta("id", "id", ColumnType.VARCHAR, true);
        EntityFieldMeta status = fieldMeta("status", "status", ColumnType.VARCHAR, false);
        EntityMeta meta = new EntityMeta(
                StaticEntity.class,
                "test_entity",
                null,
                null,
                List.of(id, status),
                id
        );

        OrmException exception = assertThrows(
                OrmException.class,
                () -> compiler.compile(Criteria.of().contains("status", TestStatus.ENABLED), meta, DBInfo.Type.POSTGRESQL)
        );

        assertEquals(OrmException.Code.INVALID_CRITERIA, exception.getCode());
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
        return fieldMeta(fieldName, columnName, columnType, ColumnType.UNKNOWN, id);
    }

    private EntityFieldMeta fieldMeta(String fieldName,
                                      String columnName,
                                      ColumnType columnType,
                                      ColumnType elementColumnType,
                                      boolean id) throws NoSuchFieldException {
        Field field = StaticEntity.class.getDeclaredField(fieldName);
        return new EntityFieldMeta(field, columnName, columnType, elementColumnType, id);
    }

    private EntityMeta collectionMeta(String fieldName, ColumnType columnType) throws NoSuchFieldException {
        return collectionMeta(fieldName, columnType, ColumnType.UNKNOWN);
    }

    private EntityMeta collectionMeta(String fieldName,
                                      ColumnType columnType,
                                      ColumnType elementColumnType) throws NoSuchFieldException {
        EntityFieldMeta id = fieldMeta("id", "id", ColumnType.VARCHAR, true);
        EntityFieldMeta collection = fieldMeta(fieldName, resolveColumnName(fieldName), columnType, elementColumnType, false);
        return new EntityMeta(
                StaticEntity.class,
                "test_entity",
                null,
                null,
                List.of(id, collection),
                id
        );
    }

    private String resolveColumnName(String fieldName) {
        return switch (fieldName) {
            case "jsonStatuses" -> "json_statuses";
            default -> fieldName;
        };
    }

    private static class StaticEntity {
        private String id;
        private String code;
        private TestStatus status;
        private Set<TestStatus> statuses;
        private Set<TestStatus> jsonStatuses;
        private List<Integer> scores;
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
