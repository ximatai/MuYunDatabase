package net.ximatai.muyun.database.core;

import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBForeignKey;
import net.ximatai.muyun.database.core.metadata.DBPrimaryKey;
import net.ximatai.muyun.database.core.metadata.DBUniqueConstraint;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface IMetaDataLoader {

    /**
     * Loads database metadata used for schema diffing and runtime table access.
     * Implementations should populate table descriptions and column descriptions
     * when the database exposes comments, because migration planning uses them
     * to avoid repeated comment DDL.
     */
    DBInfo getDBInfo();

    /**
     * Runs discovery and its lazy metadata reads within one invocation scope.
     * Connection-backed implementations should override this to bypass shared
     * caches and keep all reads on the current transaction connection. The
     * metadata and its lazy tables must not escape the callback.
     *
     * The default preserves legacy behavior for third-party loaders; it does
     * not provide connection isolation or fresh discovery guarantees.
     */
    default <R> R withFreshDBInfo(Function<DBInfo, R> callback) {
        return callback.apply(getDBInfo());
    }

    void resetInfo();

    List<DBIndex> getIndexList(String schema, String table);

    Map<String, DBColumn> getColumnMap(String schema, String table);

    default DBPrimaryKey getPrimaryKey(String schema, String table) {
        return null;
    }

    default List<DBUniqueConstraint> getUniqueConstraints(String schema, String table) {
        return List.of();
    }

    default List<DBForeignKey> getForeignKeys(String schema, String table) {
        return List.of();
    }
}
