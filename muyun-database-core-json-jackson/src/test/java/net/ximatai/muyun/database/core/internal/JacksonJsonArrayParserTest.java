package net.ximatai.muyun.database.core.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonJsonArrayParserTest {

    @Test
    void shouldLoadJacksonParserBeforeDefaultParser() {
        assertInstanceOf(JacksonJsonArrayParser.class, JsonArrayParserLoader.get());
    }

    @Test
    void shouldRoundTripStringArray() {
        JsonArrayParser parser = new JacksonJsonArrayParser();

        String json = parser.serialize(List.of("hello, world", "say \"yes\""));

        assertEquals("[\"hello, world\",\"say \\\"yes\\\"\"]", json);
        assertEquals(List.of("hello, world", "say \"yes\""), parser.parse(json));
    }

    @Test
    void shouldRejectInvalidJsonArray() {
        JsonArrayParser parser = new JacksonJsonArrayParser();

        List.of(
                "[\"unterminated]",
                "[a]",
                "[\"a\" \"b\"]",
                "[\"a\",]",
                "[\"a\", null]",
                "[1]",
                "[\"bad\\qescape\"]"
        ).forEach(json -> assertThrows(IllegalArgumentException.class, () -> parser.parse(json), json));
    }
}
