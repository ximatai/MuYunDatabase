package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.builder.sql.SchemaBuildRules;

final class SqlIdentifiers {

    private SqlIdentifiers() {
    }

    static String quote(String identifier, DBInfo.Type dbType) {
        if (!isSafe(identifier)) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Invalid SQL identifier: " + identifier);
        }
        return SchemaBuildRules.quoteIdentifier(identifier, dbType);
    }

    static String qualified(String schema, String table, DBInfo.Type dbType) {
        return quote(schema, dbType) + "." + quote(table, dbType);
    }

    static boolean isSafe(String identifier) {
        return SchemaBuildRules.isValidIdentifier(identifier);
    }
}
