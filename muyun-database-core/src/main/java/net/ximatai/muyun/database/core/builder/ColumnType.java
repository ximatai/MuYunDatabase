package net.ximatai.muyun.database.core.builder;

/**
 * 数据库列类型枚举
 * 定义支持的数据库字段数据类型，用于表结构构建和类型映射
 * 提供基于列名的自动类型推断功能，遵循命名约定简化配置
 */
public enum ColumnType {
    /**
     * 未知类型 - 需要显式指定或根据上下文推断
     * 当自动推断无法确定类型时使用
     */
    UNKNOWN,

    /**
     * 可变长度字符串类型
     * 适用于：姓名、标题、描述等文本数据
     * 对应数据库：VARCHAR(n)
     */
    VARCHAR,

    /**
     * 长文本类型
     * 适用于：文章内容、详细描述等大段文本
     * 对应数据库：TEXT, LONGTEXT等
     */
    TEXT,

    /**
     * 整数类型（32位）
     * 适用于：数量、状态码、小范围ID等
     * 对应数据库：INT, INTEGER
     */
    INT,

    /**
     * 长整数类型（64位）
     * 适用于：大数值ID、时间戳、大数据量计数
     * 对应数据库：BIGINT
     */
    BIGINT,

    /**
     * 布尔类型
     * 适用于：是否标志、开关状态、真假值
     * 对应数据库：BOOLEAN, TINYINT(1), BIT
     */
    BOOLEAN,

    /**
     * 时间戳类型（日期时间）
     * 适用于：创建时间、更新时间、业务时间点
     * 对应数据库：TIMESTAMP, DATETIME
     */
    TIMESTAMP,

    /**
     * 日期类型（不含时间）
     * 适用于：生日、开始日期、结束日期等纯日期
     * 对应数据库：DATE
     */
    DATE,

    /**
     * 高精度数值类型
     * 适用于：金额、百分比、科学计算等需要精确小数的场景
     * 对应数据库：DECIMAL, NUMERIC
     */
    NUMERIC,

    /**
     * JSON数据类型
     * 适用于：结构化扩展字段、配置信息、动态属性
     * 对应数据库：JSON, JSONB (PostgreSQL)
     */
    JSON,

    /**
     * 字符串集合类型（CSV存储）
     * 适用于：ID集合、标签集合等去重字符串集合
     * 对应数据库：TEXT (以逗号分隔存储)
     */
    SET,

    /**
     * 字符串数组类型
     * 适用于：标签、分类、多选值等字符串集合
     * 对应数据库：VARCHAR[], TEXT[] (PostgreSQL)
     */
    VARCHAR_ARRAY,

    /**
     * 整数数组类型
     * 适用于：ID集合、序号列表、数值型多选
     * 对应数据库：INT[], BIGINT[] (PostgreSQL)
     */
    INT_ARRAY;

    /**
     * 基于列名自动推断数据类型
     * 根据命名约定智能推测最合适的列类型，减少手动配置
     * <p>
     * 命名约定规则：
     * - 以特定前缀标识类型：v_(字符串), i_(整数), t_(时间戳)等
     * - 常见业务字段特殊处理：id, pid等默认为字符串
     * - 支持数组类型：files_, ids_, dicts_等前缀识别为数组
     * <p>
     * 使用场景：
     * 1. 数据库迁移时自动推断字段类型
     * 2. 快速原型开发减少配置
     * 3. 遵循命名规范的团队协作
     * <p>
     * 推断规则优先级（从上到下匹配）：
     *
     * @param name 数据库列名，支持大小写（内部转换为小写比较）
     * @return 推断的ColumnType，如果无法推断返回UNKNOWN
     * <p>
     * 示例：
     * "id"            → VARCHAR      (ID字段)
     * "create_time"   → TIMESTAMP    (时间字段)
     * "v_name"        → VARCHAR      (v_前缀字符串)
     * "i_age"         → INT          (i_前缀整数)
     * "files"         → VARCHAR_ARRAY(文件数组)
     * "unknown_field" → UNKNOWN      (无法推断)
     */
    public static ColumnType autoTypeWithColumnName(String name) {
        // 统一转换为小写处理，避免大小写敏感问题
        String lowerName = name.toLowerCase();

        // 特殊字段名称处理（高优先级）
        if ("id".equals(lowerName) || "pid".equals(lowerName)) {
            return ColumnType.VARCHAR;  // ID类字段默认为字符串，支持UUID等格式
        }

        // 前缀匹配规则（按业务含义分组）

        // 字符串类型前缀
        if (lowerName.startsWith("v_")) {          // 可变字符串
            return ColumnType.VARCHAR;
        } else if (lowerName.startsWith("id_")) { // 关联ID
            return ColumnType.VARCHAR;
        } else if (lowerName.startsWith("dict_")) { // 字典编码
            return ColumnType.VARCHAR;
        } else if (lowerName.startsWith("file_")) { // 文件路径/名称
            return ColumnType.VARCHAR;
        }

        // 数值类型前缀
        else if (lowerName.startsWith("i_")) {     // 整数值
            return ColumnType.INT;
        } else if (lowerName.startsWith("n_")) {   // 数值型（高精度）
            return ColumnType.NUMERIC;
        }

        // 布尔类型前缀
        else if (lowerName.startsWith("b_")) {     // 布尔标志
            return ColumnType.BOOLEAN;
        }

        // 时间类型前缀
        else if (lowerName.startsWith("t_")) {     // 时间戳
            return ColumnType.TIMESTAMP;
        } else if (lowerName.startsWith("d_")) {   // 日期
            return ColumnType.DATE;
        }

        // JSON类型前缀
        else if (lowerName.startsWith("j_")) {     // JSON格式数据
            return ColumnType.JSON;
        }

        // 数组类型前缀（多值字段）
        else if (lowerName.startsWith("files_")) {  // 文件数组
            return ColumnType.VARCHAR_ARRAY;
        } else if (lowerName.startsWith("ids_")) {  // ID数组
            return ColumnType.VARCHAR_ARRAY;
        } else if (lowerName.startsWith("dicts_")) { // 字典数组
            return ColumnType.VARCHAR_ARRAY;
        }

        // 无法识别的列名返回UNKNOWN，需要显式指定类型
        return ColumnType.UNKNOWN;
    }
}
