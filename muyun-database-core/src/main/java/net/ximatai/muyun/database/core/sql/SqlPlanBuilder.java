package net.ximatai.muyun.database.core.sql;

import net.ximatai.muyun.database.core.builder.sql.SchemaBuildRules;
import net.ximatai.muyun.database.core.exception.MuYunDatabaseException;
import net.ximatai.muyun.database.core.metadata.DBColumn;
import net.ximatai.muyun.database.core.metadata.DBInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

public final class SqlPlanBuilder {

    private SqlPlanBuilder() {
    }

    public static String buildInsertSql(String schema,
                                        String tableName,
                                        Map<String, ?> params,
                                        Map<String, DBColumn> columnMap,
                                        DBInfo.Type dbType) {
        StringJoiner columns = new StringJoiner(", ", "(", ")");
        StringJoiner values = new StringJoiner(", ", "(", ")");
        params.keySet().forEach(key -> {
            if (columnMap.containsKey(key)) {
                columns.add(quoteIdentifier(key, dbType));
                values.add(":" + key);
            }
        });
        return "insert into " + quoteSchemaTable(schema, tableName, dbType) + " " + columns + " values " + values;
    }

    public static String buildUpdateSql(String schema,
                                        String tableName,
                                        Map<String, Object> params,
                                        Map<String, DBColumn> columnMap,
                                        String pkName,
                                        DBInfo.Type dbType) {
        StringJoiner setClause = new StringJoiner(", ");
        params.keySet().forEach(key -> {
            if (columnMap.containsKey(key)) {
                setClause.add(quoteIdentifier(key, dbType) + "=:" + key);
            }
        });
        return "update " + quoteSchemaTable(schema, tableName, dbType)
                + " set " + setClause
                + " where " + quoteIdentifier(pkName, dbType) + " = :" + pkName;
    }

    public static String buildPatchUpdateSql(String schema,
                                             String tableName,
                                             Map<String, Object> patchParams,
                                             Map<String, DBColumn> columnMap,
                                             String pkName,
                                             String pkBindName,
                                             DBInfo.Type dbType) {
        StringJoiner setClause = new StringJoiner(", ");
        patchParams.keySet().forEach(key -> {
            if (columnMap.containsKey(key) && !key.equalsIgnoreCase(pkName)) {
                setClause.add(quoteIdentifier(key, dbType) + "=:" + key);
            }
        });
        String renderedSetClause = setClause.toString();
        if (renderedSetClause.isBlank()) {
            throw new MuYunDatabaseException("No updatable fields were provided for patch update");
        }
        return "update " + quoteSchemaTable(schema, tableName, dbType)
                + " set " + renderedSetClause
                + " where " + quoteIdentifier(pkName, dbType) + " = :" + pkBindName;
    }

    public static InsertPlan prepareInsertPlan(String schema,
                                               String tableName,
                                               Map<String, ?> params,
                                               Map<String, DBColumn> columnMap,
                                               DBInfo.Type dbType) {
        StringJoiner columns = new StringJoiner(", ", "(", ")");
        StringJoiner values = new StringJoiner(", ", "(", ")");
        List<String> includedColumns = new ArrayList<>();
        List<String> bindNames = new ArrayList<>();

        int index = 0;
        for (String key : params.keySet()) {
            if (columnMap.containsKey(key)) {
                String bindName = "p_" + index++;
                includedColumns.add(key);
                bindNames.add(bindName);
                columns.add(quoteIdentifier(key, dbType));
                values.add(":" + bindName);
            }
        }

        String sql = "insert into " + quoteSchemaTable(schema, tableName, dbType) + " " + columns + " values " + values;
        return new InsertPlan(sql, includedColumns, bindNames);
    }

    public static PreparedSql prepareUpdateSql(String schema,
                                               String tableName,
                                               Map<String, Object> params,
                                               Map<String, DBColumn> columnMap,
                                               String pkName,
                                               DBInfo.Type dbType) {
        StringJoiner setClause = new StringJoiner(", ");
        Map<String, Object> bindParams = new HashMap<>();

        int index = 0;
        for (String key : params.keySet()) {
            if (columnMap.containsKey(key)) {
                String bindName = "p_" + index++;
                setClause.add(quoteIdentifier(key, dbType) + "=:" + bindName);
                bindParams.put(bindName, params.get(key));
            }
        }

        String pkBindName = "pk_0";
        Object pkValue = Stream.of(pkName, pkName.toUpperCase(), pkName.toLowerCase())
                .map(params::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(params.get(pkName));
        bindParams.put(pkBindName, pkValue);

        String sql = "update " + quoteSchemaTable(schema, tableName, dbType)
                + " set " + setClause
                + " where " + quoteIdentifier(pkName, dbType) + " = :" + pkBindName;
        return new PreparedSql(sql, bindParams);
    }

    public static PreparedSql preparePatchUpdateSql(String schema,
                                                    String tableName,
                                                    Map<String, Object> patchParams,
                                                    Map<String, DBColumn> columnMap,
                                                    String pkName,
                                                    String pkBindName,
                                                    DBInfo.Type dbType) {
        StringJoiner setClause = new StringJoiner(", ");
        Map<String, Object> bindParams = new HashMap<>();
        int index = 0;
        for (String key : patchParams.keySet()) {
            if (columnMap.containsKey(key) && !key.equalsIgnoreCase(pkName)) {
                String bindName = "p_" + index++;
                setClause.add(quoteIdentifier(key, dbType) + "=:" + bindName);
                bindParams.put(bindName, patchParams.get(key));
            }
        }

        String renderedSetClause = setClause.toString();
        if (renderedSetClause.isBlank()) {
            throw new MuYunDatabaseException("No updatable fields were provided for patch update");
        }

        String sql = "update " + quoteSchemaTable(schema, tableName, dbType)
                + " set " + renderedSetClause
                + " where " + quoteIdentifier(pkName, dbType) + " = :" + pkBindName;
        return new PreparedSql(sql, bindParams);
    }

    public static Map<String, Object> toBindMap(Map<String, Object> source, List<String> columns, List<String> bindNames) {
        Map<String, Object> bindParams = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            bindParams.put(bindNames.get(i), source.get(columns.get(i)));
        }
        return bindParams;
    }

    public static PreparedSql prepareAtomicUpsertSql(String schema,
                                                     String tableName,
                                                     List<String> columns,
                                                     String pkName,
                                                     Map<String, Object> transformed,
                                                     DBInfo.Type dbType) {
        return switch (dbType) {
            case MYSQL -> prepareMySqlAtomicUpsertSql(schema, tableName, columns, pkName, transformed, dbType);
            case POSTGRESQL -> preparePostgresAtomicUpsertSql(schema, tableName, columns, pkName, transformed, dbType);
            default -> throw new UnsupportedOperationException("Atomic upsert is not supported for database type: " + dbType);
        };
    }

    private static PreparedSql prepareMySqlAtomicUpsertSql(String schema,
                                                           String tableName,
                                                           List<String> columns,
                                                           String pkName,
                                                           Map<String, Object> transformed,
                                                           DBInfo.Type dbType) {
        Map<String, String> bindNames = createBindNameMap(columns);
        String columnSql = columns.stream()
                .map(col -> quoteIdentifier(col, dbType))
                .collect(java.util.stream.Collectors.joining(", "));
        String valueSql = columns.stream()
                .map(col -> ":" + bindNames.get(col))
                .collect(java.util.stream.Collectors.joining(", "));

        List<String> updateColumns = columns.stream()
                .filter(col -> !col.equalsIgnoreCase(pkName))
                .toList();
        String updateSql = updateColumns.isEmpty()
                ? quoteIdentifier(pkName, dbType) + "=" + quoteIdentifier(pkName, dbType)
                : updateColumns.stream()
                .map(col -> quoteIdentifier(col, dbType) + "=VALUES(" + quoteIdentifier(col, dbType) + ")")
                .collect(java.util.stream.Collectors.joining(", "));

        String sql = "insert into " + quoteSchemaTable(schema, tableName, dbType)
                + " (" + columnSql + ") values (" + valueSql + ") on duplicate key update " + updateSql;
        return new PreparedSql(sql, toBindMap(transformed, columns, columns.stream().map(bindNames::get).toList()));
    }

    private static PreparedSql preparePostgresAtomicUpsertSql(String schema,
                                                              String tableName,
                                                              List<String> columns,
                                                              String pkName,
                                                              Map<String, Object> transformed,
                                                              DBInfo.Type dbType) {
        Map<String, String> bindNames = createBindNameMap(columns);
        String columnSql = columns.stream()
                .map(col -> quoteIdentifier(col, dbType))
                .collect(java.util.stream.Collectors.joining(", "));
        String valueSql = columns.stream()
                .map(col -> ":" + bindNames.get(col))
                .collect(java.util.stream.Collectors.joining(", "));

        List<String> updateColumns = columns.stream()
                .filter(col -> !col.equalsIgnoreCase(pkName))
                .toList();
        String updateSql = updateColumns.isEmpty()
                ? quoteIdentifier(pkName, dbType) + "=EXCLUDED." + quoteIdentifier(pkName, dbType)
                : updateColumns.stream()
                .map(col -> quoteIdentifier(col, dbType) + "=EXCLUDED." + quoteIdentifier(col, dbType))
                .collect(java.util.stream.Collectors.joining(", "));

        String sql = "insert into " + quoteSchemaTable(schema, tableName, dbType)
                + " (" + columnSql + ") values (" + valueSql + ") on conflict (" + quoteIdentifier(pkName, dbType) + ") do update set " + updateSql;
        return new PreparedSql(sql, toBindMap(transformed, columns, columns.stream().map(bindNames::get).toList()));
    }

    private static Map<String, String> createBindNameMap(List<String> columns) {
        Map<String, String> bindNames = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            bindNames.put(columns.get(i), "p_" + i);
        }
        return bindNames;
    }

    private static String quoteIdentifier(String identifier, DBInfo.Type dbType) {
        return SchemaBuildRules.quoteIdentifier(identifier, dbType);
    }

    private static String quoteSchemaTable(String schema, String tableName, DBInfo.Type dbType) {
        return SchemaBuildRules.qualifiedName(schema, tableName, dbType);
    }

    public record InsertPlan(String sql, List<String> columns, List<String> bindNames) {
    }

    public record PreparedSql(String sql, Map<String, Object> params) {
    }
}
