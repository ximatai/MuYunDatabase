package net.ximatai.muyun.database.jdbi;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractJdbiDatabaseOperationsTest {

    @Test
    void handleDateTimestampShouldAcceptInstant() {
        Instant instant = Instant.parse("2026-05-28T03:00:00Z");

        Timestamp timestamp = AbstractJdbiDatabaseOperations.handleDateTimestamp(instant);

        assertEquals(instant, timestamp.toInstant());
    }
}
