package net.ximatai.muyun.database.core.metadata;

import net.ximatai.muyun.database.core.builder.IndexSortDirection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 数据库索引元数据类
 * 封装从数据库读取的索引信息，支持链式调用
 */
public class DBIndex {
    private String name;                    // 索引名称
    private boolean unique = false;         // 是否唯一索引
    private final List<String> columns = new ArrayList<>();  // 索引包含的列名列表
    private final List<DBIndexColumn> indexColumns = new ArrayList<>();
    private String predicate;

    public String getName() {
        return name;
    }

    /**
     * 设置索引名称
     *
     * @param name 索引名称
     * @return 当前DBIndex实例，支持链式调用
     */
    public DBIndex setName(String name) {
        this.name = name;
        return this;
    }

    public boolean isUnique() {
        return unique;
    }

    /**
     * 设置是否唯一索引
     *
     * @param unique true为唯一索引，false为普通索引
     * @return 当前DBIndex实例
     */
    public DBIndex setUnique(boolean unique) {
        this.unique = unique;
        return this;
    }

    /**
     * 获取索引包含的列列表
     *
     * @return 列名字符串列表
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * 添加索引列
     *
     * @param column 列名
     * @return 当前DBIndex实例
     */
    public DBIndex addColumn(String column) {
        return addColumn(column, IndexSortDirection.ASC, indexColumns.size() + 1);
    }

    public DBIndex addColumn(String column, IndexSortDirection direction, int ordinal) {
        this.indexColumns.add(new DBIndexColumn(column, direction, ordinal));
        this.indexColumns.sort(Comparator.comparingInt(DBIndexColumn::ordinal));
        this.columns.clear();
        this.indexColumns.stream().map(DBIndexColumn::name).forEach(this.columns::add);
        return this;
    }

    public List<DBIndexColumn> getIndexColumns() {
        return List.copyOf(indexColumns);
    }

    public String getPredicate() {
        return predicate;
    }

    public DBIndex setPredicate(String predicate) {
        this.predicate = predicate == null || predicate.isBlank() ? null : predicate.trim();
        return this;
    }

    /**
     * 检查是否为复合索引（多列索引）
     *
     * @return true表示包含多个列，false为单列索引
     */
    public boolean isMulti() {
        return columns.size() > 1;
    }
}
