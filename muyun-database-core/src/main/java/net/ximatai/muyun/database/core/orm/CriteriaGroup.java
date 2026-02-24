package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CriteriaGroup implements CriteriaNode {

    private final List<Entry> entries = new ArrayList<>();

    public static CriteriaGroup create() {
        return new CriteriaGroup();
    }

    public CriteriaGroup and(String field, CriteriaOperator operator, Object... values) {
        return add(CriteriaJoin.AND, new CriteriaClause(field, operator, Arrays.asList(values)));
    }

    public CriteriaGroup or(String field, CriteriaOperator operator, Object... values) {
        return add(CriteriaJoin.OR, new CriteriaClause(field, operator, Arrays.asList(values)));
    }

    public CriteriaGroup andGroup(CriteriaGroup group) {
        Objects.requireNonNull(group, "group must not be null");
        return add(CriteriaJoin.AND, group);
    }

    public CriteriaGroup orGroup(CriteriaGroup group) {
        Objects.requireNonNull(group, "group must not be null");
        return add(CriteriaJoin.OR, group);
    }

    public CriteriaGroup andGroup(Consumer<CriteriaGroup> consumer) {
        CriteriaGroup group = create();
        consumer.accept(group);
        return andGroup(group);
    }

    public CriteriaGroup orGroup(Consumer<CriteriaGroup> consumer) {
        CriteriaGroup group = create();
        consumer.accept(group);
        return orGroup(group);
    }

    public CriteriaGroup when(boolean condition, Consumer<CriteriaGroup> consumer) {
        if (condition) {
            consumer.accept(this);
        }
        return this;
    }

    public CriteriaGroup when(Supplier<Boolean> conditionSupplier, Consumer<CriteriaGroup> consumer) {
        return when(Boolean.TRUE.equals(conditionSupplier.get()), consumer);
    }

    public CriteriaGroup eq(String field, Object value) {
        return and(field, CriteriaOperator.EQ, value);
    }

    public CriteriaGroup ne(String field, Object value) {
        return and(field, CriteriaOperator.NE, value);
    }

    public CriteriaGroup gt(String field, Object value) {
        return and(field, CriteriaOperator.GT, value);
    }

    public CriteriaGroup gte(String field, Object value) {
        return and(field, CriteriaOperator.GTE, value);
    }

    public CriteriaGroup lt(String field, Object value) {
        return and(field, CriteriaOperator.LT, value);
    }

    public CriteriaGroup lte(String field, Object value) {
        return and(field, CriteriaOperator.LTE, value);
    }

    public CriteriaGroup like(String field, String value) {
        return and(field, CriteriaOperator.LIKE, value);
    }

    public CriteriaGroup in(String field, List<?> values) {
        return and(field, CriteriaOperator.IN, values.toArray());
    }

    public CriteriaGroup notIn(String field, List<?> values) {
        return and(field, CriteriaOperator.NOT_IN, values.toArray());
    }

    public CriteriaGroup inSubQuery(String field, SqlSubQuery subQuery) {
        return and(field, CriteriaOperator.IN_SUBQUERY, subQuery);
    }

    public CriteriaGroup notInSubQuery(String field, SqlSubQuery subQuery) {
        return and(field, CriteriaOperator.NOT_IN_SUBQUERY, subQuery);
    }

    public CriteriaGroup between(String field, Object start, Object end) {
        return and(field, CriteriaOperator.BETWEEN, start, end);
    }

    public CriteriaGroup isNull(String field) {
        return and(field, CriteriaOperator.IS_NULL);
    }

    public CriteriaGroup isNotNull(String field) {
        return and(field, CriteriaOperator.IS_NOT_NULL);
    }

    public CriteriaGroup exists(SqlSubQuery subQuery) {
        return and(null, CriteriaOperator.EXISTS, subQuery);
    }

    public CriteriaGroup notExists(SqlSubQuery subQuery) {
        return and(null, CriteriaOperator.NOT_EXISTS, subQuery);
    }

    public CriteriaGroup raw(SqlRawCondition condition) {
        return and(null, CriteriaOperator.RAW, condition);
    }

    public CriteriaGroup orEq(String field, Object value) {
        return or(field, CriteriaOperator.EQ, value);
    }

    public CriteriaGroup orNe(String field, Object value) {
        return or(field, CriteriaOperator.NE, value);
    }

    public CriteriaGroup orGt(String field, Object value) {
        return or(field, CriteriaOperator.GT, value);
    }

    public CriteriaGroup orGte(String field, Object value) {
        return or(field, CriteriaOperator.GTE, value);
    }

    public CriteriaGroup orLt(String field, Object value) {
        return or(field, CriteriaOperator.LT, value);
    }

    public CriteriaGroup orLte(String field, Object value) {
        return or(field, CriteriaOperator.LTE, value);
    }

    public CriteriaGroup orLike(String field, String value) {
        return or(field, CriteriaOperator.LIKE, value);
    }

    public CriteriaGroup orIn(String field, List<?> values) {
        return or(field, CriteriaOperator.IN, values.toArray());
    }

    public CriteriaGroup orNotIn(String field, List<?> values) {
        return or(field, CriteriaOperator.NOT_IN, values.toArray());
    }

    public CriteriaGroup orInSubQuery(String field, SqlSubQuery subQuery) {
        return or(field, CriteriaOperator.IN_SUBQUERY, subQuery);
    }

    public CriteriaGroup orNotInSubQuery(String field, SqlSubQuery subQuery) {
        return or(field, CriteriaOperator.NOT_IN_SUBQUERY, subQuery);
    }

    public CriteriaGroup orBetween(String field, Object start, Object end) {
        return or(field, CriteriaOperator.BETWEEN, start, end);
    }

    public CriteriaGroup orIsNull(String field) {
        return or(field, CriteriaOperator.IS_NULL);
    }

    public CriteriaGroup orIsNotNull(String field) {
        return or(field, CriteriaOperator.IS_NOT_NULL);
    }

    public CriteriaGroup orExists(SqlSubQuery subQuery) {
        return or(null, CriteriaOperator.EXISTS, subQuery);
    }

    public CriteriaGroup orNotExists(SqlSubQuery subQuery) {
        return or(null, CriteriaOperator.NOT_EXISTS, subQuery);
    }

    public CriteriaGroup orRaw(SqlRawCondition condition) {
        return or(null, CriteriaOperator.RAW, condition);
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private CriteriaGroup add(CriteriaJoin join, CriteriaNode node) {
        entries.add(new Entry(join, node));
        return this;
    }

    public static class Entry {
        private final CriteriaJoin join;
        private final CriteriaNode node;

        public Entry(CriteriaJoin join, CriteriaNode node) {
            this.join = join;
            this.node = node;
        }

        public CriteriaJoin getJoin() {
            return join;
        }

        public CriteriaNode getNode() {
            return node;
        }
    }
}
