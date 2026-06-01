package net.ximatai.muyun.database.core.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonArrayParserLoaderTest {

    @Test
    void shouldLoadDefaultParserFromCoreModule() {
        assertInstanceOf(DefaultJsonArrayParser.class, JsonArrayParserLoader.get());
    }

    @Test
    void defaultParserShouldRejectInvalidJsonArray() {
        JsonArrayParser parser = new DefaultJsonArrayParser();

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
