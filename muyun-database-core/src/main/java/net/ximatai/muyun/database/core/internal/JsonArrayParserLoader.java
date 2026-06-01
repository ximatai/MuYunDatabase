package net.ximatai.muyun.database.core.internal;

import java.util.ServiceLoader;

/**
 * SPI 加载工具类，用于加载 JsonArrayParser 实现。
 */
public final class JsonArrayParserLoader {

    private static volatile JsonArrayParser parser;

    private JsonArrayParserLoader() {
    }

    public static JsonArrayParser get() {
        if (parser == null) {
            synchronized (JsonArrayParserLoader.class) {
                if (parser == null) {
                    parser = loadParser();
                }
            }
        }
        return parser;
    }

    private static JsonArrayParser loadParser() {
        ServiceLoader<JsonArrayParser> loader = ServiceLoader.load(JsonArrayParser.class);
        for (JsonArrayParser implementation : loader) {
            if (!(implementation instanceof DefaultJsonArrayParser)) {
                // 优先返回非默认实现（如 Jackson 实现）
                return implementation;
            }
        }
        // 兜底：返回默认实现
        return new DefaultJsonArrayParser();
    }
}
