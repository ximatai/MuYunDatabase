package net.ximatai.muyun.database.core.exception;

public class TableNotFound extends RuntimeException {
    String tableName;
    String schemaName;

    public TableNotFound(String tableName, String schemaName) {
        super("Table " + tableName + " not found in schema " + schemaName);
        this.tableName = tableName;
        this.schemaName = schemaName;
    }
}
