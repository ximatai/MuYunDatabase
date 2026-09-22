package net.ximatai.muyun.database.core;

import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBForeignKey;
import net.ximatai.muyun.database.core.metadata.DBPrimaryKey;
import net.ximatai.muyun.database.core.metadata.DBUniqueConstraint;

import java.util.List;
import java.util.Map;

public interface IMetaDataLoader {

    /**
     * Loads database metadata used for schema diffing and runtime table access.
     * Implementations should populate table descriptions and column descriptions
     * when the database exposes comments, because migration planning uses them
     * to avoid repeated comment DDL.
     */
    DBInfo getDBInfo();

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
