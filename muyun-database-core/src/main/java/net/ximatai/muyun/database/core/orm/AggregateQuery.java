package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** A metadata-safe, single-table aggregate projection over a {@link Criteria} result set. */
public record AggregateQuery(List<String> groupByFields, List<AggregateSelection> selections) {
    public AggregateQuery {
        groupByFields = groupByFields == null ? List.of() : new ArrayList<>(groupByFields);
        selections = selections == null ? List.of() : new ArrayList<>(selections);
        if (selections.isEmpty()) throw new IllegalArgumentException("aggregate query requires at least one selection");
        if (selections.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("aggregate selection must not be null");
        }
        groupByFields = groupByFields.stream().map(AggregateQuery::normalizeGroupByField).toList();
        Set<String> groupByKeys = new HashSet<>(groupByFields);
        if (groupByKeys.size() != groupByFields.size()) {
            throw new IllegalArgumentException("duplicate aggregate group-by field");
        }
        if (selections.stream().map(AggregateSelection::key).distinct().count() != selections.size()) {
            throw new IllegalArgumentException("duplicate aggregate selection key");
        }
        if (selections.stream().map(AggregateSelection::key).anyMatch(groupByKeys::contains)) {
            throw new IllegalArgumentException("aggregate selection key must not duplicate a group-by field");
        }
        selections = List.copyOf(selections);
    }
    public static AggregateQuery of(List<AggregateSelection> selections) { return new AggregateQuery(List.of(), selections); }
    public static AggregateQuery groupBy(List<String> fields, List<AggregateSelection> selections) {
        return new AggregateQuery(fields, selections);
    }

    /** Starts a fluent aggregate projection definition. */
    public static Builder builder() {
        return new Builder();
    }

    private static String normalizeGroupByField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("aggregate group-by field must not be blank");
        }
        return field.trim();
    }

    /** Fluent builder for a metadata-safe aggregate projection. */
    public static final class Builder {
        private final List<String> groupByFields = new ArrayList<>();
        private final List<AggregateSelection> selections = new ArrayList<>();

        public Builder groupBy(String... fields) {
            if (fields != null) {
                groupByFields.addAll(Arrays.asList(fields));
            }
            return this;
        }

        public Builder count(String key) {
            selections.add(AggregateSelection.count(key));
            return this;
        }

        public Builder sum(String field, String key) {
            return add(key, AggregateOperation.SUM, field);
        }

        public Builder avg(String field, String key) {
            return add(key, AggregateOperation.AVG, field);
        }

        public Builder min(String field, String key) {
            return add(key, AggregateOperation.MIN, field);
        }

        public Builder max(String field, String key) {
            return add(key, AggregateOperation.MAX, field);
        }

        public AggregateQuery build() {
            return new AggregateQuery(groupByFields, selections);
        }

        private Builder add(String key, AggregateOperation operation, String field) {
            selections.add(AggregateSelection.of(key, operation, field));
            return this;
        }
    }
}
