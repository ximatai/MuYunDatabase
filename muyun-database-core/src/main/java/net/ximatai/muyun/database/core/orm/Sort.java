package net.ximatai.muyun.database.core.orm;

import java.util.Objects;

public class Sort {
    private final String field;
    private final SortDirection direction;

    public Sort(String field, SortDirection direction) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.direction = direction == null ? SortDirection.ASC : direction;
    }

    public static Sort asc(String field) {
        return new Sort(field, SortDirection.ASC);
    }

    public static Sort desc(String field) {
        return new Sort(field, SortDirection.DESC);
    }

    public String getField() {
        return field;
    }

    public SortDirection getDirection() {
        return direction;
    }
}
