package net.ximatai.muyun.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TestingArchitectureGuardTest {

    private static final Path TEST_SRC_ROOT = Path.of("src", "test", "java", "net", "ximatai", "muyun", "database");
    private static final Pattern SHARED_SCHEMA_PATTERN =
            Pattern.compile("\\bString\\s+schema\\s*=\\s*\"test\"\\s*;");
    private static final List<Pattern> LEGACY_DDL_NAME_PATTERNS = List.of(
            Pattern.compile("\\bString\\s+baseTable\\s*=\\s*\"a_table\"\\s*;"),
            Pattern.compile("\\bString\\s+schema\\s*=\\s*\"just_a_test\"\\s*;"),
            Pattern.compile("withName\\(\\s*\"test_inherit_base\"\\s*\\)"),
            Pattern.compile("withName\\(\\s*\"test_inherit_child\"\\s*\\)")
    );

    @Test
    void shouldNotUseSharedTestSchemaLiteral() throws IOException {
        List<Path> javaFiles = listJavaFiles();
        for (Path file : javaFiles) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(
                    SHARED_SCHEMA_PATTERN.matcher(content).find(),
                    "Shared schema literal is forbidden, file: " + file
            );
        }
    }

    @Test
    void shouldNotUseHardcodedSqlObjectBasicTableInSqlObjectAnnotations() throws IOException {
        Path baseTest = TEST_SRC_ROOT.resolve("MuYunDatabaseBaseTest.java");
        String content = Files.readString(baseTest, StandardCharsets.UTF_8);

        assertFalse(
                content.contains("insert into sql_object_basic("),
                "SQL Object insert should use @Define table placeholder."
        );
        assertFalse(
                content.contains("from sql_object_basic "),
                "SQL Object query should use @Define table placeholder."
        );
    }

    @Test
    void shouldNotReintroduceLegacySharedDdlNames() throws IOException {
        Path baseTest = TEST_SRC_ROOT.resolve("MuYunDatabaseBaseTest.java");
        String content = Files.readString(baseTest, StandardCharsets.UTF_8);

        for (Pattern forbidden : LEGACY_DDL_NAME_PATTERNS) {
            assertFalse(
                    forbidden.matcher(content).find(),
                    "Legacy shared DDL naming is forbidden, pattern: " + forbidden
            );
        }
    }

    private List<Path> listJavaFiles() throws IOException {
        try (var stream = Files.walk(TEST_SRC_ROOT)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
