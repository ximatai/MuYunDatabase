package net.ximatai.muyun.database.core.orm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CriteriaClause implements CriteriaNode {
    private final String field;
    private final CriteriaOperator operator;
    private final List<Object> values;

    public CriteriaClause(String field, CriteriaOperator operator, List<Object> values) {
        this.field = field;
        this.operator = operator;
        this.values = new ArrayList<>(values);
    }

    public String getField() {
        return field;
    }

    public CriteriaOperator getOperator() {
        return operator;
    }

    public List<Object> getValues() {
        return Collections.unmodifiableList(values);
    }
}
