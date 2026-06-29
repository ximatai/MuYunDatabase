package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.ColumnType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableMetaTest {

    @Test
    void shouldResolveFieldsAndColumnsFromRuntimeMetadata() {
        TableMeta meta = TableMeta.builder("public", "runtime_record")
                .id("id", "id", ColumnType.VARCHAR, String.class)
                .field("title", "record_title", ColumnType.VARCHAR, String.class)
                .jsonSet("tags", "tags", Set.class, String.class)
                .array("scores", "scores", ColumnType.INT, List.class, Integer.class)
                .build();

        assertEquals("runtime_record", meta.getTableName());
        assertEquals("public", meta.getSchema());
        assertEquals("id", meta.getIdField().getFieldName());
        assertEquals("record_title", meta.resolveColumnName("title"));
        assertEquals("record_title", meta.resolveColumnName("record_title"));
        assertEquals("title", meta.resolveFieldName("RECORD_TITLE"));
        assertEquals("scores", meta.resolveFieldName("scores"));
        assertEquals(ColumnType.JSON_SET, meta.findByFieldName("tags").getColumnType());
        assertEquals(String.class, meta.findByFieldName("tags").getCollectionElementType().orElseThrow());
        assertEquals(ColumnType.ARRAY, meta.findByFieldName("scores").getColumnType());
        assertEquals(ColumnType.INT, meta.findByFieldName("scores").getElementColumnType());
        assertEquals(Integer.class, meta.findByFieldName("scores").getCollectionElementType().orElseThrow());
        assertNull(meta.resolveColumnName("missing"));
    }

    @Test
    void shouldRejectDuplicateFieldNames() {
        OrmException exception = assertThrows(OrmException.class, () -> TableMeta.builder("runtime_record")
                .field("title", "record_title", ColumnType.VARCHAR, String.class)
                .field("title", "record_name", ColumnType.VARCHAR, String.class)
                .build());

        assertEquals(OrmException.Code.INVALID_MAPPING, exception.getCode());
    }

    @Test
    void shouldRejectDuplicateColumnNamesCaseInsensitively() {
        OrmException exception = assertThrows(OrmException.class, () -> TableMeta.builder("runtime_record")
                .field("title", "record_title", ColumnType.VARCHAR, String.class)
                .field("name", "RECORD_TITLE", ColumnType.VARCHAR, String.class)
                .build());

        assertEquals(OrmException.Code.INVALID_MAPPING, exception.getCode());
    }

    @Test
    void shouldRejectUnsafeIdentifiers() {
        assertInvalidMapping(() -> TableMeta.builder(" public", "runtime_record")
                .field("id", "id", ColumnType.VARCHAR, String.class)
                .build());
        assertInvalidMapping(() -> TableMeta.builder("public", " runtime_record")
                .field("id", "id", ColumnType.VARCHAR, String.class)
                .build());
        assertInvalidMapping(() -> TableMeta.builder("runtime_record")
                .field("title", "record\u0000title", ColumnType.VARCHAR, String.class)
                .build());
    }

    @Test
    void shouldValidateIdFieldRules() {
        RuntimeFieldMeta id = new RuntimeFieldMeta("id", "id", ColumnType.VARCHAR, ColumnType.UNKNOWN,
                String.class, null, true);
        RuntimeFieldMeta title = RuntimeFieldMeta.of("title", "record_title", ColumnType.VARCHAR, String.class);

        TableMeta meta = TableMeta.of(null, "runtime_record", List.of(id, title), id);
        assertEquals("id", meta.getIdField().getFieldName());

        assertInvalidMapping(() -> TableMeta.of(null, "runtime_record", List.of(title),
                RuntimeFieldMeta.of("missing", "missing", ColumnType.VARCHAR, String.class)));

        assertInvalidMapping(() -> TableMeta.of(null, "runtime_record", List.of(id, title), title));

        RuntimeFieldMeta otherId = new RuntimeFieldMeta("otherId", "other_id", ColumnType.VARCHAR, ColumnType.UNKNOWN,
                String.class, null, true);
        assertInvalidMapping(() -> TableMeta.of(null, "runtime_record", List.of(id, otherId), null));
    }

    @Test
    void shouldValidateArbitraryFieldMetaImplementations() {
        assertInvalidMapping(() -> TableMeta.of(null, "runtime_record", List.of(new StubFieldMeta(
                "", "title", ColumnType.VARCHAR
        )), null));
        assertInvalidMapping(() -> TableMeta.of(null, "runtime_record", List.of(new StubFieldMeta(
                "title", "title", null
        )), null));
    }

    @Test
    void shouldRejectArrayWithoutSupportedElementColumnType() {
        assertInvalidMapping(() -> TableMeta.builder("runtime_record")
                .field("scores", "scores", ColumnType.ARRAY, List.class)
                .build());
        assertInvalidMapping(() -> TableMeta.builder("runtime_record")
                .array("payloads", "payloads", ColumnType.JSON, List.class, Object.class)
                .build());
    }

    @Test
    void shouldKeepConvenienceBuilderMetadata() {
        TableMeta meta = TableMeta.builder("runtime_record")
                .csvSet("csvTags", "csv_tags", Set.class, String.class)
                .jsonSet("jsonTags", "json_tags", Set.class, String.class)
                .array("timestamps", "timestamps", ColumnType.TIMESTAMP, List.class, java.time.Instant.class)
                .build();

        assertEquals(ColumnType.SET, meta.findByFieldName("csvTags").getColumnType());
        assertEquals(ColumnType.JSON_SET, meta.findByFieldName("jsonTags").getColumnType());
        assertEquals(ColumnType.ARRAY, meta.findByFieldName("timestamps").getColumnType());
        assertEquals(ColumnType.TIMESTAMP, meta.findByFieldName("timestamps").getElementColumnType());
        assertEquals(java.time.Instant.class, meta.findByFieldName("timestamps").getCollectionElementType().orElseThrow());
    }

    private static void assertInvalidMapping(Executable executable) {
        OrmException exception = assertThrows(OrmException.class, executable::execute);
        assertEquals(OrmException.Code.INVALID_MAPPING, exception.getCode());
        assertTrue(exception.getMessage() != null && !exception.getMessage().isBlank());
    }

    @FunctionalInterface
    private interface Executable {
        void execute();
    }

    private record StubFieldMeta(String fieldName,
                                 String columnName,
                                 ColumnType columnType) implements FieldMeta {

        @Override
        public String getFieldName() {
            return fieldName;
        }

        @Override
        public String getColumnName() {
            return columnName;
        }

        @Override
        public ColumnType getColumnType() {
            return columnType;
        }

        @Override
        public ColumnType getElementColumnType() {
            return ColumnType.UNKNOWN;
        }

        @Override
        public boolean isId() {
            return false;
        }

        @Override
        public Class<?> getFieldType() {
            return Object.class;
        }

        @Override
        public Optional<Class<?>> getCollectionElementType() {
            return Optional.empty();
        }
    }
}
