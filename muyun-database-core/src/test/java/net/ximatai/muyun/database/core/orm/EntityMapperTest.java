package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityMapperTest {

    @Test
    void fromMapShouldConvertTimestampToInstant() {
        Instant instant = Instant.parse("2026-05-28T03:00:00Z");
        EntityMeta meta = new EntityMetaResolver().resolve(AuditEntity.class);

        AuditEntity entity = EntityMapper.fromMap(meta, Map.of(
                "id", "audit-1",
                "created_at", Timestamp.from(instant)
        ), AuditEntity.class);

        assertEquals("audit-1", entity.getId());
        assertEquals(instant, entity.getCreatedAt());
    }

    @Test
    void jsonSetShouldSerializeCollectionWithCommasAndQuotes() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        JsonSetEntity entity = new JsonSetEntity();
        entity.setId("e-1");
        Set<String> tags = new LinkedHashSet<>();
        tags.add("hello, world");
        tags.add("say \"yes\"");
        tags.add("line1\nline2");
        entity.setTags(tags);

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        String stored = (String) map.get("tags");
        assertNotNull(stored);
        assertEquals(true, stored.startsWith("["));
        assertEquals(true, stored.endsWith("]"));
        assertEquals(true, stored.contains("hello, world"));
        assertEquals(true, stored.contains("\"say \\\"yes\\\"\""));
    }

    @Test
    void jsonSetShouldDeserializeToLinkedHashSet() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        JsonSetEntity entity = EntityMapper.fromMap(meta, Map.of(
                "id", "e-1",
                "tags", "[\"tag1\",\"tag2\",\"tag3\"]"
        ), JsonSetEntity.class);

        assertNotNull(entity.getTags());
        assertEquals(LinkedHashSet.class, entity.getTags().getClass());
        List<String> list = new ArrayList<>(entity.getTags());
        assertEquals(List.of("tag1", "tag2", "tag3"), list);
    }

    @Test
    void jsonSetShouldDeserializeToTreeSet() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetTreeEntity.class);

        JsonSetTreeEntity entity = EntityMapper.fromMap(meta, Map.of(
                "id", "e-2",
                "tags", "[\"zebra\",\"apple\",\"banana\"]"
        ), JsonSetTreeEntity.class);

        assertNotNull(entity.getTags());
        assertEquals(TreeSet.class, entity.getTags().getClass());
        List<String> list = new ArrayList<>(entity.getTags());
        assertEquals(List.of("apple", "banana", "zebra"), list);
    }

    @Test
    void jsonSetShouldDeserializeToArrayList() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetListEntity.class);

        JsonSetListEntity entity = EntityMapper.fromMap(meta, Map.of(
                "id", "e-3",
                "tags", "[\"a\",\"b\"]"
        ), JsonSetListEntity.class);

        assertNotNull(entity.getTags());
        assertEquals(ArrayList.class, entity.getTags().getClass());
        assertEquals(List.of("a", "b"), entity.getTags());
    }

    @Test
    void jsonSetShouldDeserializeToListInterface() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetListInterfaceEntity.class);

        JsonSetListInterfaceEntity entity = EntityMapper.fromMap(meta, Map.of(
                "id", "e-31",
                "tags", "[\"a\",\"b\"]"
        ), JsonSetListInterfaceEntity.class);

        assertNotNull(entity.getTags());
        assertEquals(ArrayList.class, entity.getTags().getClass());
        assertEquals(List.of("a", "b"), entity.getTags());
    }

    @Test
    void csvSetShouldDeserializeToListInterface() {
        EntityMeta meta = new EntityMetaResolver().resolve(CsvSetListInterfaceEntity.class);

        CsvSetListInterfaceEntity entity = EntityMapper.fromMap(meta, Map.of(
                "id", "e-32",
                "tags", "a,b,a"
        ), CsvSetListInterfaceEntity.class);

        assertNotNull(entity.getTags());
        assertEquals(ArrayList.class, entity.getTags().getClass());
        assertEquals(List.of("a", "b"), entity.getTags());
    }

    @Test
    void fieldMetaShouldExposeCollectionElementTypeWhenDeclared() {
        EntityMeta listMeta = new EntityMetaResolver().resolve(JsonSetStatusListEntity.class);
        EntityMeta setMeta = new EntityMetaResolver().resolve(CsvSetStatusEntity.class);
        EntityMeta stringMeta = new EntityMetaResolver().resolve(JsonSetListInterfaceEntity.class);
        EntityMeta rawMeta = new EntityMetaResolver().resolve(JsonSetRawListEntity.class);
        EntityMeta objectMeta = new EntityMetaResolver().resolve(JsonSetRawEntity.class);

        assertEquals(TestStatus.class, listMeta.findByFieldName("statuses").getCollectionElementType().orElseThrow());
        assertEquals(TestStatus.class, setMeta.findByFieldName("statuses").getCollectionElementType().orElseThrow());
        assertEquals(String.class, stringMeta.findByFieldName("tags").getCollectionElementType().orElseThrow());
        assertFalse(rawMeta.findByFieldName("tags").getCollectionElementType().isPresent());
        assertFalse(objectMeta.findByFieldName("tags").getCollectionElementType().isPresent());
    }

    @Test
    void jsonSetShouldUseConverterForListElements() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetStatusListEntity.class);
        JsonSetStatusListEntity entity = new JsonSetStatusListEntity();
        entity.setId("e-33");
        entity.setStatuses(List.of(TestStatus.ENABLED, TestStatus.DISABLED, TestStatus.ENABLED));

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true, new TestStatusCodeConverter());

        assertEquals("[\"enabled\",\"disabled\"]", map.get("statuses"));

        JsonSetStatusListEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "e-33",
                "statuses", "[\"enabled\",\"disabled\"]"
        ), JsonSetStatusListEntity.class, new TestStatusCodeConverter());

        assertNotNull(loaded.getStatuses());
        assertEquals(ArrayList.class, loaded.getStatuses().getClass());
        assertEquals(List.of(TestStatus.ENABLED, TestStatus.DISABLED), loaded.getStatuses());
    }

    @Test
    void csvSetShouldUseConverterForSetElements() {
        EntityMeta meta = new EntityMetaResolver().resolve(CsvSetStatusEntity.class);
        CsvSetStatusEntity entity = new CsvSetStatusEntity();
        entity.setId("e-34");
        LinkedHashSet<TestStatus> statuses = new LinkedHashSet<>();
        statuses.add(TestStatus.ENABLED);
        statuses.add(TestStatus.DISABLED);
        entity.setStatuses(statuses);

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true, new TestStatusCodeConverter());

        assertEquals("enabled,disabled", map.get("statuses"));

        CsvSetStatusEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "e-34",
                "statuses", "enabled,disabled,enabled"
        ), CsvSetStatusEntity.class, new TestStatusCodeConverter());

        assertNotNull(loaded.getStatuses());
        assertEquals(LinkedHashSet.class, loaded.getStatuses().getClass());
        assertEquals(List.of(TestStatus.ENABLED, TestStatus.DISABLED), new ArrayList<>(loaded.getStatuses()));
    }

    @Test
    void jsonSetShouldUseDefaultConverterForEnumElements() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetStatusListEntity.class);
        JsonSetStatusListEntity entity = new JsonSetStatusListEntity();
        entity.setId("e-35");
        entity.setStatuses(List.of(TestStatus.ENABLED, TestStatus.DISABLED, TestStatus.ENABLED));

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        assertEquals("[\"ENABLED\",\"DISABLED\"]", map.get("statuses"));

        JsonSetStatusListEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "e-35",
                "statuses", "[\"ENABLED\",\"DISABLED\"]"
        ), JsonSetStatusListEntity.class);

        assertNotNull(loaded.getStatuses());
        assertEquals(List.of(TestStatus.ENABLED, TestStatus.DISABLED), loaded.getStatuses());
    }

    @Test
    void csvSetShouldUseDefaultConverterForIntegerElements() {
        EntityMeta meta = new EntityMetaResolver().resolve(CsvSetIntegerEntity.class);

        CsvSetIntegerEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "e-36",
                "ids", "1,2,1"
        ), CsvSetIntegerEntity.class);

        assertNotNull(loaded.getIds());
        assertEquals(LinkedHashSet.class, loaded.getIds().getClass());
        assertEquals(List.of(1, 2), new ArrayList<>(loaded.getIds()));
    }

    @Test
    void jsonSetShouldUseDefaultConverterForIntegerElements() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetIntegerListEntity.class);

        JsonSetIntegerListEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "e-37",
                "ids", "[\"1\",\"2\",\"1\"]"
        ), JsonSetIntegerListEntity.class);

        assertNotNull(loaded.getIds());
        assertEquals(ArrayList.class, loaded.getIds().getClass());
        assertEquals(List.of(1, 2), loaded.getIds());
    }

    @Test
    void arrayShouldUseConverterForElementsAndPreserveDuplicates() {
        EntityMeta meta = new EntityMetaResolver().resolve(ArrayStatusListEntity.class);
        ArrayStatusListEntity entity = new ArrayStatusListEntity();
        entity.id = "array-1";
        entity.statuses = List.of(TestStatus.ENABLED, TestStatus.DISABLED, TestStatus.ENABLED);

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true, new TestStatusCodeConverter());

        assertEquals(List.of("enabled", "disabled", "enabled"), map.get("statuses"));

        ArrayStatusListEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "array-1",
                "statuses", new String[]{"enabled", "disabled", "enabled"}
        ), ArrayStatusListEntity.class, new TestStatusCodeConverter());

        assertEquals(ArrayList.class, loaded.statuses.getClass());
        assertEquals(List.of(TestStatus.ENABLED, TestStatus.DISABLED, TestStatus.ENABLED), loaded.statuses);
    }

    @Test
    void arrayShouldAdaptJavaArrayFields() {
        EntityMeta meta = new EntityMetaResolver().resolve(ArrayStringArrayEntity.class);
        ArrayStringArrayEntity entity = new ArrayStringArrayEntity();
        entity.id = "array-2";
        entity.tags = new String[]{"red", "blue"};

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        assertEquals(List.of("red", "blue"), map.get("tags"));

        ArrayStringArrayEntity loaded = EntityMapper.fromMap(meta, Map.of(
                "id", "array-2",
                "tags", new String[]{"green", "yellow"}
        ), ArrayStringArrayEntity.class);

        assertArrayEquals(new String[]{"green", "yellow"}, loaded.tags);
    }

    @Test
    void jsonSetShouldSerializeSingleElement() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        JsonSetEntity entity = new JsonSetEntity();
        entity.setId("e-4");
        Set<String> single = new LinkedHashSet<>();
        single.add("only-one");
        entity.setTags(single);

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        assertEquals("[\"only-one\"]", map.get("tags"));
    }

    @Test
    void jsonSetShouldSerializeEmptySet() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        JsonSetEntity entity = new JsonSetEntity();
        entity.setId("e-5");
        entity.setTags(new LinkedHashSet<>());

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        assertEquals("[]", map.get("tags"));
    }

    @Test
    void jsonSetShouldHandleNullValue() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        JsonSetEntity entity = new JsonSetEntity();
        entity.setId("e-6");
        entity.setTags(null);

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        assertEquals(null, map.get("tags"));
    }

    @Test
    void jsonSetShouldHandleNullInputOnDeserialize() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        Map<String, Object> input = new java.util.HashMap<>();
        input.put("id", "e-7");
        input.put("tags", null);

        JsonSetEntity entity = EntityMapper.fromMap(meta, input, JsonSetEntity.class);

        assertEquals(null, entity.getTags());
    }

    @Test
    void jsonSetShouldNormalizeJsonStringInput() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetRawEntity.class);

        JsonSetRawEntity entity = new JsonSetRawEntity();
        entity.setId("e-8");
        entity.setTags(" [\"hello, world\", \"say \\\"yes\\\"\"] ");

        Map<String, Object> map = EntityMapper.toMap(meta, entity, false, true);

        assertEquals("[\"hello, world\",\"say \\\"yes\\\"\"]", map.get("tags"));
    }

    @Test
    void jsonSetShouldRejectInvalidJsonStringInput() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetRawEntity.class);

        JsonSetRawEntity entity = new JsonSetRawEntity();
        entity.setId("e-9");
        entity.setTags("[\"unterminated]");

        assertThrows(IllegalArgumentException.class, () -> EntityMapper.toMap(meta, entity, false, true));
    }

    @Test
    void jsonSetShouldRejectInvalidJsonOnDeserialize() {
        EntityMeta meta = new EntityMetaResolver().resolve(JsonSetEntity.class);

        assertThrows(IllegalArgumentException.class, () -> EntityMapper.fromMap(meta, Map.of(
                "id", "e-10",
                "tags", "[\"unterminated]"
        ), JsonSetEntity.class));
    }

    @Table(name = "audit_entity")
    public static class AuditEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "created_at")
        private Instant createdAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    @Table(name = "json_set_entity")
    public static class JsonSetEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.JSON_SET)
        private Set<String> tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Set<String> getTags() {
            return tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }
    }

    @Table(name = "json_set_tree_entity")
    public static class JsonSetTreeEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.JSON_SET)
        private TreeSet<String> tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public TreeSet<String> getTags() {
            return tags;
        }

        public void setTags(TreeSet<String> tags) {
            this.tags = tags;
        }
    }

    @Table(name = "json_set_list_entity")
    public static class JsonSetListEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.JSON_SET)
        private ArrayList<String> tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ArrayList<String> getTags() {
            return tags;
        }

        public void setTags(ArrayList<String> tags) {
            this.tags = tags;
        }
    }

    @Table(name = "json_set_list_interface_entity")
    public static class JsonSetListInterfaceEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.JSON_SET)
        private List<String> tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @Table(name = "csv_set_list_interface_entity")
    public static class CsvSetListInterfaceEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.SET)
        private List<String> tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    @Table(name = "json_set_raw_entity")
    public static class JsonSetRawEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.JSON_SET)
        private Object tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Object getTags() {
            return tags;
        }

        public void setTags(Object tags) {
            this.tags = tags;
        }
    }

    @Table(name = "json_set_raw_list_entity")
    public static class JsonSetRawListEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.JSON_SET)
        @SuppressWarnings("rawtypes")
        private List tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @SuppressWarnings("rawtypes")
        public List getTags() {
            return tags;
        }

        @SuppressWarnings("rawtypes")
        public void setTags(List tags) {
            this.tags = tags;
        }
    }

    @Table(name = "json_set_status_list_entity")
    public static class JsonSetStatusListEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "statuses", type = ColumnType.JSON_SET)
        private List<TestStatus> statuses;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<TestStatus> getStatuses() {
            return statuses;
        }

        public void setStatuses(List<TestStatus> statuses) {
            this.statuses = statuses;
        }
    }

    @Table(name = "csv_set_status_entity")
    public static class CsvSetStatusEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "statuses", type = ColumnType.SET)
        private Set<TestStatus> statuses;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Set<TestStatus> getStatuses() {
            return statuses;
        }

        public void setStatuses(Set<TestStatus> statuses) {
            this.statuses = statuses;
        }
    }

    @Table(name = "csv_set_integer_entity")
    public static class CsvSetIntegerEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "ids", type = ColumnType.SET)
        private Set<Integer> ids;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Set<Integer> getIds() {
            return ids;
        }

        public void setIds(Set<Integer> ids) {
            this.ids = ids;
        }
    }

    @Table(name = "json_set_integer_list_entity")
    public static class JsonSetIntegerListEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "ids", type = ColumnType.JSON_SET)
        private List<Integer> ids;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<Integer> getIds() {
            return ids;
        }

        public void setIds(List<Integer> ids) {
            this.ids = ids;
        }
    }

    @Table(name = "array_status_list_entity")
    public static class ArrayStatusListEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "statuses", type = ColumnType.ARRAY, elementType = ColumnType.VARCHAR)
        private List<TestStatus> statuses;
    }

    @Table(name = "array_string_array_entity")
    public static class ArrayStringArrayEntity {
        @Id
        @Column(length = 32)
        private String id;

        @Column(name = "tags", type = ColumnType.ARRAY)
        private String[] tags;
    }

    private enum TestStatus {
        ENABLED("enabled"),
        DISABLED("disabled");

        private final String code;

        TestStatus(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        static TestStatus fromCode(Object value) {
            String code = String.valueOf(value);
            for (TestStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status code: " + code);
        }
    }

    private static class TestStatusCodeConverter implements DatabaseValueConverter {
        @Override
        public Object toDatabaseValue(Object value) {
            if (value instanceof TestStatus status) {
                return status.getCode();
            }
            return DatabaseValueConverter.DEFAULT.toDatabaseValue(value);
        }

        @Override
        public Object fromDatabaseValue(Object value, Class<?> targetType) {
            if (targetType == TestStatus.class) {
                return TestStatus.fromCode(value);
            }
            return DatabaseValueConverter.DEFAULT.fromDatabaseValue(value, targetType);
        }
    }
}
