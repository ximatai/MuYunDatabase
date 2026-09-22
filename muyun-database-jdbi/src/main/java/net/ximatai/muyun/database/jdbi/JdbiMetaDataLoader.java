package net.ximatai.muyun.database.jdbi;

import net.ximatai.muyun.database.core.IMetaDataLoader;
import net.ximatai.muyun.database.core.exception.MuYunDatabaseException;
import net.ximatai.muyun.database.core.metadata.*;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.IndexSortDirection;

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
    private final Jdbi jdbi;

    public Jdbi getJdbi() {
        return jdbi;
    }

    /**
     * 构造函数
     * 初始化时自动加载数据库元信息
     */
    public JdbiMetaDataLoader(Jdbi jdbi) {
        this.jdbi = jdbi;
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
                    Map<String, String> tableComments = Map.of();

                    // 根据不同数据库类型设置参数
                    if (info.getDatabaseType().equals(DBInfo.Type.MYSQL)) {
                        catalog = schema.getName();
                        tableComments = loadMySqlTableComments(handle, schema.getName());
                    } else {
                        schemaPattern = schema.getName();
                    }

                    // 获取表列表
                    try (ResultSet tablesRs = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                        while (tablesRs.next()) {
                            String tableName = tablesRs.getString("TABLE_NAME");
                            String schemaName = schema.getName();
                            DBTable table = new DBTable(this)
                                    .setName(tableName)
                                    .setSchema(schemaName)
                                    .setDescription(tableComments.getOrDefault(tableName, tablesRs.getString("REMARKS")));
                            info.getSchema(schemaName).addTable(table);
                        }
                    }
                }

                return info;
            } catch (Exception e) {
                throw new MuYunDatabaseException(e.getMessage(), READ_METADATA_ERROR, e);
            }
        });
    }

    @Override
    public DBInfo getDBInfo() {
        if (info == null) {
            initInfo();
        }
        return info;
    }

    public void resetInfo(){
        info = null;
    }

    private Map<String, String> loadMySqlTableComments(Handle handle, String schema) {
        return handle.createQuery("""
                        select TABLE_NAME, TABLE_COMMENT
                        from information_schema.TABLES
                        where TABLE_SCHEMA = :schema
                        """)
                .bind("schema", schema)
                .reduceRows(new HashMap<>(), (map, rowView) -> {
                    map.put(rowView.getColumn("TABLE_NAME", String.class), rowView.getColumn("TABLE_COMMENT", String.class));
                    return map;
                });
    }

    @Override
    public List<DBIndex> getIndexList(String schema, String table) {
        Map<String, DBIndex> indexesByName = new LinkedHashMap<>();
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
                Set<String> constraintOwnedIndexes = loadConstraintOwnedIndexNames(handle, schema, table);

                // 获取索引信息
                try (ResultSet rs = metaData.getIndexInfo(catalog, schemaPattern, table, false, false)) {
                    while (rs.next()) {
                        String indexName = rs.getString("INDEX_NAME");
                        if (indexName == null) {
                            continue;
                        }
                        if (constraintOwnedIndexes.contains(indexName)) {
                            continue;
                        }

                        String columnName = rs.getString("COLUMN_NAME");
                        if (columnName == null) {
                            continue;
                        }
                        String ascOrDesc = rs.getString("ASC_OR_DESC");
                        IndexSortDirection direction = "D".equalsIgnoreCase(ascOrDesc)
                                ? IndexSortDirection.DESC
                                : IndexSortDirection.ASC;
                        int ordinal = rs.getInt("ORDINAL_POSITION");
                        String predicate = null;
                        try {
                            predicate = rs.getString("FILTER_CONDITION");
                        } catch (SQLException ignored) {
                            // Optional JDBC metadata column.
                        }
                        DBIndex index = indexesByName.computeIfAbsent(indexName,
                                name -> new DBIndex().setName(name));
                        index.addColumn(columnName, direction, ordinal).setPredicate(predicate);
                        index.setUnique(!rs.getBoolean("NON_UNIQUE"));
                    }
                }
                if ("PostgreSQL".equalsIgnoreCase(info.getTypeName())) {
                    Map<String, String> predicates = handle.createQuery("""
                                    select idx.relname as index_name,
                                           pg_get_expr(i.indpred, i.indrelid) as predicate
                                    from pg_index i
                                    join pg_class tbl on tbl.oid = i.indrelid
                                    join pg_class idx on idx.oid = i.indexrelid
                                    join pg_namespace ns on ns.oid = tbl.relnamespace
                                    where ns.nspname = :schema and tbl.relname = :table
                                    """)
                            .bind("schema", schema)
                            .bind("table", table)
                            .reduceRows(new HashMap<>(), (map, row) -> {
                                map.put(row.getColumn("index_name", String.class), row.getColumn("predicate", String.class));
                                return map;
                            });
                    indexesByName.values().forEach(index -> index.setPredicate(predicates.get(index.getName())));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        return List.copyOf(indexesByName.values());
    }

    private Set<String> loadConstraintOwnedIndexNames(Handle handle, String schema, String table) {
        if (info.getDatabaseType() == DBInfo.Type.MYSQL) {
            // MySQL does not distinguish a UNIQUE constraint from its UNIQUE index.
            // PRIMARY is the only index that is always owned by a table constraint.
            return new HashSet<>(handle.createQuery("""
                            select constraint_name
                            from information_schema.table_constraints
                            where table_schema = :schema
                              and table_name = :table
                              and constraint_type = 'PRIMARY KEY'
                            """)
                    .bind("schema", schema)
                    .bind("table", table)
                    .mapTo(String.class)
                    .list());
        }
        if ("PostgreSQL".equalsIgnoreCase(info.getTypeName())) {
            return new HashSet<>(handle.createQuery("""
                            select idx.relname
                            from pg_constraint constraint_def
                            join pg_class tbl on tbl.oid = constraint_def.conrelid
                            join pg_namespace ns on ns.oid = tbl.relnamespace
                            join pg_class idx on idx.oid = constraint_def.conindid
                            where ns.nspname = :schema
                              and tbl.relname = :table
                              and constraint_def.contype in ('p', 'u', 'x')
                            """)
                    .bind("schema", schema)
                    .bind("table", table)
                    .mapTo(String.class)
                    .list());
        }
        return Set.of();
    }

    @Override
    public DBPrimaryKey getPrimaryKey(String schema, String table) {
        return jdbi.withHandle(handle -> {
            try {
                DatabaseMetaData metaData = handle.getConnection().getMetaData();
                String catalog = info.getDatabaseType().equals(DBInfo.Type.MYSQL) ? schema : null;
                String schemaPattern = info.getDatabaseType().equals(DBInfo.Type.MYSQL) ? null : schema;
                String name = null;
                TreeMap<Integer, String> columns = new TreeMap<>();
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, schemaPattern, table)) {
                    while (rs.next()) {
                        name = rs.getString("PK_NAME");
                        columns.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
                    }
                }
                return columns.isEmpty() ? null : new DBPrimaryKey(name, new ArrayList<>(columns.values()));
            } catch (SQLException e) {
                throw new MuYunDatabaseException(e.getMessage(), READ_METADATA_ERROR, e);
            }
        });
    }

    @Override
    public List<DBUniqueConstraint> getUniqueConstraints(String schema, String table) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        select tc.constraint_name, kcu.column_name, kcu.ordinal_position
                        from information_schema.table_constraints tc
                        join information_schema.key_column_usage kcu
                          on tc.constraint_catalog = kcu.constraint_catalog
                         and tc.constraint_schema = kcu.constraint_schema
                         and tc.constraint_name = kcu.constraint_name
                         and tc.table_schema = kcu.table_schema
                         and tc.table_name = kcu.table_name
                        where tc.constraint_type = 'UNIQUE'
                          and tc.table_schema = :schema
                          and tc.table_name = :table
                        order by tc.constraint_name, kcu.ordinal_position
                        """)
                .bind("schema", schema)
                .bind("table", table)
                .reduceRows(new LinkedHashMap<String, List<String>>(), (constraints, row) -> {
                    constraints.computeIfAbsent(row.getColumn("constraint_name", String.class), ignored -> new ArrayList<>())
                            .add(row.getColumn("column_name", String.class));
                    return constraints;
                }).entrySet().stream()
                .map(entry -> new DBUniqueConstraint(entry.getKey(), entry.getValue()))
                .toList());
    }

    @Override
    public List<DBForeignKey> getForeignKeys(String schema, String table) {
        return jdbi.withHandle(handle -> {
            try {
                DatabaseMetaData metaData = handle.getConnection().getMetaData();
                String catalog = info.getDatabaseType().equals(DBInfo.Type.MYSQL) ? schema : null;
                String schemaPattern = info.getDatabaseType().equals(DBInfo.Type.MYSQL) ? null : schema;
                Map<String, ForeignKeyAccumulator> foreignKeys = new LinkedHashMap<>();
                try (ResultSet rs = metaData.getImportedKeys(catalog, schemaPattern, table)) {
                    while (rs.next()) {
                        String name = rs.getString("FK_NAME");
                        ForeignKeyAccumulator accumulator = foreignKeys.computeIfAbsent(name, ignored ->
                                new ForeignKeyAccumulator(
                                        name,
                                        firstNonBlank(rsString(rs, "PKTABLE_SCHEM"), rsString(rs, "PKTABLE_CAT")),
                                        rsString(rs, "PKTABLE_NAME"),
                                        foreignKeyAction(rsShort(rs, "DELETE_RULE"))
                                ));
                        accumulator.add(rs.getInt("KEY_SEQ"), rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME"));
                    }
                }
                return foreignKeys.values().stream().map(ForeignKeyAccumulator::build).toList();
            } catch (SQLException e) {
                throw new MuYunDatabaseException(e.getMessage(), READ_METADATA_ERROR, e);
            }
        });
    }

    private static String rsString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static short rsShort(ResultSet rs, String column) {
        try {
            return rs.getShort(column);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static ForeignKeyAction foreignKeyAction(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> ForeignKeyAction.CASCADE;
            case DatabaseMetaData.importedKeySetNull -> ForeignKeyAction.SET_NULL;
            case DatabaseMetaData.importedKeySetDefault -> ForeignKeyAction.SET_DEFAULT;
            case DatabaseMetaData.importedKeyRestrict -> ForeignKeyAction.RESTRICT;
            default -> ForeignKeyAction.NO_ACTION;
        };
    }

    private static final class ForeignKeyAccumulator {
        private final String name;
        private final String referencedSchema;
        private final String referencedTable;
        private final ForeignKeyAction onDelete;
        private final TreeMap<Integer, String> columns = new TreeMap<>();
        private final TreeMap<Integer, String> referencedColumns = new TreeMap<>();

        private ForeignKeyAccumulator(String name, String referencedSchema, String referencedTable, ForeignKeyAction onDelete) {
            this.name = name;
            this.referencedSchema = referencedSchema;
            this.referencedTable = referencedTable;
            this.onDelete = onDelete;
        }

        private void add(int sequence, String column, String referencedColumn) {
            columns.put(sequence, column);
            referencedColumns.put(sequence, referencedColumn);
        }

        private DBForeignKey build() {
            return new DBForeignKey(name, new ArrayList<>(columns.values()), referencedSchema, referencedTable,
                    new ArrayList<>(referencedColumns.values()), onDelete);
        }
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
                Map<String, String> columnComments = info.getDatabaseType().equals(DBInfo.Type.MYSQL)
                        ? loadMySqlColumnComments(handle, schema, table)
                        : Map.of();

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

                        column.setDescription(columnComments.getOrDefault(column.getName(), rs.getString("REMARKS")));
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

    private Map<String, String> loadMySqlColumnComments(Handle handle, String schema, String table) {
        return handle.createQuery("""
                        select COLUMN_NAME, COLUMN_COMMENT
                        from information_schema.COLUMNS
                        where TABLE_SCHEMA = :schema and TABLE_NAME = :table
                        """)
                .bind("schema", schema)
                .bind("table", table)
                .reduceRows(new HashMap<>(), (map, rowView) -> {
                    map.put(rowView.getColumn("COLUMN_NAME", String.class), rowView.getColumn("COLUMN_COMMENT", String.class));
                    return map;
                });
    }
}
