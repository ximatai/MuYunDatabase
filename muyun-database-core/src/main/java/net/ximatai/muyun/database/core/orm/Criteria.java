package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Criteria {
    private final CriteriaGroup root = CriteriaGroup.create();

    public static Criteria of() {
        return new Criteria();
    }

    public Criteria and(String field, CriteriaOperator operator, Object... values) {
        root.and(field, operator, values);
        return this;
    }

    public Criteria or(String field, CriteriaOperator operator, Object... values) {
        root.or(field, operator, values);
        return this;
    }

    public Criteria andGroup(CriteriaGroup group) {
        root.andGroup(group);
        return this;
    }

    public Criteria orGroup(CriteriaGroup group) {
        root.orGroup(group);
        return this;
    }

    public Criteria andGroup(Consumer<CriteriaGroup> consumer) {
        root.andGroup(consumer);
        return this;
    }

    public Criteria orGroup(Consumer<CriteriaGroup> consumer) {
        root.orGroup(consumer);
        return this;
    }

    public Criteria when(boolean condition, Consumer<Criteria> consumer) {
        if (condition) {
            consumer.accept(this);
        }
        return this;
    }

    public Criteria when(Supplier<Boolean> conditionSupplier, Consumer<Criteria> consumer) {
        return when(Boolean.TRUE.equals(conditionSupplier.get()), consumer);
    }

    public Criteria eq(String field, Object value) {
        return and(field, CriteriaOperator.EQ, value);
    }

    public Criteria ne(String field, Object value) {
        return and(field, CriteriaOperator.NE, value);
    }

    public Criteria orEq(String field, Object value) {
        return or(field, CriteriaOperator.EQ, value);
    }

    public Criteria orNe(String field, Object value) {
        return or(field, CriteriaOperator.NE, value);
    }

    public Criteria gt(String field, Object value) {
        return and(field, CriteriaOperator.GT, value);
    }

    public Criteria gte(String field, Object value) {
        return and(field, CriteriaOperator.GTE, value);
    }

    public Criteria orGt(String field, Object value) {
        return or(field, CriteriaOperator.GT, value);
    }

    public Criteria orGte(String field, Object value) {
        return or(field, CriteriaOperator.GTE, value);
    }

    public Criteria lt(String field, Object value) {
        return and(field, CriteriaOperator.LT, value);
    }

    public Criteria lte(String field, Object value) {
        return and(field, CriteriaOperator.LTE, value);
    }

    public Criteria orLt(String field, Object value) {
        return or(field, CriteriaOperator.LT, value);
    }

    public Criteria orLte(String field, Object value) {
        return or(field, CriteriaOperator.LTE, value);
    }

    public Criteria like(String field, String value) {
        return and(field, CriteriaOperator.LIKE, value);
    }

    public Criteria orLike(String field, String value) {
        return or(field, CriteriaOperator.LIKE, value);
    }

    public Criteria in(String field, List<?> values) {
        return and(field, CriteriaOperator.IN, values.toArray());
    }

    public Criteria notIn(String field, List<?> values) {
        return and(field, CriteriaOperator.NOT_IN, values.toArray());
    }

    public Criteria inSubQuery(String field, SqlSubQuery subQuery) {
        return and(field, CriteriaOperator.IN_SUBQUERY, subQuery);
    }

    public Criteria notInSubQuery(String field, SqlSubQuery subQuery) {
        return and(field, CriteriaOperator.NOT_IN_SUBQUERY, subQuery);
    }

    public Criteria orIn(String field, List<?> values) {
        return or(field, CriteriaOperator.IN, values.toArray());
    }

    public Criteria orNotIn(String field, List<?> values) {
        return or(field, CriteriaOperator.NOT_IN, values.toArray());
    }

    public Criteria orInSubQuery(String field, SqlSubQuery subQuery) {
        return or(field, CriteriaOperator.IN_SUBQUERY, subQuery);
    }

    public Criteria orNotInSubQuery(String field, SqlSubQuery subQuery) {
        return or(field, CriteriaOperator.NOT_IN_SUBQUERY, subQuery);
    }

    public Criteria between(String field, Object start, Object end) {
        return and(field, CriteriaOperator.BETWEEN, start, end);
    }

    public Criteria orBetween(String field, Object start, Object end) {
        return or(field, CriteriaOperator.BETWEEN, start, end);
    }

    public Criteria isNull(String field) {
        return and(field, CriteriaOperator.IS_NULL);
    }

    public Criteria isNotNull(String field) {
        return and(field, CriteriaOperator.IS_NOT_NULL);
    }

    public Criteria orIsNull(String field) {
        return or(field, CriteriaOperator.IS_NULL);
    }

    public Criteria orIsNotNull(String field) {
        return or(field, CriteriaOperator.IS_NOT_NULL);
    }

    public Criteria exists(SqlSubQuery subQuery) {
        return and(null, CriteriaOperator.EXISTS, subQuery);
    }

    public Criteria notExists(SqlSubQuery subQuery) {
        return and(null, CriteriaOperator.NOT_EXISTS, subQuery);
    }

    public Criteria orExists(SqlSubQuery subQuery) {
        return or(null, CriteriaOperator.EXISTS, subQuery);
    }

    public Criteria orNotExists(SqlSubQuery subQuery) {
        return or(null, CriteriaOperator.NOT_EXISTS, subQuery);
    }

    public Criteria raw(SqlRawCondition condition) {
        return and(null, CriteriaOperator.RAW, condition);
    }

    public Criteria orRaw(SqlRawCondition condition) {
        return or(null, CriteriaOperator.RAW, condition);
    }

    public List<CriteriaClause> getClauses() {
        List<CriteriaClause> list = new ArrayList<>();
        collectClauses(root, list);
        return Collections.unmodifiableList(list);
    }

    public CriteriaGroup getRoot() {
        return root;
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    private void collectClauses(CriteriaGroup group, List<CriteriaClause> target) {
        for (CriteriaGroup.Entry entry : group.getEntries()) {
            if (entry.getNode() instanceof CriteriaClause clause) {
                target.add(clause);
            } else if (entry.getNode() instanceof CriteriaGroup nested) {
                collectClauses(nested, target);
            }
        }
    }
}
