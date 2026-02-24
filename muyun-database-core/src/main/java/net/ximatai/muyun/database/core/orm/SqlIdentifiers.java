package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.regex.Pattern;

final class SqlIdentifiers {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SqlIdentifiers() {
    }

    static String quote(String identifier, DBInfo.Type dbType) {
        if (!isSafe(identifier)) {
            throw new OrmException(OrmException.Code.INVALID_CRITERIA, "Invalid SQL identifier: " + identifier);
        }
        if (dbType == DBInfo.Type.MYSQL) {
            return "`" + identifier + "`";
        }
        return "\"" + identifier + "\"";
    }

    static String qualified(String schema, String table, DBInfo.Type dbType) {
        return quote(schema, dbType) + "." + quote(table, dbType);
    }

    static boolean isSafe(String identifier) {
        return identifier != null && IDENTIFIER.matcher(identifier).matches();
    }
}
