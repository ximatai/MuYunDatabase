package net.ximatai.muyun.database.jdbi;

import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.exception.MuYunDatabaseException;
import net.ximatai.muyun.database.core.metadata.*;
import org.jdbi.v3.core.Jdbi;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static net.ximatai.muyun.database.core.exception.MuYunDatabaseException.Type.READ_METADATA_ERROR;

/**
 * JDBI元数据加载器
 * 基于JDBC DatabaseMetaData实现数据库元信息读取
 */
public class JdbiMetaDataLoader implements IMetaDataLoader {

    private DBInfo info;
    private Jdbi jdbi;

    public Jdbi getJdbi() {
        return jdbi;
    }

    /**
     * 构造函数
     * 初始化时自动加载数据库元信息
     */
    public JdbiMetaDataLoader(Jdbi jdbi) {
        this.jdbi = jdbi;
        initInfo();
    }

    /**
     * 初始化数据库信息
     * 加载数据库类型、模式、表结构等元数据
     */
    private void initInfo() {
        info = getJdbi().withHandle(handle -> {
            Connection connection = handle.getConnection();
            try {
                DatabaseMetaData metaData = connection.getMetaData();

                // 创建数据库信息对象
                DBInfo info = new DBInfo(metaData.getDatabaseProductName());
                String databaseName = connection.getCatalog();
                info.setName(databaseName);

                // 加载模式信息（MySQL和PostgreSQL处理方式不同）
                if (info.getDatabaseType().equals(DBInfo.Type.MYSQL)) {
                    // MySQL：通过show databases获取数据库列表
                    handle.createQuery("show databases;")
                            .mapTo(String.class)  // 直接将第一列映射为 String，忽略列名
                            .list()               // 立即执行查询并关闭资源，返回 List<String>
                            .forEach(dbName -> info.addSchema(new DBSchema(dbName)));
                } else {
                    // PostgreSQL：通过JDBC元数据获取模式列表
                    try (ResultSet schemasRs = metaData.getSchemas()) {
                        boolean flag = false;
                        while (schemasRs.next()) {
                            flag = true;
                            info.addSchema(new DBSchema(schemasRs.getString("TABLE_SCHEM")));
                        }

                        // 如果没有模式，使用数据库名作为默认模式
                        if (!flag) {
                            info.addSchema(new DBSchema(databaseName));
                        }
                    }
                }

                // 加载每个模式下的表信息
                for (DBSchema schema : info.getSchemas()) {
                    String catalog = null;
                    String schemaPattern = null;

                    // 根据不同数据库类型设置参数
                    if (info.getDatabaseType().equals(DBInfo.Type.MYSQL)) {
                        catalog = schema.getName();
                    } else {
                        schemaPattern = schema.getName();
                    }

                    // 获取表列表
                    try (ResultSet tablesRs = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                        while (tablesRs.next()) {
                            String tableName = tablesRs.getString("TABLE_NAME");
                            String schemaName = schema.getName();
                            DBTable table = new DBTable(this).setName(tableName).setSchema(schemaName);
                            info.getSchema(schemaName).addTable(table);
                        }
                    }
                }

                return info;
            } catch (Exception e) {
                e.printStackTrace();
                throw new MuYunDatabaseException(e.getMessage(), READ_METADATA_ERROR);
            }
        });
    }

    @Override
    public DBInfo getDBInfo() {
        return info;
    }

    @Override
    public List<DBIndex> getIndexList(String schema, String table) {
        List<DBIndex> indexList = new ArrayList<>();
        jdbi.useHandle(handle -> {
            Connection connection = handle.getConnection();
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                String catalog = null;
                String schemaPattern = null;

                // 设置数据库特定的参数
                if (info.getDatabaseType().equals(DBInfo.Type.MYSQL)) {
                    catalog = schema;
                } else {
                    schemaPattern = schema;
                }

                // 获取索引信息
                try (ResultSet rs = metaData.getIndexInfo(catalog, schemaPattern, table, false, false)) {
                    while (rs.next()) {
                        String indexName = rs.getString("INDEX_NAME");
                        // 跳过主键索引
                        if (indexName.endsWith("_pkey") || indexName.equalsIgnoreCase("PRIMARY")) {
                            continue;
                        }

                        String columnName = rs.getString("COLUMN_NAME");
                        // 查找或创建索引对象
                        Optional<DBIndex> hitIndex = indexList.stream()
                                .filter(i -> i.getName().equals(indexName))
                                .findFirst();

                        if (hitIndex.isPresent()) {
                            hitIndex.get().addColumn(columnName);
                        } else {
                            DBIndex index = new DBIndex();
                            index.setName(indexName);
                            index.addColumn(columnName);
                            // 设置唯一性约束
                            if (!rs.getBoolean("NON_UNIQUE")) {
                                index.setUnique(true);
                            }
                            indexList.add(index);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        return indexList;
    }

    @Override
    public Map<String, DBColumn> getColumnMap(String schema, String table) {
        // 使用不区分大小写的TreeMap存储列信息
        Map<String, DBColumn> columnMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        getJdbi().useHandle(handle -> {
            Connection connection = handle.getConnection();
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                String catalog = null;
                String schemaPattern = null;

                if (info.getDatabaseType().equals(DBInfo.Type.MYSQL)) {
                    catalog = schema;
                } else {
                    schemaPattern = schema;
                }

                // 获取列基本信息
                try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, table, null)) {
                    while (rs.next()) {
                        DBColumn column = new DBColumn();
                        column.setName(rs.getString("COLUMN_NAME"));
                        column.setType(rs.getString("TYPE_NAME"));
                        column.setLength(rs.getInt("COLUMN_SIZE"));
                        column.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);

                        String defaultValue = rs.getString("COLUMN_DEF");
                        column.setDefaultValue(defaultValue);

                        // 判断是否为自增序列
                        if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
                            column.setSequence();
                        }
                        if (defaultValue != null && defaultValue.startsWith("nextval(")) {
                            column.setSequence();
                        }
                        // MySQL自增字段特殊处理
                        if (column.isSequence() && info.getDatabaseType().equals(DBInfo.Type.MYSQL) && defaultValue == null) {
                            column.setDefaultValue("AUTO_INCREMENT");
                        }

                        column.setDescription(rs.getString("REMARKS"));
                        columnMap.put(column.getName(), column);
                    }
                }

                // 获取主键信息
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, schemaPattern, table)) {
                    while (rs.next()) {
                        String primaryKeyColumn = rs.getString("COLUMN_NAME");
                        DBColumn column = columnMap.get(primaryKeyColumn);
                        if (column != null) {
                            column.setPrimaryKey(true);
                        }
                    }
                }

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        return columnMap;
    }
}
