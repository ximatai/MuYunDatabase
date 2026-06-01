package net.ximatai.muyun.database.core.internal;

import java.util.List;

/**
 * 内部 SPI 接口，用于解析 JSON 数组。
 * 不对外公开，由框架内部使用。
 * 核心模块提供默认实现，可选模块提供专业 JSON 库实现。
 */
public interface JsonArrayParser {

    /**
     * 解析字符串为字符串列表。
     *
     * @param json 待解析的字符串，可能是 JSON 数组（["a","b"]）或单个字符串（"a"）
     * @return 解析结果列表
     * @throws IllegalArgumentException 当 JSON 数组格式非法时抛出
     */
    List<String> parse(String json);

    /**
     * 将字符串列表序列化为 JSON 数组字符串。
     *
     * @param list 待序列化的列表
     * @return JSON 数组字符串
     */
    String serialize(List<String> list);
}
