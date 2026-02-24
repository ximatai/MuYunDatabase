package net.ximatai.muyun.database.core.orm;

import java.util.List;

public interface CrudRepository<T, ID> {

    ID insert(T entity);

    int update(T entity);

    default int update(T entity, NullUpdateStrategy strategy) {
        return update(entity);
    }

    int upsert(T entity);

    int deleteById(ID id);

    T findById(ID id);

    List<T> findAll(PageRequest pageRequest);

    default List<T> findAll(PageRequest pageRequest, Sort... sorts) {
        return findAll(pageRequest);
    }

    PageResult<T> pageFindAll(PageRequest pageRequest, Sort... sorts);

    List<T> query(Criteria criteria, PageRequest pageRequest);

    default List<T> query(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return query(criteria, pageRequest);
    }

    PageResult<T> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts);
}
