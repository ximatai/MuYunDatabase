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
 */
public class RuntimeTableGateway {
    private final IDatabaseOperations<Object> operations;
    private final String schema;
    private final String tableName;
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
        this.columnResolver = Objects.requireNonNull(columnResolver, "columnResolver must not be null");
        this.columnMapper = columnResolver instanceof RuntimeColumnMapper mapper ? mapper : null;
        this.valueConverter = valueConverter == null ? DatabaseValueConverter.DEFAULT : valueConverter;
        this.criteriaCompiler = new CriteriaSqlCompiler(this.valueConverter);
    }

    public Object insert(Map<String, Object> values) {
        Map<String, Object> columns = toColumnMap(values);
        if (columns.isEmpty()) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, "runtime table insert values must not be empty");
        }
        return operations.insertItem(schema, tableName, columns);
    }

    public List<Map<String, Object>> query(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return toFieldRows(queryColumns(criteria, pageRequest, sorts));
    }

    public List<Map<String, Object>> queryColumns(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        CompiledCriteria compiled = criteriaCompiler.compile(criteria, columnResolver, databaseType());

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
        CompiledCriteria compiled = criteriaCompiler.compile(criteria, columnResolver, databaseType());

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
        CompiledCriteria compiled = criteriaCompiler.compile(criteria, columnResolver, databaseType());

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total_count FROM ").append(qualifiedTable());
        if (!compiled.getSql().isBlank()) {
            sql.append(" WHERE ").append(compiled.getSql());
        }
        Long count = CountValueResolver.resolve(operations.row(sql.toString(), compiled.getParams()));
        return count == null ? 0L : count;
    }

    public int patchWhere(Map<String, Object> patchValues, Map<String, Object> whereValues) {
        return operations.patchUpdateItemWhere(schema, tableName, toColumnMap(patchValues), toColumnMap(whereValues));
    }

    public int deleteWhere(Map<String, Object> whereValues) {
        return operations.deleteItemWhere(schema, tableName, toColumnMap(whereValues));
    }

    private Map<String, Object> toColumnMap(Map<String, Object> values) {
        Map<String, Object> columns = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return columns;
        }
        values.forEach((field, value) -> columns.put(resolveColumn(field), valueConverter.toDatabaseValue(value)));
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
        row.forEach((column, value) -> fields.put(columnMapper.resolveFieldName(column), value));
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

    private String qualifiedTable() {
        return SqlIdentifiers.qualified(schema, tableName, databaseType());
    }

    private DBInfo.Type databaseType() {
        return operations.getDBInfo().getDatabaseType();
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null || !SqlIdentifiers.isSafe(value)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Invalid " + name + ": " + value);
        }
        return value;
    }
}
