package net.ximatai.muyun.database.jdbi.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import org.jdbi.v3.core.Jdbi;

public class JdbiEntityManager extends DefaultSimpleEntityManager {

    public JdbiEntityManager(IDatabaseOperations<?> operations) {
        super(operations);
    }

    public <K> JdbiEntityManager(Jdbi jdbi, JdbiMetaDataLoader loader, Class<K> pkType, String pkName) {
        super(new JdbiDatabaseOperations<>(jdbi, loader, pkType, pkName));
    }
}
