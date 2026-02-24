package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;

public final class SimpleOrm {

    private SimpleOrm() {
    }

    public static SimpleEntityManager from(IDatabaseOperations<?> operations) {
        return new DefaultSimpleEntityManager(operations);
    }

    public static SimpleEntityManager from(IDatabaseOperations<?> operations, UpsertStrategy upsertStrategy) {
        return new DefaultSimpleEntityManager(operations, upsertStrategy);
    }

    public static <T, ID> CrudRepository<T, ID> repository(Class<T> entityClass, IDatabaseOperations<?> operations) {
        return new DefaultCrudRepository<>(entityClass, from(operations));
    }

    public static <T, ID> CrudRepository<T, ID> repository(Class<T> entityClass, SimpleEntityManager entityManager) {
        return new DefaultCrudRepository<>(entityClass, entityManager);
    }
}
