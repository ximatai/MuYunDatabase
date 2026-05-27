package net.ximatai.muyun.database.core.orm;

import java.util.List;
import java.util.Map;

public interface EntityDao<T, ID> {

    boolean ensureTable();

    ID insert(T entity);

    int updateById(T entity);

    default int updateByIdAndCondition(T entity, Map<String, Object> conditions) {
        throw new UnsupportedOperationException("updateByIdAndCondition is not supported by this EntityDao implementation");
    }

    int deleteById(ID id);

    default int deleteByIdAndCondition(ID id, Map<String, Object> conditions) {
        throw new UnsupportedOperationException("deleteByIdAndCondition is not supported by this EntityDao implementation");
    }

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
