package net.ximatai.muyun.database.core.annotation;

import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.Index;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationProcessorTest {

    @Test
    void shouldSupportIdWithoutColumnAndInferItsJavaType() {
        TableWrapper table = AnnotationProcessor.fromEntityClass(IdOnlyEntity.class);

        assertEquals("id", table.getPrimaryKey().getName());
        assertEquals(ColumnType.VARCHAR, table.getPrimaryKey().getType());
    }

    @Test
    void shouldHonorColumnUniqueAndIndexedName() {
        TableWrapper table = AnnotationProcessor.fromEntityClass(IndexedEntity.class);

        Index unique = table.getIndexes().stream()
                .filter(Index::isUnique)
                .findFirst()
                .orElseThrow();
        Index named = table.getIndexes().stream()
                .filter(index -> "idx_indexed_entity_status".equals(index.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(java.util.List.of("external_no"), unique.getColumns());
        assertEquals(java.util.List.of("status"), named.getColumns());
    }

    @Test
    void shouldRejectConflictingUniqueDeclarations() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AnnotationProcessor.fromEntityClass(ConflictingEntity.class)
        );

        assertTrue(exception.getMessage().contains("Conflicting unique declarations"));
    }

    @Table(name = "id_only_entity")
    private static class IdOnlyEntity {
        @Id
        private String id;
    }

    @Table(name = "indexed_entity")
    private static class IndexedEntity {
        @Id
        private String id;

        @Column(name = "external_no", unique = true)
        private String externalNo;

        @Indexed(name = "idx_indexed_entity_status")
        @Column
        private String status;
    }

    @Table(name = "conflicting_entity")
    private static class ConflictingEntity {
        @Id
        private String id;

        @Indexed(unique = false)
        @Column(unique = true)
        private String code;
    }
}
