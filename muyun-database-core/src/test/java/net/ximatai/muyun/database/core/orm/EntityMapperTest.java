package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
