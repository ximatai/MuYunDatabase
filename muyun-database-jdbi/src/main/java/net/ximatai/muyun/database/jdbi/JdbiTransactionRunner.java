package net.ximatai.muyun.database.jdbi;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class JdbiTransactionRunner<K> {

    private final Jdbi jdbi;
    private final JdbiMetaDataLoader metaDataLoader;
    private final Class<K> pkType;
    private final String pkName;

    public JdbiTransactionRunner(Jdbi jdbi, JdbiMetaDataLoader metaDataLoader, Class<K> pkType, String pkName) {
        this.jdbi = Objects.requireNonNull(jdbi);
        this.metaDataLoader = Objects.requireNonNull(metaDataLoader);
        this.pkType = Objects.requireNonNull(pkType);
        this.pkName = Objects.requireNonNull(pkName);
    }

    public <R> R inTransaction(Function<TxContext<K>, R> callback) {
        Objects.requireNonNull(callback);

        return jdbi.inTransaction(handle -> {
            HandleDatabaseOperations<K> ops = new HandleDatabaseOperations<>(handle, metaDataLoader, pkType, pkName);
            SimpleEntityManager orm = new DefaultSimpleEntityManager(ops);
            TxContext<K> context = new TxContext<>(handle, ops, orm);
            return callback.apply(context);
        });
    }

    public void inTransactionVoid(Consumer<TxContext<K>> callback) {
        Objects.requireNonNull(callback);
        inTransaction(ctx -> {
            callback.accept(ctx);
            return null;
        });
    }

    public static class TxContext<K> {
        private final Handle handle;
        private final IDatabaseOperations<K> operations;
        private final SimpleEntityManager entityManager;

        TxContext(Handle handle, IDatabaseOperations<K> operations, SimpleEntityManager entityManager) {
            this.handle = handle;
            this.operations = operations;
            this.entityManager = entityManager;
        }

        public Handle getHandle() {
            return handle;
        }

        public IDatabaseOperations<K> getOperations() {
            return operations;
        }

        public SimpleEntityManager getEntityManager() {
            return entityManager;
        }

        public <D> D attachDao(Class<D> daoType) {
            return handle.attach(daoType);
        }
    }
}
