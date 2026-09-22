package net.ximatai.muyun.database.core.orm;

import java.util.Collection;

@FunctionalInterface
public interface MuYunSchemaContributor {
    Collection<ManagedTable> tables();
}
