package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CriteriaSqlCompiler {

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

    private final EnumMap<CriteriaOperator, ClauseRenderer> renderers = new EnumMap<>(CriteriaOperator.class);
    private final DatabaseValueConverter valueConverter;

    public CriteriaSqlCompiler() {
        this(DatabaseValueConverter.DEFAULT);
    }

    public CriteriaSqlCompiler(DatabaseValueConverter valueConverter) {
        this.valueConverter = valueConverter == null ? DatabaseValueConverter.DEFAULT : valueConverter;
        renderers.put(CriteriaOperator.EQ, (clause, context) -> compare(clause, context, "="));
        renderers.put(CriteriaOperator.NE, (clause, context) -> compare(clause, context, "<>"));
        renderers.put(CriteriaOperator.GT, (clause, context) -> compare(clause, context, ">"));
        renderers.put(CriteriaOperator.GTE, (clause, context) -> compare(clause, context, ">="));
        renderers.put(CriteriaOperator.LT, (clause, context) -> compare(clause, context, "<"));
        renderers.put(CriteriaOperator.LTE, (clause, context) -> compare(clause, context, "<="));
        renderers.put(CriteriaOperator.LIKE, (clause, context) -> compare(clause, context, "LIKE"));
        renderers.put(CriteriaOperator.IS_NULL, (clause, context) -> resolveColumn(clause, context) + " IS NULL");
        renderers.put(CriteriaOperator.IS_NOT_NULL, (clause, context) -> resolveColumn(clause, context) + " IS NOT NULL");
        renderers.put(CriteriaOperator.BETWEEN, this::renderBetween);
        renderers.put(CriteriaOperator.IN, this::renderIn);
        renderers.put(CriteriaOperator.NOT_IN, this::renderNotIn);
        renderers.put(CriteriaOperator.IN_SUBQUERY, (clause, context) -> renderInSubQuery(clause, context, false));
        renderers.put(CriteriaOperator.NOT_IN_SUBQUERY, (clause, context) -> renderInSubQuery(clause, context, true));
        renderers.put(CriteriaOperator.EXISTS, (clause, context) -> renderExists(clause, context, false));
        renderers.put(CriteriaOperator.NOT_EXISTS, (clause, context) -> renderExists(clause, context, true));
        renderers.put(CriteriaOperator.RAW, this::renderRaw);
        renderers.put(CriteriaOperator.CONTAINS, this::renderContains);
        renderers.put(CriteriaOperator.CONTAINS_ANY,
                (clause, context) -> renderContainsMulti(clause, context, " OR ", "1 = 0"));
        renderers.put(CriteriaOperator.CONTAINS_ALL,
                (clause, context) -> renderContainsMulti(clause, context, " AND ", "1 = 1"));
        renderers.put(CriteriaOperator.IS_EMPTY, (clause, context) -> renderCollectionEmpty(clause, context, false));
        renderers.put(CriteriaOperator.IS_NOT_EMPTY, (clause, context) -> renderCollectionEmpty(clause, context, true));
    }

    /**
     * Compiles criteria into a SQL WHERE fragment and named parameters.
     */
    public CompiledCriteria compile(Criteria criteria, CriteriaColumnResolver columnResolver, DBInfo.Type dbType) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(columnResolver, "columnResolver must not be null");
        Objects.requireNonNull(dbType, "dbType must not be null");

        ClauseContext context = new ClauseContext(columnResolver, dbType, null, valueConverter);
        String sql = compileGroup(criteria.getRoot(), context);
        return new CompiledCriteria(sql, context.params);
    }

    public CompiledCriteria compile(Criteria criteria, TableMeta tableMeta, DBInfo.Type dbType) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(tableMeta, "tableMeta must not be null");
        Objects.requireNonNull(dbType, "dbType must not be null");

        ClauseContext context = new ClauseContext(tableMeta, dbType, tableMeta, valueConverter);
        String sql = compileGroup(criteria.getRoot(), context);
        return new CompiledCriteria(sql, context.params);
    }

    CompiledCriteria compile(Criteria criteria, EntityMeta meta, DBInfo.Type dbType) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(meta, "meta must not be null");
        Objects.requireNonNull(dbType, "dbType must not be null");

        return compile(criteria, meta.asTableMeta(), dbType);
    }

    private String compileGroup(CriteriaGroup group, ClauseContext context) {
        List<String> parts = new ArrayList<>();
        for (CriteriaGroup.Entry entry : group.getEntries()) {
            String expression;
            if (entry.getNode() instanceof CriteriaClause clause) {
                expression = compileClause(clause, context);
            } else if (entry.getNode() instanceof CriteriaGroup nestedGroup) {
                String nested = compileGroup(nestedGroup, context);
                expression = nested.isBlank() ? "" : "(" + nested + ")";
            } else {
                throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unsupported criteria node");
            }

            if (expression.isBlank()) {
                continue;
            }
            if (parts.isEmpty()) {
                parts.add(expression);
            } else {
                parts.add(entry.getJoin().name() + " " + expression);
            }
        }
        return String.join(" ", parts);
    }

    private String compileClause(CriteriaClause clause, ClauseContext context) {
        ClauseRenderer renderer = renderers.get(clause.getOperator());
        if (renderer == null) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unsupported criteria operator: " + clause.getOperator());
        }
        return renderer.render(clause, context);
    }

    private String compare(CriteriaClause clause, ClauseContext context, String op) {
        String key = "p" + context.nextParamIndex();
        context.params.put(key, context.toDatabaseValue(clause, firstValue(clause)));
        return resolveColumn(clause, context) + " " + op + " :" + key;
    }

    private String renderBetween(CriteriaClause clause, ClauseContext context) {
        if (clause.getValues().size() != 2) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "BETWEEN requires exactly 2 values");
        }
        String key = "p" + context.nextParamIndex();
        String key1 = key + "_1";
        String key2 = key + "_2";
        context.params.put(key1, context.toDatabaseValue(clause, clause.getValues().get(0)));
        context.params.put(key2, context.toDatabaseValue(clause, clause.getValues().get(1)));
        return resolveColumn(clause, context) + " BETWEEN :" + key1 + " AND :" + key2;
    }

    private String renderIn(CriteriaClause clause, ClauseContext context) {
        if (clause.getValues().isEmpty()) {
            return "1 = 0";
        }
        String key = "p" + context.nextParamIndex();
        List<String> holders = new ArrayList<>();
        for (int i = 0; i < clause.getValues().size(); i++) {
            String listKey = key + "_" + i;
            holders.add(":" + listKey);
            context.params.put(listKey, context.toDatabaseValue(clause, clause.getValues().get(i)));
        }
        return resolveColumn(clause, context) + " IN (" + String.join(", ", holders) + ")";
    }

    private String renderNotIn(CriteriaClause clause, ClauseContext context) {
        if (clause.getValues().isEmpty()) {
            return "1 = 1";
        }
        String key = "p" + context.nextParamIndex();
        List<String> holders = new ArrayList<>();
        for (int i = 0; i < clause.getValues().size(); i++) {
            String listKey = key + "_" + i;
            holders.add(":" + listKey);
            context.params.put(listKey, context.toDatabaseValue(clause, clause.getValues().get(i)));
        }
        return resolveColumn(clause, context) + " NOT IN (" + String.join(", ", holders) + ")";
    }

    private String renderContains(CriteriaClause clause, ClauseContext context) {
        if (clause.getValues().size() != 1) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "CONTAINS requires exactly one value");
        }
        CollectionField collection = resolveCollectionField(clause, context);
        String key = bindCollectionElement(collection.fieldMeta, clause.getValues().get(0), context);
        return renderCollectionContains(collection, key, context.dbType);
    }

    private String renderContainsMulti(CriteriaClause clause,
                                       ClauseContext context,
                                       String joiner,
                                       String emptyExpression) {
        CollectionField collection = resolveCollectionField(clause, context);
        if (clause.getValues().isEmpty()) {
            return emptyExpression;
        }
        List<String> keys = new ArrayList<>();
        for (Object value : clause.getValues()) {
            keys.add(bindCollectionElement(collection.fieldMeta, value, context));
        }
        if (collection.fieldMeta.getColumnType() == ColumnType.ARRAY) {
            List<String> valueExpressions = keys.stream().map(key -> ":" + key).toList();
            if (" OR ".equals(joiner)) {
                return CriteriaDialectExpressions.collectionContainsAny(
                        context.dbType,
                        collection.fieldMeta.getColumnType(),
                        collection.fieldMeta.getElementColumnType(),
                        collection.columnSql,
                        valueExpressions
                );
            }
            return CriteriaDialectExpressions.collectionContainsAll(
                    context.dbType,
                    collection.fieldMeta.getColumnType(),
                    collection.fieldMeta.getElementColumnType(),
                    collection.columnSql,
                    valueExpressions
            );
        }
        List<String> parts = keys.stream()
                .map(key -> renderCollectionContains(collection, key, context.dbType))
                .toList();
        return "(" + String.join(joiner, parts) + ")";
    }

    private String renderCollectionContains(CollectionField collection, String key, DBInfo.Type dbType) {
        return CriteriaDialectExpressions.collectionContains(
                dbType,
                collection.fieldMeta.getColumnType(),
                collection.fieldMeta.getElementColumnType(),
                collection.columnSql,
                ":" + key
        );
    }

    private String renderCollectionEmpty(CriteriaClause clause, ClauseContext context, boolean not) {
        CollectionField collection = resolveCollectionField(clause, context);
        String expression = CriteriaDialectExpressions.collectionIsEmpty(
                context.dbType,
                collection.fieldMeta.getColumnType(),
                collection.fieldMeta.getElementColumnType(),
                collection.columnSql
        );
        return not ? "NOT " + expression : expression;
    }

    private CollectionField resolveCollectionField(CriteriaClause clause, ClauseContext context) {
        FieldMeta fieldMeta = context.requireFieldMeta(clause.getField());
        if (fieldMeta.getColumnType() != ColumnType.SET
                && fieldMeta.getColumnType() != ColumnType.JSON_SET
                && fieldMeta.getColumnType() != ColumnType.ARRAY) {
            throw new OrmException(
                    OrmException.Code.INVALID_CRITERIA,
                    clause.getOperator() + " requires SET, JSON_SET, or ARRAY field: " + clause.getField()
            );
        }
        if (fieldMeta.getColumnType() == ColumnType.ARRAY && context.dbType != DBInfo.Type.POSTGRESQL) {
            throw new OrmException(
                    OrmException.Code.INVALID_CRITERIA,
                    clause.getOperator() + " for ARRAY fields is only supported on PostgreSQL: " + clause.getField()
            );
        }
        String columnSql = resolveColumn(clause, context);
        return new CollectionField(fieldMeta, columnSql);
    }

    private String bindCollectionElement(FieldMeta fieldMeta, Object value, ClauseContext context) {
        Object encoded;
        try {
            encoded = FieldValueCodec.toCollectionElementDatabaseValue(fieldMeta, value, valueConverter);
        } catch (IllegalArgumentException ex) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, ex.getMessage(), ex);
        }
        if (encoded == null) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Collection criteria value must not be null or blank");
        }
        String key = "p" + context.nextParamIndex();
        context.params.put(key, encoded);
        return key;
    }

    private String renderInSubQuery(CriteriaClause clause, ClauseContext context, boolean notIn) {
        SqlSubQuery subQuery = asSubQuery(clause);
        String rewritten = rewriteNamedParams(subQuery.getSql(), subQuery.getParams(), context);
        String keyword = notIn ? " NOT IN " : " IN ";
        return resolveColumn(clause, context) + keyword + "(" + rewritten + ")";
    }

    private String renderExists(CriteriaClause clause, ClauseContext context, boolean notExists) {
        SqlSubQuery subQuery = asSubQuery(clause);
        String rewritten = rewriteNamedParams(subQuery.getSql(), subQuery.getParams(), context);
        return (notExists ? "NOT EXISTS (" : "EXISTS (") + rewritten + ")";
    }

    private String renderRaw(CriteriaClause clause, ClauseContext context) {
        SqlRawCondition raw = asRawCondition(clause);
        return rewriteNamedParams(raw.getSql(), raw.getParams(), context);
    }

    private String resolveColumn(CriteriaClause clause, ClauseContext context) {
        String columnName = context.columnResolver.resolveColumnName(clause.getField());
        if (columnName == null) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unknown field or column: " + clause.getField());
        }
        if (!SqlIdentifiers.isSafe(columnName)) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unsafe column name: " + columnName);
        }
        return SqlIdentifiers.quote(columnName, context.dbType);
    }

    private SqlSubQuery asSubQuery(CriteriaClause clause) {
        if (clause.getValues().size() != 1 || !(clause.getValues().get(0) instanceof SqlSubQuery subQuery)) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, clause.getOperator() + " requires one SqlSubQuery value");
        }
        return subQuery;
    }

    private SqlRawCondition asRawCondition(CriteriaClause clause) {
        if (clause.getValues().size() != 1 || !(clause.getValues().get(0) instanceof SqlRawCondition raw)) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "RAW requires one SqlRawCondition value");
        }
        return raw;
    }

    private String rewriteNamedParams(String rawSql, Map<String, Object> sourceParams, ClauseContext context) {
        if (rawSql.contains("?")) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "SQL fragment must use named parameters instead of positional '?'");
        }
        Map<String, String> renamed = new LinkedHashMap<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(rawSql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String original = matcher.group(1);
            if (!sourceParams.containsKey(original)) {
                throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Missing sql parameter: " + original);
            }
            String rewritten = renamed.computeIfAbsent(original, key -> "sq" + context.nextParamIndex());
            matcher.appendReplacement(buffer, ":" + rewritten);
        }
        matcher.appendTail(buffer);
        renamed.forEach((original, rewritten) ->
                context.params.put(rewritten, valueConverter.toDatabaseValue(sourceParams.get(original))));
        return buffer.toString();
    }

    private Object firstValue(CriteriaClause clause) {
        if (clause.getValues().isEmpty()) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, clause.getOperator() + " requires one value");
        }
        return clause.getValues().get(0);
    }

    private record CollectionField(FieldMeta fieldMeta, String columnSql) {
    }

    private static class ClauseContext {
        private final CriteriaColumnResolver columnResolver;
        private final DBInfo.Type dbType;
        private final TableMeta tableMeta;
        private final DatabaseValueConverter valueConverter;
        private final Map<String, Object> params = new HashMap<>();
        private int paramIndex;

        private ClauseContext(CriteriaColumnResolver columnResolver,
                              DBInfo.Type dbType,
                              TableMeta tableMeta,
                              DatabaseValueConverter valueConverter) {
            this.columnResolver = columnResolver;
            this.dbType = dbType;
            this.tableMeta = tableMeta;
            this.valueConverter = valueConverter;
        }

        private int nextParamIndex() {
            return paramIndex++;
        }

        private Object toDatabaseValue(CriteriaClause clause, Object value) {
            FieldMeta fieldMeta = resolveFieldMeta(clause.getField());
            try {
                if (fieldMeta == null) {
                    return valueConverter.toDatabaseValue(value);
                }
                return FieldValueCodec.toDatabaseValue(fieldMeta, value, valueConverter);
            } catch (OrmException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new OrmException(OrmException.Code.INVALID_CRITERIA, ex.getMessage(), ex);
            }
        }

        private FieldMeta resolveFieldMeta(String fieldOrColumn) {
            if (tableMeta == null) {
                return null;
            }
            FieldMeta byField = tableMeta.findByFieldName(fieldOrColumn);
            if (byField != null) {
                return byField;
            }
            return tableMeta.findByColumnName(fieldOrColumn);
        }

        private FieldMeta requireFieldMeta(String fieldOrColumn) {
            FieldMeta fieldMeta = resolveFieldMeta(fieldOrColumn);
            if (fieldMeta == null) {
                throw new OrmException(
                        OrmException.Code.INVALID_CRITERIA,
                        "Collection criteria requires field metadata for field: " + fieldOrColumn
                );
            }
            return fieldMeta;
        }
    }

    @FunctionalInterface
    private interface ClauseRenderer {
        String render(CriteriaClause clause, ClauseContext context);
    }
}
