package net.ximatai.muyun.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentationGuardTest {

    private static final List<Path> DOC_FILES = List.of(
            Path.of("..", "README.md"),
            Path.of("..", "docs", "QUICKSTART.md"),
            Path.of("..", "docs", "API_CONTRACT.md"),
            Path.of("..", "docs", "REFACTOR_GUIDE.md")
    );

    @Test
    void shouldNotContainLeakedEnvironmentPrefixOrLegacyTypos() throws IOException {
        for (Path file : DOC_FILES) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(content.contains("${cf."), "Forbidden placeholder prefix found: " + file);
            assertFalse(content.contains("cf:"), "Forbidden config key found: " + file);
            assertFalse(content.contains("veryun."), "Typo found: " + file);
        }
    }

    @Test
    void shouldKeepRepositoryAsPrimaryEntryInCoreDocs() throws IOException {
        for (Path file : DOC_FILES) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(
                    content.contains("@MuYunSqlDao") || content.contains("MuYunSqlDaoFactory"),
                    "Legacy repository entry found: " + file
            );
        }
    }
}
