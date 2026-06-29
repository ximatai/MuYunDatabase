package net.ximatai.muyun.database.jdbi;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractJdbiDatabaseOperationsTest {

    @Test
    void handleDateTimestampShouldAcceptInstant() {
        Instant instant = Instant.parse("2026-05-28T03:00:00Z");

        Timestamp timestamp = AbstractJdbiDatabaseOperations.handleDateTimestamp(instant);

        assertEquals(instant, timestamp.toInstant());
    }

    @Test
    void getDBValueShouldConvertPostgresArrayElementTypes() {
        JdbiDatabaseOperations<String> operations = new JdbiDatabaseOperations<>(null, null, String.class, "id");

        assertArrayEquals(new BigInteger[]{BigInteger.ONE, BigInteger.TWO},
                (BigInteger[]) operations.getDBValue(List.of(1, "2"), "_int8"));
        assertArrayEquals(new BigDecimal[]{new BigDecimal("1.5"), new BigDecimal("2.0")},
                (BigDecimal[]) operations.getDBValue(List.of("1.5", 2), "_numeric"));
        assertArrayEquals(new Timestamp[]{Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 3, 4, 5))},
                (Timestamp[]) operations.getDBValue(new String[]{"2026-01-02 03:04:05"}, "_timestamp"));
    }
}
