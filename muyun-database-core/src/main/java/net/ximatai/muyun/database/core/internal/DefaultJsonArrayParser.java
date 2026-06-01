package net.ximatai.muyun.database.core.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 核心模块提供的默认保底解析器。
 * 不依赖第三方库，仅处理安全/简单场景的 JSON 数组。
 * 同时也修复了原手写实现的转义缺陷。
 */
public class DefaultJsonArrayParser implements JsonArrayParser {

    @Override
    public List<String> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String content = json.trim();
        if (content.startsWith("[")) {
            return parseJsonArray(content);
        } else {
            // 兼容单个字符串输入（如 "single"）
            return Collections.singletonList(content);
        }
    }

    @Override
    public String serialize(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String element : list) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJsonString(element)).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private List<String> parseJsonArray(String content) {
        List<String> result = new ArrayList<>();
        content = content.trim();
        if (!content.startsWith("[") || !content.endsWith("]")) {
            throw new IllegalArgumentException("Invalid JSON array format: " + content);
        }
        int i = 1;
        i = skipWhitespace(content, i);
        if (i < content.length() && content.charAt(i) == ']') {
            if (skipWhitespace(content, i + 1) != content.length()) {
                throw new IllegalArgumentException("Invalid JSON array format: " + content);
            }
            return result;
        }

        while (i < content.length()) {
            i = skipWhitespace(content, i);
            if (i >= content.length() || content.charAt(i) != '"') {
                throw new IllegalArgumentException("JSON_SET only supports JSON string arrays: " + content);
            }

            ParsedString parsed = parseString(content, i);
            result.add(parsed.value());
            i = skipWhitespace(content, parsed.nextIndex());

            if (i >= content.length()) {
                throw new IllegalArgumentException("Invalid JSON array format: " + content);
            }

            char next = content.charAt(i);
            if (next == ',') {
                i++;
                int afterComma = skipWhitespace(content, i);
                if (afterComma < content.length() && content.charAt(afterComma) == ']') {
                    throw new IllegalArgumentException("Trailing comma is not allowed in JSON array: " + content);
                }
                i = afterComma;
            } else if (next == ']') {
                if (skipWhitespace(content, i + 1) != content.length()) {
                    throw new IllegalArgumentException("Invalid JSON array format: " + content);
                }
                return result;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' in JSON array: " + content);
            }
        }

        throw new IllegalArgumentException("Invalid JSON array format: " + content);
    }

    private int skipWhitespace(String content, int index) {
        int i = index;
        while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
            i++;
        }
        return i;
    }

    private ParsedString parseString(String content, int quoteIndex) {
        StringBuilder sb = new StringBuilder();
        int i = quoteIndex + 1;
        while (i < content.length()) {
            char current = content.charAt(i);
            if (current == '"') {
                return new ParsedString(sb.toString(), i + 1);
            }
            if (current == '\\') {
                if (i + 1 >= content.length()) {
                    throw new IllegalArgumentException("Invalid JSON escape in " + content);
                }
                char escaped = content.charAt(i + 1);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 5 >= content.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape in " + content);
                        }
                        String hex = content.substring(i + 2, i + 6);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid unicode escape in " + content, e);
                        }
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape in " + content);
                }
                i += 2;
            } else {
                if (current < 0x20) {
                    throw new IllegalArgumentException("Control character is not allowed in JSON string: " + content);
                }
                sb.append(current);
                i++;
            }
        }
        throw new IllegalArgumentException("Invalid JSON array format: missing closing quote in " + content);
    }

    private String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private record ParsedString(String value, int nextIndex) {
    }
}
