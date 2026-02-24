package net.ximatai.muyun.database.core.orm;

import java.util.List;

public interface EntityDao<T, ID> {

    boolean ensureTable();

    ID insert(T entity);

    int updateById(T entity);

    int deleteById(ID id);

    boolean existsById(ID id);

    T findById(ID id);

    List<T> query(Criteria criteria, PageRequest pageRequest, Sort... sorts);

    default List<T> list(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return query(criteria, pageRequest, sorts);
    }

    PageResult<T> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts);

    default PageResult<T> page(Criteria criteria, PageRequest pageRequest, Sort... sorts) {
        return pageQuery(criteria, pageRequest, sorts);
    }

    long count(Criteria criteria);

    int upsert(T entity);
}
