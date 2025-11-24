package net.ximatai.muyun.database.core.builder;

import java.math.BigDecimal;

/**
 * 数据库列定义类
 * 用于定义数据库表的结构信息，支持链式调用设置列属性
 */
public class Column {
    // 基础属性
    private String name;           // 列名
    private String comment;        // 列注释/描述
    private ColumnType type;       // 列数据类型
    private String defaultValue;   // 默认值

    // 数值类型相关属性
    private Integer length;        // 长度限制（适用于字符串类型）
    private Integer precision;     // 精度（适用于数值类型，总位数）
    private Integer scale;         // 小数位数（适用于数值类型）

    // 约束属性
    private boolean nullable = true;   // 是否允许为空，默认为true
    private boolean unique = false;    // 是否唯一约束，默认为false
    private boolean primaryKey = false;// 是否为主键，默认为false

    // 序列和索引属性
    private boolean sequence = false;  // 是否使用序列，默认为false
    private boolean indexed = false;   // 是否创建索引，默认为false

    /**
     * 构造函数：创建一个列定义
     * 自动根据列名推断数据类型（通过ColumnType.autoTypeWithColumnName方法）
     *
     * @param name 列名，不能为null或空字符串
     * @throws IllegalArgumentException 如果列名为null或空
     */
    public Column(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("列名不能为空");
        }
        this.name = name.trim();
        this.type = ColumnType.autoTypeWithColumnName(name);
    }

    /**
     * 静态工厂方法：创建列定义的便捷方式
     * 示例：Column id = Column.of("id");
     *
     * @param name 列名
     * @return 新的Column实例
     */
    public static Column of(String name) {
        return new Column(name);
    }

    /**
     * 设置列注释
     *
     * @param comment 列的描述性注释
     * @return 当前Column实例，支持链式调用
     */
    public Column setComment(String comment) {
        this.comment = comment;
        return this;
    }

    /**
     * 设置列数据类型
     *
     * @param type 列数据类型枚举
     * @return 当前Column实例
     */
    public Column setType(ColumnType type) {
        this.type = type;
        return this;
    }

    /**
     * 设置列默认值
     *
     * @param defaultValue 默认值字符串表示
     * @return 当前Column实例
     */
    public Column setDefaultValueAny(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public Column setDefaultValue(String defaultValue) {
        this.defaultValue = "'%s'".formatted(defaultValue);
        return this;
    }

    public Column setDefaultValue(boolean defaultValue) {
        this.defaultValue = Boolean.toString(defaultValue);
        return this;
    }

    public Column setDefaultValue(int defaultValue) {
        this.defaultValue = "%s".formatted(defaultValue);
        return this;
    }

    public Column setDefaultValue(double defaultValue) {
        this.defaultValue = "%s".formatted(defaultValue);
        return this;
    }

    public Column setDefaultValue(float defaultValue) {
        this.defaultValue = "%s".formatted(defaultValue);
        return this;
    }

    public Column setDefaultValue(BigDecimal defaultValue) {
        this.defaultValue = defaultValue.toString();
        return this;
    }

    public Column setDefaultValue(java.sql.Date defaultValue) {
        this.defaultValue = defaultValue.toString();
        return this;
    }

    public Column setDefaultValue(java.sql.Time defaultValue) {
        this.defaultValue = defaultValue.toString();
        return this;
    }

    public Column setDefaultValue(java.sql.Timestamp defaultValue) {
        this.defaultValue = defaultValue.toString();
        return this;
    }

    /**
     * 设置是否允许为空
     *
     * @param nullable true允许为空，false不允许为空
     * @return 当前Column实例
     */
    public Column setNullable(boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    /**
     * 设置唯一约束
     *
     * @param unique true添加唯一约束，false不添加
     * @return 当前Column实例
     */
    public Column setUnique(boolean unique) {
        this.unique = unique;
        return this;
    }

    /**
     * 设置是否为主键
     *
     * @param primaryKey true设置为主键，false不设置
     * @return 当前Column实例
     */
    public Column setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
        // 主键自动设置为非空
        if (primaryKey) {
            this.nullable = false;
        }
        return this;
    }

    /**
     * 设置是否使用序列（适用于自增主键等场景）
     *
     * @param sequence true使用序列，false不使用
     * @return 当前Column实例
     */
    public Column setSequence(boolean sequence) {
        this.sequence = sequence;
        return this;
    }

    /**
     * 设置是否创建索引
     *
     * @param indexed true创建索引，false不创建
     * @return 当前Column实例
     */
    public Column setIndexed(boolean indexed) {
        this.indexed = indexed;
        return this;
    }

    /**
     * 便捷方法：设置允许为空
     * 等同于 setNullable(true)
     *
     * @return 当前Column实例
     */
    public Column setNullable() {
        this.nullable = true;
        return this;
    }

    /**
     * 便捷方法：设置唯一约束
     * 等同于 setUnique(true)
     *
     * @return 当前Column实例
     */
    public Column setUnique() {
        this.unique = true;
        return this;
    }

    /**
     * 便捷方法：设置为主键
     * 自动设置primaryKey=true，nullable=false
     *
     * @return 当前Column实例
     */
    public Column setPrimaryKey() {
        this.primaryKey = true;
        this.nullable = false; // 主键不能为空
        return this;
    }

    /**
     * 便捷方法：使用序列
     * 等同于 setSequence(true)
     *
     * @return 当前Column实例
     */
    public Column setSequence() {
        this.sequence = true;
        return this;
    }

    /**
     * 便捷方法：创建索引
     * 等同于 setIndexed(true)
     *
     * @return 当前Column实例
     */
    public Column setIndexed() {
        this.indexed = true;
        return this;
    }

    // ============ Getter方法 ============

    /**
     * 获取精度（数值类型的总位数）
     *
     * @return 精度值，可能为null
     */
    public Integer getPrecision() {
        return precision;
    }

    /**
     * 设置精度
     *
     * @param precision 精度值，大于0的整数
     * @return 当前Column实例
     */
    public Column setPrecision(Integer precision) {
        this.precision = precision;
        return this;
    }

    /**
     * 获取小数位数
     *
     * @return 小数位数，可能为null
     */
    public Integer getScale() {
        return scale;
    }

    /**
     * 设置小数位数
     *
     * @param scale 小数位数，大于等于0的整数
     * @return 当前Column实例
     */
    public Column setScale(Integer scale) {
        this.scale = scale;
        return this;
    }

    /**
     * 获取列名
     *
     * @return 列名字符串
     */
    public String getName() {
        return name;
    }

    /**
     * 获取列注释
     *
     * @return 注释字符串，可能为null
     */
    public String getComment() {
        return comment;
    }

    /**
     * 获取列数据类型
     *
     * @return ColumnType枚举实例
     */
    public ColumnType getType() {
        return type;
    }

    /**
     * 获取默认值
     *
     * @return 默认值字符串表示，可能为null
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * 检查是否允许为空
     *
     * @return true允许为空，false不允许
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * 检查是否有唯一约束
     *
     * @return true有唯一约束，false没有
     */
    public boolean isUnique() {
        return unique;
    }

    /**
     * 检查是否为主键
     *
     * @return true是主键，false不是
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * 检查是否使用序列
     *
     * @return true使用序列，false不使用
     */
    public boolean isSequence() {
        return sequence;
    }

    /**
     * 检查是否有索引
     *
     * @return true有索引，false没有
     */
    public boolean isIndexed() {
        return indexed;
    }

    /**
     * 获取长度限制
     *
     * @return 长度值，可能为null
     */
    public Integer getLength() {
        return length;
    }

    /**
     * 设置长度限制（主要用于字符串类型）
     *
     * @param length 长度值，大于0的整数
     * @return 当前Column实例
     */
    public Column setLength(Integer length) {
        this.length = length;
        return this;
    }
}
