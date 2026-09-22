package net.ximatai.muyun.database.core.builder;

import java.util.List;
import java.util.Objects;

public class Index {
    private String name;
    private final List<IndexColumn> indexColumns;
    private final boolean unique;
    private String predicate;

    public Index(String columnName, boolean unique) {
        this(List.of(IndexColumn.asc(columnName)), unique, null);
    }

    public Index(List<String> columns, boolean unique) {
        this(columns.stream().map(IndexColumn::asc).toList(), unique, null);
    }

    public Index(List<IndexColumn> columns, boolean unique, String predicate) {
        this.indexColumns = List.copyOf(columns);
        if (this.indexColumns.isEmpty()) {
            throw new IllegalArgumentException("index columns must not be empty");
        }
        this.unique = unique;
        this.predicate = normalizePredicate(predicate);
    }

    public static Index of(List<IndexColumn> columns, boolean unique) {
        return new Index(columns, unique, null);
    }

    public boolean isUnique() {
        return unique;
    }

    public List<String> getColumns() {
        return indexColumns.stream().map(IndexColumn::name).toList();
    }

    public List<IndexColumn> getIndexColumns() {
        return indexColumns;
    }

    public String getPredicate() {
        return predicate;
    }

    public Index predicate(String predicate) {
        this.predicate = normalizePredicate(predicate);
        return this;
    }

    public Index named(String name) {
        setName(name);
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "index name must not be null");
    }

    private static String normalizePredicate(String predicate) {
        return predicate == null || predicate.isBlank() ? null : predicate.trim();
    }
}
