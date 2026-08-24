package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Single-table Map gateway for runtime-defined records.
 * <p>
 * Use the {@link TableMeta} constructors or factory methods when runtime
 * fields need typed conversion, collection Criteria operators, or logical field
 * names in query results. The legacy {@link CriteriaColumnResolver}
 * constructors are kept for simple field-to-column Criteria resolution.
 */
public class RuntimeTableGateway {
    private final IDatabaseOperations<Object> operations;
    private final String schema;
    private final String tableName;
    private final TableMeta tableMeta;
    private final CriteriaColumnResolver columnResolver;
    private final RuntimeColumnMapper columnMapper;
    private final DatabaseValueConverter valueConverter;
    private final CriteriaSqlCompiler criteriaCompiler;

    @SuppressWarnings("unchecked")
    public RuntimeTableGateway(IDatabaseOperations<?> operations,
                               String schema,
                               String tableName,
                               CriteriaColumnResolver columnResolver) {
        this(operations, schema, tableName, columnResolver, DatabaseValueConverter.DEFAULT);
    }

    @SuppressWarnings("unchecked")
    public RuntimeTableGateway(IDatabaseOperations<?> operations,
                               String schema,
                               String tableName,
                               CriteriaColumnResolver columnResolver,
                               DatabaseValueConverter valueConverter) {
        this.operations = (IDatabaseOperations<Object>) Objects.requireNonNull(operations, "operations must not be null");
        this.schema = schema == null || schema.isBlank() ? operations.getDefaultSchemaName() : requireIdentifier(schema, "schema");
        this.tableName = requireIdentifier(tableName, "tableName");
        this.tableMeta = null;
        this.columnResolver = Objects.requireNonNull(columnResolver, "columnResolver must not be null");
        this.columnMapper = columnResolver instanceof RuntimeColumnMapper mapper ? mapper : null;
        this.valueConverter = valueConverter == null ? DatabaseValueConverter.DEFAULT : valueConverter;
        this.criteriaCompiler = new CriteriaSqlCompiler(this.valueConverter);
    }

    @SuppressWarnings("unchecked")
    public RuntimeTableGateway(IDatabaseOperations<?> operations,
                               TableMeta tableMeta) {
        this(operations, tableMeta, DatabaseValueConverter.DEFAULT);
    }

    @SuppressWarnings("unchecked")
    public RuntimeTableGateway(IDatabaseOperations<?> operations,
                               TableMeta tableMeta,
                               DatabaseValueConverter valueConverter) {
        this.operations = (IDatabaseOperations<Object>) Objects.requireNonNull(operations, "operations must not be null");
        this.tableMeta = Objects.requireNonNull(tableMeta, "tableMeta must not be null");
        this.schema = tableMeta.getSchema() == null || tableMeta.getSchema().isBlank()
                ? operations.getDefaultSchemaName()
                : requireIdentifier(tableMeta.getSchema(), "schema");
        this.tableName = requireIdentifier(tableMeta.getTableName(), "tableName");
        this.columnResolver = tableMeta;
        this.columnMapper = tableMeta;
        this.valueConverter = valueConverter == null ? DatabaseValueConverter.DEFAULT : valueConverter;
        this.criteriaCompiler = new CriteriaSqlCompiler(this.valueConverter);
    }

    public static RuntimeTableGateway of(IDatabaseOperations<?> operations,
                                         TableMeta tableMeta,
                                         DatabaseValueConverter valueConverter) {
        return new RuntimeTableGateway(operations, tableMeta, valueConverter);
    }

    public static RuntimeTableGateway of(IDatabaseOperations<?> operations,
                                         TableMeta tableMeta) {
        return new RuntimeTableGateway(operations, tableMeta);
    }

    public Object insert(Map<String, Object> values) {
        Map<String, Object> columns = toColumnMap(values, OrmException.Code.INVALID_ENTITY);
        if (columns.isEmpty()) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, "runtime table insert values must not be empty");
        }
        return operations.insertItem(schema, tableName, columns, primaryKeyColumnName());
    }

    public List<Map<String, Object>> query(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return toFieldRows(queryColumns(criteria, pageRequest, sorts));
    }

    public List<Map<String, Object>> queryColumns(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        CompiledCriteria compiled = compile(criteria);

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(qualifiedTable());
        if (!compiled.getSql().isBlank()) {
            sql.append(" WHERE ").append(compiled.getSql());
        }
        appendOrderBy(sql, sorts);
        sql.append(" LIMIT :limit OFFSET :offset");

        Map<String, Object> params = new HashMap<>(compiled.getParams());
        params.put("limit", pageRequest.getLimit());
        params.put("offset", pageRequest.getOffset());
        return operations.query(sql.toString(), params);
    }

    public List<Map<String, Object>> list(Criteria criteria, Sort... sorts) {
        return toFieldRows(listColumns(criteria, sorts));
    }

    public List<Map<String, Object>> listColumns(Criteria criteria, Sort... sorts) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        CompiledCriteria compiled = compile(criteria);

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(qualifiedTable());
        if (!compiled.getSql().isBlank()) {
            sql.append(" WHERE ").append(compiled.getSql());
        }
        appendOrderBy(sql, sorts);

        return operations.query(sql.toString(), compiled.getParams());
    }

    public PageResult<Map<String, Object>> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        long total = count(criteria);
        return PageResult.of(query(criteria, pageRequest, sorts), total, pageRequest);
    }

    public PageResult<Map<String, Object>> pageQueryColumns(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        long total = count(criteria);
        return PageResult.of(queryColumns(criteria, pageRequest, sorts), total, pageRequest);
    }

    public long count(Criteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        CompiledCriteria compiled = compile(criteria);

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total_count FROM ").append(qualifiedTable());
        if (!compiled.getSql().isBlank()) {
            sql.append(" WHERE ").append(compiled.getSql());
        }
        Long count = CountValueResolver.resolve(operations.row(sql.toString(), compiled.getParams()));
        return count == null ? 0L : count;
    }

    /**
     * @deprecated Use {@link #aggregateResult(Criteria, AggregateQuery)} to retain the projection contract.
     */
    @Deprecated(since = "3.26.17", forRemoval = false)
    public List<Map<String, Object>> aggregate(Criteria criteria, AggregateQuery aggregateQuery) {
        return aggregateResult(criteria, aggregateQuery).rows().stream().map(AggregateRow::asMap).toList();
    }

    /**
     * Runs a metadata-governed aggregate and returns rows together with their projection definition.
     * This projection-only API deliberately does not accept SQL fragments, HAVING clauses, aggregate ordering, or pagination.
     */
    public AggregateResult aggregateResult(Criteria criteria, AggregateQuery aggregateQuery) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(aggregateQuery, "aggregateQuery must not be null");
        requireAggregateMetadata();
        CompiledCriteria compiled = compile(criteria);
        List<String> groupColumns = aggregateQuery.groupByFields().stream().map(field -> {
            validateAggregateGroupBy(field);
            return resolveColumn(field);
        }).toList();
        List<String> selectParts = new java.util.ArrayList<>();
        for (int i = 0; i < groupColumns.size(); i++) {
            selectParts.add(SqlIdentifiers.quote(groupColumns.get(i), databaseType()) + " AS g" + i);
        }
        for (int i = 0; i < aggregateQuery.selections().size(); i++) {
            AggregateSelection selection = aggregateQuery.selections().get(i);
            AggregateCapabilities.validateSelection(selection, selectionFieldMeta(selection));
            String expression = selection.operation() == AggregateOperation.COUNT ? "COUNT(*)"
                    : selection.operation().name() + "(" + SqlIdentifiers.quote(resolveColumn(selection.field()), databaseType()) + ")";
            selectParts.add(expression + " AS a" + i);
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", selectParts))
                .append(" FROM ").append(qualifiedTable());
        if (!compiled.getSql().isBlank()) sql.append(" WHERE ").append(compiled.getSql());
        if (!groupColumns.isEmpty()) sql.append(" GROUP BY ").append(groupColumns.stream()
                .map(column -> SqlIdentifiers.quote(column, databaseType())).collect(java.util.stream.Collectors.joining(", ")));
        List<AggregateRow> rows = operations.query(sql.toString(), compiled.getParams()).stream()
                .map(row -> new AggregateRow(aggregateRow(row, aggregateQuery))).toList();
        return new AggregateResult(aggregateQuery, rows);
    }

    private Map<String, Object> aggregateRow(Map<String, Object> row, AggregateQuery query) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < query.groupByFields().size(); i++) {
            String field = query.groupByFields().get(i);
            result.put(field, aggregateFieldValue(columnValue(row, "g" + i), field));
        }
        for (int i = 0; i < query.selections().size(); i++) {
            AggregateSelection selection = query.selections().get(i);
            Object value = columnValue(row, "a" + i);
            result.put(selection.key(), aggregateSelectionValue(value, selection));
        }
        return result;
    }

    private Object aggregateFieldValue(Object value, String fieldOrColumn) {
        FieldMeta fieldMeta = resolveFieldMeta(fieldOrColumn);
        if (fieldMeta == null) {
            return value;
        }
        try {
            return FieldValueCodec.fromDatabaseValue(value, fieldMeta, valueConverter);
        } catch (OrmException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, ex.getMessage(), ex);
        }
    }

    private Object aggregateSelectionValue(Object value, AggregateSelection selection) {
        try {
            return AggregateCapabilities.normalizeSelectionValue(value, selection,
                    selectionFieldMeta(selection), valueConverter);
        } catch (OrmException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, ex.getMessage(), ex);
        }
    }

    private void validateAggregateGroupBy(String field) {
        if (tableMeta != null) {
            AggregateCapabilities.validateGroupBy(resolveFieldMeta(field), field);
        }
    }

    private FieldMeta selectionFieldMeta(AggregateSelection selection) {
        return selection.field() == null ? null : resolveFieldMeta(selection.field());
    }

    private void requireAggregateMetadata() {
        if (tableMeta == null) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING,
                    "Runtime table aggregates require a TableMeta-backed RuntimeTableGateway");
        }
    }

    private static Object columnValue(Map<String, Object> row, String alias) {
        if (row.containsKey(alias)) return row.get(alias);
        return row.entrySet().stream().filter(entry -> alias.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    public int patchWhere(Map<String, Object> patchValues, Map<String, Object> whereValues) {
        return operations.patchUpdateItemWhere(
                schema,
                tableName,
                toColumnMap(patchValues, OrmException.Code.INVALID_ENTITY),
                toColumnMap(whereValues, OrmException.Code.INVALID_CRITERIA),
                primaryKeyColumnName()
        );
    }

    public int deleteWhere(Map<String, Object> whereValues) {
        return operations.deleteItemWhere(schema, tableName, toColumnMap(whereValues, OrmException.Code.INVALID_CRITERIA));
    }

    private Map<String, Object> toColumnMap(Map<String, Object> values, OrmException.Code conversionErrorCode) {
        Map<String, Object> columns = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return columns;
        }
        values.forEach((field, value) -> {
            FieldMeta fieldMeta = resolveFieldMeta(field);
            String column = fieldMeta == null ? resolveColumn(field) : fieldMeta.getColumnName();
            Object databaseValue;
            try {
                databaseValue = fieldMeta == null
                        ? valueConverter.toDatabaseValue(value)
                        : FieldValueCodec.toDatabaseValue(fieldMeta, value, valueConverter);
            } catch (IllegalArgumentException ex) {
                throw new OrmException(conversionErrorCode, ex.getMessage(), ex);
            }
            columns.put(column, databaseValue);
        });
        return columns;
    }

    private List<Map<String, Object>> toFieldRows(List<Map<String, Object>> rows) {
        if (columnMapper == null) {
            return rows;
        }
        return rows.stream().map(this::toFieldMap).toList();
    }

    private Map<String, Object> toFieldMap(Map<String, Object> row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        row.forEach((column, value) -> {
            FieldMeta fieldMeta = resolveFieldMetaByColumn(column);
            String fieldName = columnMapper.resolveFieldName(column);
            Object fieldValue;
            try {
                fieldValue = fieldMeta == null ? value : FieldValueCodec.fromDatabaseValue(value, fieldMeta, valueConverter);
            } catch (OrmException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new OrmException(OrmException.Code.INVALID_ENTITY, ex.getMessage(), ex);
            }
            fields.put(fieldName, fieldValue);
        });
        return fields;
    }

    private void appendOrderBy(StringBuilder sql, Sort... sorts) {
        if (sorts == null || sorts.length == 0) {
            return;
        }
        List<String> parts = java.util.Arrays.stream(sorts)
                .filter(Objects::nonNull)
                .map(sort -> SqlIdentifiers.quote(resolveColumn(sort.getField()), databaseType())
                        + " " + sort.getDirection().name())
                .toList();
        if (!parts.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", parts));
        }
    }

    private String resolveColumn(String fieldOrColumn) {
        if (fieldOrColumn == null || fieldOrColumn.isBlank()) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unknown or unsafe field: " + fieldOrColumn);
        }
        String column = columnResolver.resolveColumnName(fieldOrColumn);
        if (column == null || !SqlIdentifiers.isSafe(column)) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unknown or unsafe field: " + fieldOrColumn);
        }
        return column;
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

    private FieldMeta resolveFieldMetaByColumn(String columnName) {
        if (tableMeta == null) {
            return null;
        }
        return tableMeta.findByColumnName(columnName);
    }

    private CompiledCriteria compile(Criteria criteria) {
        if (tableMeta != null) {
            return criteriaCompiler.compile(criteria, tableMeta, databaseType());
        }
        return criteriaCompiler.compile(criteria, columnResolver, databaseType());
    }

    private String qualifiedTable() {
        return SqlIdentifiers.qualified(schema, tableName, databaseType());
    }

    private DBInfo.Type databaseType() {
        return operations.getDBInfo().getDatabaseType();
    }

    private String primaryKeyColumnName() {
        if (tableMeta == null || tableMeta.getIdField() == null) {
            return operations.getPKName();
        }
        return tableMeta.getIdField().getColumnName();
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !SqlIdentifiers.isSafe(value)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Invalid " + name + ": " + value);
        }
        return value;
    }
}
