package net.ximatai.muyun.database.core.metadata;

import java.util.List;

public record DBUniqueConstraint(String name, List<String> columns) {
    public DBUniqueConstraint {
        columns = List.copyOf(columns);
    }
}
