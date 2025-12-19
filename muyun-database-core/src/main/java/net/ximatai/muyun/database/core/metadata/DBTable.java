package net.ximatai.muyun.database.core.metadata;

import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.builder.TableBase;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DBTable extends TableBase {
    private final IMetaDataLoader iMetaDataLoader;

    private Map<String, DBColumn> columnMap;

    private List<DBIndex> indexList;

    public DBTable(IMetaDataLoader iMetaDataLoader) {
        this.iMetaDataLoader = iMetaDataLoader;
    }

    public DBTable setName(String name) {
        this.name = name;
        return this;
    }

    public DBTable setSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public Map<String, DBColumn> getColumnMap() {
        if (columnMap == null) {
            columnMap = iMetaDataLoader.getColumnMap(schema, name);
        }
        return columnMap;
    }

    public List<DBIndex> getIndexList() {
        if (indexList == null) {
            indexList = iMetaDataLoader.getIndexList(schema, name);
        }
        return indexList;
    }

    public boolean contains(String column) {
        if (column == null) {
            return false;
        }
        return getColumn(column) != null;
    }

    public DBColumn getColumn(String column) {
        Objects.requireNonNull(column);
        return getColumnMap().get(column);
    }

    public void resetColumns() {
        this.columnMap = null;
    }

    public void resetIndexes() {
        this.indexList = null;
    }
}
