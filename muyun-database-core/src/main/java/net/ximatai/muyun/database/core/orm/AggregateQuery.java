package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
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

    private static String normalizeGroupByField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("aggregate group-by field must not be blank");
        }
        return field.trim();
    }
}
