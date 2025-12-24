package net.ximatai.muyun.database.core.metadata;

import net.ximatai.muyun.database.core.exception.TableNotFound;

import java.util.HashMap;
import java.util.Map;

public class DBSchema {

    private String name;
    private Map<String, DBTable> tables = new HashMap<>();

    public DBSchema(String name) {
        this.name = name;
    }

    public void addTable(DBTable table) {
        this.tables.put(table.getName(), table);
    }

    public DBSchema setTables(Map<String, DBTable> tables) {
        this.tables = tables;
        return this;
    }

    public DBTable getTable(String name) {
        if (!containsTable(name)) {
            throw new TableNotFound(name, this.name);
        }
        return this.tables.get(name);
    }

    public DBSchema removeTable(String name) {
        this.tables.remove(name);
        return this;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DBSchema && name.equals(((DBSchema) obj).name);
    }

    public Map<String, DBTable> getTables() {
        return tables;
    }

    public boolean containsTable(String tableName) {
        return getTables().containsKey(tableName);
    }
}
