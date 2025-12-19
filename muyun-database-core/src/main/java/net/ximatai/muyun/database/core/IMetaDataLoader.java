package net.ximatai.muyun.database.core;

import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBIndex;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.List;
import java.util.Map;

public interface IMetaDataLoader {

    DBInfo getDBInfo();

    void resetInfo();

    List<DBIndex> getIndexList(String schema, String table);

    Map<String, DBColumn> getColumnMap(String schema, String table);
}
