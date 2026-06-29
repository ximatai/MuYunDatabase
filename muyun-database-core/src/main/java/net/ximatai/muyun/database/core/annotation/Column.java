package net.ximatai.muyun.database.core.annotation;

import net.ximatai.muyun.database.core.builder.ColumnType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据库列注解
 * 用于标记实体类字段与数据库表列的映射关系
 * 通过此注解可以定义列的各种属性，如名称、类型、约束等
 * <p>
 * 使用示例：
 * {@code
 * public class User {
 *     @Column(name = "user_id", type = ColumnType.BIGINT, nullable = false, comment = "用户ID")
 *     private Long userId;
 *
 *     @Column(name = "username", type = ColumnType.VARCHAR, length = 50, unique = true, comment = "用户名")
 *     private String username;
 * }
 * }
 */
@Target(ElementType.FIELD)  // 注解只能应用于类的字段上
@Retention(RetentionPolicy.RUNTIME)  // 注解在运行时保留，可通过反射读取
public @interface Column {

    /**
     * 数据库列名
     * 如果为空，则默认使用字段名作为列名
     * 建议显式指定列名以确保数据库命名一致性
     *
     * @return 列名字符串，默认为空字符串（使用字段名）
     */
    String name() default "";

    /**
     * 列的数据类型
     * 用于指定数据库中的列类型，如VARCHAR、INT、DATETIME等
     * 默认为UNKNOWN，系统会根据字段类型自动推断
     *
     * @return 列类型枚举，默认为UNKNOWN
     */
    ColumnType type() default ColumnType.UNKNOWN;

    /**
     * 数组列的元素类型
     * 仅当 type = ColumnType.ARRAY 时使用。默认为UNKNOWN时，系统会尝试从字段元素类型推断。
     *
     * @return 数组元素列类型枚举，默认为UNKNOWN
     */
    ColumnType elementType() default ColumnType.UNKNOWN;

    /**
     * 列长度限制
     * 主要用于字符串类型（VARCHAR、CHAR等）的长度定义
     * 值为0表示使用数据库默认长度或根据类型自动推断
     *
     * @return 长度值，默认为0
     */
    int length() default 0;

    /**
     * 数值精度（总位数）
     * 适用于数值类型（DECIMAL、NUMERIC等）
     * 表示数值的总位数，包括整数部分和小数部分
     * 例如：DECIMAL(10,2)中的10就是精度
     *
     * @return 精度值，默认为0
     */
    int precision() default 0;

    /**
     * 小数位数
     * 适用于数值类型，表示小数点后的位数
     * 例如：DECIMAL(10,2)中的2就是小数位数
     *
     * @return 小数位数，默认为0
     */
    int scale() default 0;

    /**
     * 是否允许空值
     * true：列允许存储NULL值
     * false：列不允许为NULL，即NOT NULL约束
     * 主键字段通常应设置为false
     *
     * @return 是否允许为空，默认为true
     */
    boolean nullable() default true;

    /**
     * 是否添加唯一约束
     * true：为该列添加唯一性约束，确保列中所有值都是唯一的
     * false：不添加唯一约束
     * 常用于用户名、邮箱等需要唯一性的字段
     *
     * @return 是否唯一，默认为false
     */
    boolean unique() default false;

    /**
     * 列注释/描述
     * 用于在数据库中为列添加注释说明，提高可读性
     * 描述列的业务含义、用途或约束条件
     *
     * @return 列注释文本，默认为空字符串
     */
    String comment() default "";

    /**
     * 列默认值定义
     * 使用@Default注解提供更灵活和类型安全的默认值设置
     * 支持各种数据类型的默认值，包括函数调用、表达式等
     * 通过unset属性判断是否设置了默认值
     * <p>
     * 使用示例：
     * {@code
     *
     * @return Default注解实例，默认为未设置状态
     * @Column(defaultVal = @Default(value = "CURRENT_TIMESTAMP"))
     * private Date createTime;
     * @Column(defaultVal = @Default(value = "0"))
     * private Integer status;
     * }
     */
    Default defaultVal() default @Default(unset = true);
}
