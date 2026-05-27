package net.ximatai.muyun.database.core.orm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityMetaResolverTest {
    @Test
    void shouldRejectNullTableResolverResultAsInvalidMapping() {
        EntityMetaResolver resolver = new EntityMetaResolver(entityClass -> null);

        OrmException exception = assertThrows(
                OrmException.class,
                () -> resolver.resolve(DemoEntity.class)
        );

        assertEquals(OrmException.Code.INVALID_MAPPING, exception.getCode());
    }

    private static class DemoEntity {
    }
}
