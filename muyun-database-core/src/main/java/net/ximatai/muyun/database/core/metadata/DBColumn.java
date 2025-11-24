package net.ximatai.muyun.database.core.metadata;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库列元数据类
 * 用于封装从数据库元数据中读取的列信息
 * 提供与构建器Column类的转换功能
 */
public class DBColumn {
    // 列基本信息
    private String name;           // 列名
    private String description;    // 列注释/描述
    private String type;          // 数据库类型字符串
    private String defaultValue;  // 默认值原始字符串
    private boolean nullable;     // 是否允许为空
    private boolean primaryKey;   // 是否为主键
    private boolean sequence;     // 是否使用序列
    private Integer length;       // 字段长度

    // 使用正则表达式来匹配单引号之间的内容
    private static final Pattern SINGLE_QUOTE_PATTERN = Pattern.compile("'([^']*)'");

    // Getter和Setter方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取处理后的默认值
     * 对原始默认值进行格式化处理，使其符合SQL标准
     *
     * @return 格式化后的默认值
     */
    public String getDefaultValueWithString() {
        String input = defaultValue;
        if (input == null) {
            return null;
        }

        // BIT类型处理：1->true, 0->false
        if (this.getType().equalsIgnoreCase("bit")) {
            if (input.equals("1")) {
                return "true";
            } else if (input.equals("0")) {
                return "false";
            }
        }

        // VARCHAR类型处理：为纯字符串值添加引号
        if (this.getType().equalsIgnoreCase("varchar")
                && !input.contains("::")          // 排除类型转换表达式
                && !input.endsWith("()")) {       // 排除函数调用
            return "'" + input + "'";
        }

        return input;
    }

    public Object getDefaultValue() {
        String input = defaultValue;
        if (input == null) {
            return null;
        }

        String type = this.getType().toLowerCase();

        switch (type) {
            case "bit":
                return input.equals("1");
            case "bool":
                return Boolean.parseBoolean(input);
            case "float":
            case "double":
                return Double.parseDouble(input);
            case "decimal":
            case "numeric":
                return new java.math.BigDecimal(input);
            case "date":
                return java.sql.Date.valueOf(input);
            case "time":
                return java.sql.Time.valueOf(input);
        }

        // 处理所有int类型（包括tinyint, smallint, mediumint, bigint等）
        if (type.equals("int") || type.startsWith("int") || type.endsWith("int")) {
            return Integer.parseInt(input);
        }

        // 默认处理
        Matcher matcher = SINGLE_QUOTE_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return input;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isSequence() {
        return sequence;
    }

    /**
     * 设置为序列字段
     */
    public void setSequence() {
        this.sequence = true;
    }

    /**
     * 设置为可空字段
     */
    public void setNullable() {
        this.nullable = true;
    }

    /**
     * 设置为主键字段
     * 主键字段自动设置为非空
     */
    public void setPrimaryKey() {
        this.primaryKey = true;
    }

    /**
     * 获取显示标签
     * 优先返回描述信息，无描述时返回列名
     *
     * @return 显示用的标签文本
     */
    public String getLabel() {
        if (getDescription() != null) {
            return getDescription();
        }
        return getName();
    }

    public Integer getLength() {
        return length;
    }

    /**
     * 设置字段长度
     * 过滤掉无效的长度值（如Integer.MAX_VALUE）
     *
     * @param length 字段长度
     */
    public void setLength(Integer length) {
        if (Integer.MAX_VALUE != length) { // 过滤数据库返回的无效长度值
            this.length = length;
        }
    }

    /**
     * 转换为构建器Column对象
     * 用于将元数据转换为可构建的列定义
     *
     * @return 构建器Column实例
     */
    public Column toColumn() {
        Column column = Column.of(this.getName());
        column.setComment(this.getDescription());
        column.setType(ColumnType.valueOf(this.getType().toUpperCase()));
        column.setDefaultValueAny(this.getDefaultValueWithString());
        column.setNullable(this.isNullable());
        column.setPrimaryKey(this.isPrimaryKey());
        column.setSequence(this.isSequence());
        column.setLength(this.getLength());
        return column;
    }
}
