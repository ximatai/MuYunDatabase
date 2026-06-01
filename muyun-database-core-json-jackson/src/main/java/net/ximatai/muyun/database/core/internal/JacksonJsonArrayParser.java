package net.ximatai.muyun.database.core.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用 Jackson 的 JsonArrayParser 实现，提供专业的 JSON 解析能力。
 * 可选依赖，用户需要在 classpath 中包含此模块才会生效。
 */
public class JacksonJsonArrayParser implements JsonArrayParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        String content = json.trim();
        if (content.startsWith("[")) {
            try {
                List<Object> values = MAPPER.readValue(content, new TypeReference<List<Object>>() {});
                List<String> result = new ArrayList<>();
                for (Object value : values) {
                    if (!(value instanceof String text)) {
                        throw new IllegalArgumentException("JSON_SET only supports JSON string arrays: " + content);
                    }
                    result.add(text);
                }
                return result;
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid JSON array format: " + content, e);
            }
        } else {
            return Collections.singletonList(content);
        }
    }

    @Override
    public String serialize(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize list to JSON", e);
        }
    }
}
