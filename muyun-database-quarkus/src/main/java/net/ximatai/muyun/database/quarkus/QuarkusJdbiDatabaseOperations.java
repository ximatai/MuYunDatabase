package net.ximatai.muyun.database.quarkus;

import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import org.jdbi.v3.core.Jdbi;

class QuarkusJdbiDatabaseOperations<K> extends JdbiDatabaseOperations<K> {

    private final String defaultSchema;

    QuarkusJdbiDatabaseOperations(Jdbi jdbi,
                                  JdbiMetaDataLoader metaDataLoader,
                                  Class<K> pkType,
                                  String pkName,
                                  String defaultSchema) {
        super(jdbi, metaDataLoader, pkType, pkName);
        this.defaultSchema = defaultSchema;
    }

    @Override
    public String getDefaultSchemaName() {
        if (defaultSchema != null && !defaultSchema.isBlank()) {
            return defaultSchema;
        }
        return super.getDefaultSchemaName();
    }
}
