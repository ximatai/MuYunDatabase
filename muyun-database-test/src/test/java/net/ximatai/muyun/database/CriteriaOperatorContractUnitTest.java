package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.orm.CriteriaOperator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriteriaOperatorContractUnitTest {

    @Test
    void shouldKeepOperatorContractForMuyunSpringConsumer() {
        Set<String> names = Arrays.stream(CriteriaOperator.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertTrue(names.contains("NOT_IN"), "CriteriaOperator must include NOT_IN");
        assertTrue(names.contains("IN_SUBQUERY"), "CriteriaOperator must include IN_SUBQUERY");
        assertTrue(names.contains("NOT_IN_SUBQUERY"), "CriteriaOperator must include NOT_IN_SUBQUERY");
        assertFalse(names.contains("IN_SUB_QUERY"), "Use IN_SUBQUERY naming only");
        assertFalse(names.contains("NOT_IN_SUB_QUERY"), "Use NOT_IN_SUBQUERY naming only");
    }
}
