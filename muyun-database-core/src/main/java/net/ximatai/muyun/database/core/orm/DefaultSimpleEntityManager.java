package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.builder.TableBuilder;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DefaultSimpleEntityManager implements SimpleEntityManager {

    private final IDatabaseOperations<Object> operations;
    private final EntityMetaResolver metaResolver;
    private final UpsertStrategy upsertStrategy;
    private final CriteriaSqlCompiler criteriaCompiler;

    @SuppressWarnings("unchecked")
    public DefaultSimpleEntityManager(IDatabaseOperations<?> operations) {
        this(operations, UpsertStrategy.ATOMIC_PREFERRED);
    }

    @SuppressWarnings("unchecked")
    public DefaultSimpleEntityManager(IDatabaseOperations<?> operations, UpsertStrategy upsertStrategy) {
        this.operations = (IDatabaseOperations<Object>) operations;
        this.metaResolver = new EntityMetaResolver();
        this.upsertStrategy = upsertStrategy == null ? UpsertStrategy.ATOMIC_PREFERRED : upsertStrategy;
        this.criteriaCompiler = new CriteriaSqlCompiler();
    }

    protected EntityMeta resolveMeta(Class<?> entityClass) {
        return metaResolver.resolve(entityClass);
    }

    @Override
    public <T> boolean ensureTable(Class<T> entityClass) {
        return new TableBuilder(operations).build(entityClass);
    }

    @Override
    public <T> MigrationResult ensureTable(Class<T> entityClass, MigrationOptions options) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        MigrationOptions safeOptions = options == null ? MigrationOptions.execute() : options;

        EntityMeta meta = resolveMeta(entityClass);
        SchemaMigrationPlanner.Plan plan = new SchemaMigrationPlanner(operations).plan(meta.getTableWrapper());
        if (!plan.isChanged()) {
            return MigrationResult.empty(safeOptions);
        }

        if (safeOptions.isStrict() && plan.hasNonAdditive()) {
            throw new OrmException(
                    OrmException.Code.STRICT_MIGRATION_REJECTED,
                    "Strict migration rejected non-additive changes for table " + schema(meta) + "." + meta.getTableName()
            );
        }

        if (safeOptions.isDryRun()) {
            return new MigrationResult(true, true, plan.hasNonAdditive(), plan.getStatements());
        }

        boolean changed = new TableBuilder(operations).build(meta.getTableWrapper());
        return new MigrationResult(changed, false, plan.hasNonAdditive(), plan.getStatements());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID> ID insert(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");

        EntityMeta meta = resolveMeta(entity.getClass());
        Object currentId = meta.getIdField().read(entity);

        Map<String, Object> body = EntityMapper.toMap(meta, entity, false, currentId != null);
        Object id = operations.insertItem(schema(meta), meta.getTableName(), body);

        if (currentId == null && id != null) {
            meta.getIdField().write(entity, id);
            return (ID) id;
        }

        return (ID) currentId;
    }

    @Override
    public <T> int update(T entity) {
        return update(entity, NullUpdateStrategy.INCLUDE_NULLS);
    }

    @Override
    public <T> int update(T entity, NullUpdateStrategy strategy) {
        Objects.requireNonNull(entity, "entity must not be null");
        NullUpdateStrategy safeStrategy = strategy == null ? NullUpdateStrategy.INCLUDE_NULLS : strategy;

        EntityMeta meta = resolveMeta(entity.getClass());
        Object id = meta.getIdField().read(entity);
        if (id == null) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, "entity id must not be null");
        }

        boolean includeNull = safeStrategy == NullUpdateStrategy.INCLUDE_NULLS;
        Map<String, Object> body = EntityMapper.toMap(meta, entity, includeNull, false);
        body.put(meta.getIdColumnName(), id);

        return operations.updateItem(schema(meta), meta.getTableName(), body);
    }

    @Override
    public <T> int upsert(T entity) {
        Objects.requireNonNull(entity, "entity must not be null");

        EntityMeta meta = resolveMeta(entity.getClass());
        Object id = meta.getIdField().read(entity);
        if (id == null) {
            throw new OrmException(OrmException.Code.INVALID_ENTITY, "entity id must not be null");
        }

        Map<String, Object> body = EntityMapper.toMap(meta, entity, true, true);
        return executeUpsert(schema(meta), meta.getTableName(), body);
    }

    @Override
    public <T, ID> T findById(Class<T> entityClass, ID id) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");

        EntityMeta meta = resolveMeta(entityClass);
        Map<String, Object> row = operations.getItem(schema(meta), meta.getTableName(), id);
        return EntityMapper.fromMap(meta, row, entityClass);
    }

    @Override
    public <T, ID> int deleteById(Class<T> entityClass, ID id) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");

        EntityMeta meta = resolveMeta(entityClass);
        return operations.deleteItem(schema(meta), meta.getTableName(), id);
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass, PageRequest pageRequest) {
        return findAll(entityClass, pageRequest, new Sort[0]);
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass, PageRequest pageRequest, Sort... sorts) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");

        EntityMeta meta = resolveMeta(entityClass);
        String schemaDotTable = SqlIdentifiers.qualified(schema(meta), meta.getTableName(), databaseType());

        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(schemaDotTable);
        appendOrderBy(sql, meta, sorts);
        sql.append(" LIMIT :limit OFFSET :offset");

        Map<String, Object> params = new HashMap<>();
        params.put("limit", pageRequest.getLimit());
        params.put("offset", pageRequest.getOffset());

        List<Map<String, Object>> rows = operations.query(sql.toString(), params);
        return rows.stream()
                .map(row -> EntityMapper.fromMap(meta, row, entityClass))
                .collect(Collectors.toList());
    }

    @Override
    public <T> PageResult<T> pageFindAll(Class<T> entityClass, PageRequest pageRequest) {
        return pageFindAll(entityClass, pageRequest, new Sort[0]);
    }

    @Override
    public <T> PageResult<T> pageFindAll(Class<T> entityClass, PageRequest pageRequest, Sort... sorts) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");

        EntityMeta meta = resolveMeta(entityClass);
        long total = countTotal(meta, null, Map.of());
        List<T> records = findAll(entityClass, pageRequest, sorts);
        return PageResult.of(records, total, pageRequest);
    }

    @Override
    public <T> List<T> query(Class<T> entityClass, Criteria criteria, PageRequest pageRequest) {
        return query(entityClass, criteria, pageRequest, new Sort[0]);
    }

    @Override
    public <T> List<T> query(Class<T> entityClass, Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");

        EntityMeta meta = resolveMeta(entityClass);
        CompiledCriteria compiled = criteriaCompiler.compile(criteria, meta, databaseType());

        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(SqlIdentifiers.qualified(schema(meta), meta.getTableName(), databaseType()));
        if (!compiled.getSql().isEmpty()) {
            sql.append(" WHERE ").append(compiled.getSql());
        }

        appendOrderBy(sql, meta, sorts);
        sql.append(" LIMIT :limit OFFSET :offset");

        Map<String, Object> params = new HashMap<>(compiled.getParams());
        params.put("limit", pageRequest.getLimit());
        params.put("offset", pageRequest.getOffset());

        List<Map<String, Object>> rows = operations.query(sql.toString(), params);
        return rows.stream()
                .map(row -> EntityMapper.fromMap(meta, row, entityClass))
                .collect(Collectors.toList());
    }

    @Override
    public <T> PageResult<T> pageQuery(Class<T> entityClass, Criteria criteria, PageRequest pageRequest) {
        return pageQuery(entityClass, criteria, pageRequest, new Sort[0]);
    }

    @Override
    public <T> PageResult<T> pageQuery(Class<T> entityClass, Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");

        EntityMeta meta = resolveMeta(entityClass);
        CompiledCriteria compiled = criteriaCompiler.compile(criteria, meta, databaseType());
        long total = countTotal(meta, compiled.getSql(), compiled.getParams());
        List<T> records = query(entityClass, criteria, pageRequest, sorts);
        return PageResult.of(records, total, pageRequest);
    }

    private String schema(EntityMeta meta) {
        if (meta.getSchema() != null && !meta.getSchema().isBlank()) {
            return meta.getSchema();
        }
        return operations.getDefaultSchemaName();
    }

    private long countTotal(EntityMeta meta, String whereSql, Map<String, Object> params) {
        String schemaDotTable = SqlIdentifiers.qualified(schema(meta), meta.getTableName(), databaseType());
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total_count FROM ").append(schemaDotTable);
        if (whereSql != null && !whereSql.isBlank()) {
            sql.append(" WHERE ").append(whereSql);
        }
        Map<String, Object> row = operations.row(sql.toString(), params == null ? Map.of() : params);
        Long count = CountValueResolver.resolve(row);
        return count == null ? 0L : count;
    }

    private void appendOrderBy(StringBuilder sql, EntityMeta meta, Sort... sorts) {
        if (sorts == null || sorts.length == 0) {
            return;
        }

        List<String> parts = new ArrayList<>();
        for (Sort sort : sorts) {
            if (sort == null) {
                continue;
            }
            String columnName = meta.resolveColumnName(sort.getField());
            if (columnName == null || !SqlIdentifiers.isSafe(columnName)) {
                throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Unknown or unsafe sort field: " + sort.getField());
            }
            parts.add(SqlIdentifiers.quote(columnName, databaseType()) + " " + sort.getDirection().name());
        }

        if (!parts.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", parts));
        }
    }

    private DBInfo.Type databaseType() {
        return operations.getDBInfo().getDatabaseType();
    }

    private int executeUpsert(String schema, String tableName, Map<String, Object> body) {
        if (upsertStrategy == UpsertStrategy.LEGACY_ONLY) {
            return operations.upsertItem(schema, tableName, body);
        }

        if (!operations.supportsAtomicUpsert()) {
            if (upsertStrategy == UpsertStrategy.ATOMIC_ONLY) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "Atomic upsert is not supported by current database operations");
            }
            return operations.upsertItem(schema, tableName, body);
        }

        try {
            return operations.atomicUpsertItem(schema, tableName, body);
        } catch (RuntimeException ex) {
            if (upsertStrategy == UpsertStrategy.ATOMIC_ONLY) {
                throw ex;
            }
            return operations.upsertItem(schema, tableName, body);
        }
    }
}
