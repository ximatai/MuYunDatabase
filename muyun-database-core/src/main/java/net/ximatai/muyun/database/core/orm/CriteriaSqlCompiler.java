package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CriteriaSqlCompiler {

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

    private final EnumMap<CriteriaOperator, ClauseRenderer> renderers = new EnumMap<>(CriteriaOperator.class);

    CriteriaSqlCompiler() {
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
    }

    CompiledCriteria compile(Criteria criteria, EntityMeta meta, DBInfo.Type dbType) {
        ClauseContext context = new ClauseContext(meta, dbType);
        String sql = compileGroup(criteria.getRoot(), context);
        return new CompiledCriteria(sql, context.params);
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
        context.params.put(key, firstValue(clause));
        return resolveColumn(clause, context) + " " + op + " :" + key;
    }

    private String renderBetween(CriteriaClause clause, ClauseContext context) {
        if (clause.getValues().size() != 2) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "BETWEEN requires exactly 2 values");
        }
        String key = "p" + context.nextParamIndex();
        String key1 = key + "_1";
        String key2 = key + "_2";
        context.params.put(key1, clause.getValues().get(0));
        context.params.put(key2, clause.getValues().get(1));
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
            context.params.put(listKey, clause.getValues().get(i));
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
            context.params.put(listKey, clause.getValues().get(i));
        }
        return resolveColumn(clause, context) + " NOT IN (" + String.join(", ", holders) + ")";
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
        String columnName = context.meta.resolveColumnName(clause.getField());
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
        renamed.forEach((original, rewritten) -> context.params.put(rewritten, sourceParams.get(original)));
        return buffer.toString();
    }

    private Object firstValue(CriteriaClause clause) {
        if (clause.getValues().isEmpty()) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, clause.getOperator() + " requires one value");
        }
        return clause.getValues().get(0);
    }

    private static class ClauseContext {
        private final EntityMeta meta;
        private final DBInfo.Type dbType;
        private final Map<String, Object> params = new HashMap<>();
        private int paramIndex;

        private ClauseContext(EntityMeta meta, DBInfo.Type dbType) {
            this.meta = meta;
            this.dbType = dbType;
        }

        private int nextParamIndex() {
            return paramIndex++;
        }
    }

    @FunctionalInterface
    private interface ClauseRenderer {
        String render(CriteriaClause clause, ClauseContext context);
    }
}
