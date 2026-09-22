package net.ximatai.muyun.database.core.metadata;

import java.util.List;

public record DBPrimaryKey(String name, List<String> columns) {
    public DBPrimaryKey {
        columns = List.copyOf(columns);
    }
}
