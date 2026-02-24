package net.ximatai.muyun.database.core.orm;

import java.util.List;

public interface SimpleEntityManager {

    <T> boolean ensureTable(Class<T> entityClass);

    default <T> MigrationResult ensureTable(Class<T> entityClass, MigrationOptions options) {
        boolean changed = ensureTable(entityClass);
        return new MigrationResult(changed, options != null && options.isDryRun(), false, List.of());
    }

    <T, ID> ID insert(T entity);

    <T> int update(T entity);

    default <T> int update(T entity, NullUpdateStrategy strategy) {
        return update(entity);
    }

    <T> int upsert(T entity);

    <T, ID> T findById(Class<T> entityClass, ID id);

    <T, ID> int deleteById(Class<T> entityClass, ID id);

    <T> List<T> findAll(Class<T> entityClass, PageRequest pageRequest);

    default <T> List<T> findAll(Class<T> entityClass, PageRequest pageRequest, Sort... sorts) {
        return findAll(entityClass, pageRequest);
    }

    default <T> PageResult<T> pageFindAll(Class<T> entityClass, PageRequest pageRequest) {
        List<T> records = findAll(entityClass, pageRequest);
        return PageResult.unknownTotal(records, pageRequest);
    }

    default <T> PageResult<T> pageFindAll(Class<T> entityClass, PageRequest pageRequest, Sort... sorts) {
        List<T> records = findAll(entityClass, pageRequest, sorts);
        return PageResult.unknownTotal(records, pageRequest);
    }

    <T> List<T> query(Class<T> entityClass, Criteria criteria, PageRequest pageRequest);

    default <T> List<T> query(Class<T> entityClass, Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return query(entityClass, criteria, pageRequest);
    }

    default <T> PageResult<T> pageQuery(Class<T> entityClass, Criteria criteria, PageRequest pageRequest) {
        List<T> records = query(entityClass, criteria, pageRequest);
        return PageResult.unknownTotal(records, pageRequest);
    }

    default <T> PageResult<T> pageQuery(Class<T> entityClass, Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        List<T> records = query(entityClass, criteria, pageRequest, sorts);
        return PageResult.unknownTotal(records, pageRequest);
    }
}
