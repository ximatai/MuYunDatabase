package net.ximatai.muyun.database.core;

import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;

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
}
