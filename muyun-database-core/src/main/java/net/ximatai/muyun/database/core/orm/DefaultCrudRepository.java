package net.ximatai.muyun.database.core.orm;

import java.util.List;
import java.util.Objects;

public class DefaultCrudRepository<T, ID> implements CrudRepository<T, ID> {

    private final Class<T> entityClass;
    private final SimpleEntityManager entityManager;

    public DefaultCrudRepository(Class<T> entityClass, SimpleEntityManager entityManager) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    @Override
    public ID insert(T entity) {
        return entityManager.insert(entity);
    }

    @Override
    public int update(T entity) {
        return entityManager.update(entity);
    }

    @Override
    public int update(T entity, NullUpdateStrategy strategy) {
        return entityManager.update(entity, strategy);
    }

    @Override
    public int upsert(T entity) {
        return entityManager.upsert(entity);
    }

    @Override
    public int deleteById(ID id) {
        return entityManager.deleteById(entityClass, id);
    }

    @Override
    public T findById(ID id) {
        return entityManager.findById(entityClass, id);
    }

    @Override
    public List<T> findAll(PageRequest pageRequest) {
        return entityManager.findAll(entityClass, pageRequest);
    }

    @Override
    public List<T> findAll(PageRequest pageRequest, Sort... sorts) {
        return entityManager.findAll(entityClass, pageRequest, sorts);
    }

    @Override
    public PageResult<T> pageFindAll(PageRequest pageRequest, Sort... sorts) {
        return entityManager.pageFindAll(entityClass, pageRequest, sorts);
    }

    @Override
    public List<T> query(Criteria criteria, PageRequest pageRequest) {
        return entityManager.query(entityClass, criteria, pageRequest);
    }

    @Override
    public List<T> query(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return entityManager.query(entityClass, criteria, pageRequest, sorts);
    }

    @Override
    public PageResult<T> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return entityManager.pageQuery(entityClass, criteria, pageRequest, sorts);
    }
}
