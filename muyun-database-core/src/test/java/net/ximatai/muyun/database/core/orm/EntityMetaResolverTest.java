package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.Index;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void shouldResolveArrayElementTypesFromAnnotationAndFieldType() {
        EntityMeta meta = new EntityMetaResolver().resolve(ArrayEntity.class);

        assertEquals(ColumnType.ARRAY, meta.findByFieldName("tags").getColumnType());
        assertEquals(ColumnType.VARCHAR, meta.findByFieldName("tags").getElementColumnType());
        assertEquals(ColumnType.ARRAY, meta.findByFieldName("scores").getColumnType());
        assertEquals(ColumnType.INT, meta.findByFieldName("scores").getElementColumnType());
    }

    @Test
    void shouldApplyEntitySchemaCustomizersOnceToTheCachedModel() {
        MuYunEntitySchemaCustomizer<CustomizedEntity> customizer =
                MuYunEntitySchemaCustomizer.forEntity(
                        CustomizedEntity.class,
                        table -> table.addIndex(new Index("status", false).named("idx_customized_status"))
                );
        EntityMetaResolver resolver = new EntityMetaResolver(List.of(customizer));

        EntityMeta first = resolver.resolve(CustomizedEntity.class);
        EntityMeta second = resolver.resolve(CustomizedEntity.class);

        assertSame(first, second);
        assertEquals(1, first.getTableWrapper().getIndexes().size());
        assertEquals("idx_customized_status", first.getTableWrapper().getIndexes().getFirst().getName());
    }

    private static class DemoEntity {
    }

    @Table(name = "array_entity")
    private static class ArrayEntity {
        @Id
        @Column
        private String id;

        @Column(type = ColumnType.ARRAY, elementType = ColumnType.VARCHAR)
        private List<String> tags;

        @Column(type = ColumnType.ARRAY)
        private List<Integer> scores;
    }

    @Table(name = "customized_entity")
    private static class CustomizedEntity {
        @Id
        @Column
        private String id;

        @Column
        private String status;
    }
}
