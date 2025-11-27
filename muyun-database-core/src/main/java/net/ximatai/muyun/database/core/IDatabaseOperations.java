package net.ximatai.muyun.database.core;

import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.metadata.DBTable;

import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库操作接口
 * 定义数据库的CRUD操作和SQL构建方法
 */
public interface IDatabaseOperations<K> {

    /**
     * 获取元数据加载器
     */
    IMetaDataLoader getMetaDataLoader();

    /**
     * 获取数据库信息
     */
    DBInfo getDBInfo();

    /**
     * 获取默认模式名称
     */
    default String getDefaultSchemaName() {
        return getDBInfo().getDefaultSchemaName();
    }

    /**
     * 转换数据为数据库格式
     * 根据表结构对数据进行类型转换
     */
    default Map<String, Object> transformDataForDB(DBTable dbTable, Map<String, Object> data) {
        return data;
    }

    /**
     * 构建插入SQL语句
     *
     * @param schema    模式名
     * @param tableName 表名
     * @param params    参数映射
     * @return 插入SQL语句
     */
    default String buildInsertSql(String schema, String tableName, Map<String, ?> params) {
        DBTable dbTable = getDBInfo().getSchema(schema).getTable(tableName);
        Objects.requireNonNull(dbTable);

        Map<String, DBColumn> columnMap = dbTable.getColumnMap();

        StringJoiner columns = new StringJoiner(", ", "(", ")");
        StringJoiner values = new StringJoiner(", ", "(", ")");
        params.keySet().forEach(key -> {
            if (columnMap.containsKey(key)) {
                columns.add(key);
                values.add(":" + key);
            }
        });

        return "insert into " + schema + "." + tableName + " " + columns + " values " + values;
    }

    /**
     * 构建更新SQL语句
     *
     * @param schema    模式名
     * @param tableName 表名
     * @param params    参数映射
     * @param pkName    主键字段名
     * @return 更新SQL语句
     */
    default String buildUpdateSql(String schema, String tableName, Map<String, Object> params, String pkName) {
        DBTable dbTable = getDBInfo().getSchema(schema).getTable(tableName);
        Objects.requireNonNull(dbTable);

        Map<String, DBColumn> columnMap = dbTable.getColumnMap();

        StringJoiner setClause = new StringJoiner(", ");
        params.keySet().forEach(key -> {
            if (columnMap.containsKey(key)) {
                setClause.add(key + "=:" + key);
            }
        });

        return "update " + schema + "." + tableName + " set " + setClause + " where " + pkName + " = :" + pkName;
    }

    /**
     * 插入单条记录（使用默认模式）
     */
    default K insertItem(String tableName, Map<String, Object> params) {
        return this.insertItem(getDefaultSchemaName(), tableName, params);
    }

    /**
     * 插入单条记录
     * 自动处理ID字段，支持自增ID和手动指定ID
     *
     * @return 插入记录的主键ID
     */
    default K insertItem(String schema, String tableName, Map<String, Object> params) {
        DBTable table = getDBInfo().getSchema(schema).getTable(tableName);
        Map<String, Object> transformed = transformDataForDB(table, params);

        if (params.containsKey("id") || params.containsKey("ID")) {
            this.insert(buildInsertSql(schema, tableName, transformed), transformed);
            return params.get("id") != null ? (K) params.get("id") : (K) params.get("ID");
        } else {
            return this.insert(buildInsertSql(schema, tableName, transformed), transformed, "id");
        }
    }

    /**
     * 批量插入记录（使用默认模式）
     */
    default List<K> insertList(String tableName, List<Map<String, Object>> list) {
        return this.insertList(getDefaultSchemaName(), tableName, list);
    }

    /**
     * 批量插入记录
     *
     * @return 插入记录的主键ID列表
     */
    default List<K> insertList(String schema, String tableName, List<Map<String, Object>> list) {
        Objects.requireNonNull(list, "The list must not be null");
        if (list.isEmpty()) {
            throw new IllegalArgumentException("The list must not be empty");
        }

        DBTable table = getDBInfo().getSchema(schema).getTable(tableName);
        List<Map<String, Object>> transformedList = list.stream().map(it -> transformDataForDB(table, it)).collect(Collectors.toList());

        return this.batchInsert(buildInsertSql(schema, tableName, transformedList.get(0)), transformedList, "id");
    }

    /**
     * 更新记录（使用默认模式）
     */
    default Integer updateItem(String tableName, Map<String, Object> params) {
        return this.updateItem(getDefaultSchemaName(), tableName, params);
    }

    /**
     * 更新记录
     *
     * @return 影响的行数
     */
    default Integer updateItem(String schema, String tableName, Map<String, Object> params) {
        DBTable table = getDBInfo().getSchema(schema).getTable(tableName);
        Map<String, Object> transformed = transformDataForDB(table, params);
        return this.update(buildUpdateSql(schema, tableName, transformed, "id"), transformed);
    }

    /**
     * 删除记录（使用默认模式）
     */
    default Integer deleteItem(String tableName, String id) {
        return this.deleteItem(getDefaultSchemaName(), tableName, id);
    }

    /**
     * 删除记录
     *
     * @return 影响的行数
     */
    default Integer deleteItem(String schema, String tableName, String id) {
        DBTable dbTable = getDBInfo().getSchema(schema).getTable(tableName);
        Objects.requireNonNull(dbTable);

        return this.delete("DELETE FROM " + schema + "." + tableName + " WHERE id=:id", Collections.singletonMap("id", id));
    }

    /**
     * 查询单条记录（使用默认模式）
     */
    default Map<String, Object> getItem(String tableName, String id) {
        return this.getItem(getDefaultSchemaName(), tableName, id);
    }

    /**
     * 查询单条记录
     *
     * @return 记录映射，未找到时返回null
     */
    default Map<String, Object> getItem(String schema, String tableName, String id) {
        DBTable dbTable = getDBInfo().getSchema(schema).getTable(tableName);
        Objects.requireNonNull(dbTable);

        return this.row("SELECT * FROM " + schema + "." + tableName + " WHERE id=:id", Collections.singletonMap("id", id));
    }

    // 基础CRUD操作方法

    /**
     * 插入记录并返回主键
     */
    K insert(String sql, Map<String, Object> params, String pk);

    /**
     * 插入记录
     */
    int insert(String sql, Map<String, Object> params);

    /**
     * 批量插入记录
     */
    List<K> batchInsert(String sql, List<Map<String, Object>> paramsList, String pk);

    /**
     * 查询单行（可变参数）
     */
    default Map<String, Object> row(String sql, Object... params) {
        return this.row(sql, Arrays.stream(params).collect(Collectors.toList()));
    }

    /**
     * 查询单行（列表参数）
     */
    Map<String, Object> row(String sql, List<Object> params);

    /**
     * 查询单行（映射参数）
     */
    Map<String, Object> row(String sql, Map<String, Object> params);

    /**
     * 查询单行（无参数）
     */
    default Map<String, Object> row(String sql) {
        return row(sql, Collections.emptyList());
    }

    /**
     * 查询多行（映射参数）
     */
    List<Map<String, Object>> query(String sql, Map<String, Object> params);

    /**
     * 查询多行（列表参数）
     */
    List<Map<String, Object>> query(String sql, List<Object> params);

    /**
     * 查询多行（可变参数）
     */
    default List<Map<String, Object>> query(String sql, Object... params) {
        return this.query(sql, Arrays.stream(params).collect(Collectors.toList()));
    }

    /**
     * 查询多行（无参数）
     */
    default List<Map<String, Object>> query(String sql) {
        return this.query(sql, Collections.emptyList());
    }

    /**
     * 更新操作（映射参数）
     */
    Integer update(String sql, Map<String, Object> params);

    /**
     * 更新操作（可变参数）
     */
    default Integer update(String sql, Object... params) {
        return this.update(sql, Arrays.stream(params).collect(Collectors.toList()));
    }

    /**
     * 更新操作（列表参数）
     */
    Integer update(String sql, List<Object> params);

    /**
     * 删除操作（映射参数）
     */
    default Integer delete(String sql, Map<String, Object> params) {
        return this.update(sql, params);
    }

    /**
     * 删除操作（可变参数）
     */
    default Integer delete(String sql, Object... params) {
        return this.update(sql, params);
    }

    /**
     * 删除操作（列表参数）
     */
    default Integer delete(String sql, List<?> params) {
        return this.update(sql, params);
    }

    /**
     * 执行SQL语句（无参数）
     */
    Integer execute(String sql);

    /**
     * 执行SQL语句（可变参数）
     */
    Integer execute(String sql, Object... params);

    /**
     * 执行SQL语句（列表参数）
     */
    Integer execute(String sql, List<Object> params);

    /**
     * 创建数据库数组对象
     */
    Array createArray(List<Object> list, String type);
}
