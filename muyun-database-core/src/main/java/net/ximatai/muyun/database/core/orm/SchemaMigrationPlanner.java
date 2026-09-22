package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.builder.*;
import net.ximatai.muyun.database.core.builder.sql.SchemaBuildRules;
import net.ximatai.muyun.database.core.metadata.*;
import net.ximatai.muyun.database.core.orm.sql.MigrationSqlDialect;
import net.ximatai.muyun.database.core.orm.sql.MySqlMigrationSqlDialect;
import net.ximatai.muyun.database.core.orm.sql.PostgresMigrationSqlDialect;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SchemaMigrationPlanner {

    private final IDatabaseOperations<?> operations;
    private final DBInfo info;
    private final MigrationSqlDialect dialect;

    SchemaMigrationPlanner(IDatabaseOperations<?> operations) {
        this.operations = operations;
        this.info = operations.getDBInfo();
        this.dialect = createDialect(info.getDatabaseType());
    }

    Plan plan(TableWrapper wrapper) {
        String schema = wrapper.getSchema() == null || wrapper.getSchema().isBlank()
                ? operations.getDefaultSchemaName()
                : wrapper.getSchema();

        String table = wrapper.getName();
        assertValidIdentifier(schema, "schema");
        assertValidIdentifier(table, "table");
        validateModel(wrapper, schema);

        PlanBuilder builder = new PlanBuilder();

        DBSchema dbSchema = info.getSchema(schema);
        if (dbSchema == null) {
            builder.addAdditive(
                    MigrationChange.Type.CREATE_SCHEMA,
                    schema,
                    dialect.createSchemaIfNotExists(SchemaBuildRules.quoteIdentifier(schema, getDatabaseType()))
            );
        }

        if (dbSchema == null || !dbSchema.containsTable(table)) {
            planForNewTable(schema, table, wrapper, builder);
            return builder.build();
        }

        planForExistingTable(schema, table, wrapper, builder);
        return builder.build();
    }

    private void planForNewTable(String schema, String table, TableWrapper wrapper, PlanBuilder builder) {
        String schemaDotTable = SchemaBuildRules.qualifiedName(schema, table, getDatabaseType());
        Set<String> inheritedColumns = getDatabaseType() == DBInfo.Type.POSTGRESQL
                ? inheritedColumnNames(wrapper, schema)
                : Set.of();
        builder.addAdditive(MigrationChange.Type.CREATE_TABLE, schemaDotTable,
                dialect.createTableWithTempColumn(schemaDotTable, inheritanceClause(wrapper, schema)));

        if (wrapper.getComment() != null) {
            builder.addAdditive(MigrationChange.Type.SET_TABLE_COMMENT, schemaDotTable, dialect.setTableComment(schemaDotTable, wrapper.getComment()));
        }

        if (wrapper.getPrimaryKey() != null && !inheritedColumns.contains(wrapper.getPrimaryKey().getName())) {
            String type = resolveColumnType(wrapper.getPrimaryKey());
            builder.addAdditive(MigrationChange.Type.ADD_COLUMN, wrapper.getPrimaryKey().getName(), dialect.addColumn(schemaDotTable, buildColumnString(wrapper.getPrimaryKey(), type)));
            planNewColumnComment(schemaDotTable, wrapper.getPrimaryKey(), type, builder);
        }

        if (getDatabaseType() == DBInfo.Type.MYSQL) {
            planNewInheritedColumns(schemaDotTable, wrapper, schema, builder);
        }

        for (Column column : wrapper.getColumns()) {
            if (inheritedColumns.contains(column.getName())) continue;
            String type = resolveColumnType(column);
            builder.addAdditive(MigrationChange.Type.ADD_COLUMN, column.getName(), dialect.addColumn(schemaDotTable, buildColumnString(column, type)));
            planNewColumnComment(schemaDotTable, column, type, builder);
        }

        PrimaryKeyConstraint primaryKey = wrapper.getPrimaryKeyConstraint();
        if (primaryKey != null && !isInlinePrimaryKey(wrapper)) {
            builder.addAdditive(MigrationChange.Type.ADD_PRIMARY_KEY, constraintTarget(table, primaryKey.name(), "pkey"),
                    addPrimaryKeySql(schemaDotTable, table, primaryKey));
        }

        for (UniqueConstraint constraint : wrapper.getUniqueConstraints()) {
            builder.addAdditive(MigrationChange.Type.ADD_UNIQUE_CONSTRAINT, constraint.name(),
                    addUniqueConstraintSql(schemaDotTable, constraint));
        }

        for (ForeignKeyConstraint constraint : wrapper.getForeignKeys()) {
            builder.addAdditive(MigrationChange.Type.ADD_FOREIGN_KEY, constraint.name(),
                    addForeignKeySql(schemaDotTable, schema, constraint));
        }

        for (Index index : wrapper.getIndexes()) {
            builder.addAdditive(MigrationChange.Type.CREATE_INDEX, indexTarget(table, index), buildCreateIndexSql(schemaDotTable, table, index));
        }

        builder.addAdditive(MigrationChange.Type.DROP_TEMP_COLUMN, "_temp", dialect.dropTempColumn(schemaDotTable));
    }

    private void planForExistingTable(String schema, String tableName, TableWrapper wrapper, PlanBuilder builder) {
        DBTable table = info.getSchema(schema).getTable(tableName);
        String schemaDotTable = SchemaBuildRules.qualifiedName(schema, tableName, getDatabaseType());

        if (wrapper.getComment() != null && !Objects.equals(table.getDescription(), wrapper.getComment())) {
            builder.addAdditive(MigrationChange.Type.SET_TABLE_COMMENT, schemaDotTable, dialect.setTableComment(schemaDotTable, wrapper.getComment()));
        }

        planInheritedColumns(table, wrapper, builder);

        if (wrapper.getPrimaryKey() != null) {
            checkAndPlanColumn(table, wrapper.getPrimaryKey(), builder);
        }

        for (Column column : wrapper.getColumns()) {
            checkAndPlanColumn(table, column, builder);
        }

        checkAndPlanPrimaryKey(table, wrapper.getPrimaryKeyConstraint(), builder);
        for (UniqueConstraint constraint : wrapper.getUniqueConstraints()) {
            checkAndPlanUniqueConstraint(table, constraint, builder);
        }
        for (ForeignKeyConstraint constraint : wrapper.getForeignKeys()) {
            checkAndPlanForeignKey(table, constraint, builder);
        }

        for (String columnName : wrapper.getDroppedColumns()) {
            checkAndPlanDropColumn(table, columnName, builder);
        }

        for (Index index : wrapper.getDroppedIndexes()) {
            checkAndPlanDropIndex(table, index, builder);
        }

        for (Index index : wrapper.getIndexes()) {
            checkAndPlanIndex(table, index, builder);
        }
        planMissingInheritance(table, wrapper, builder);
    }

    private void checkAndPlanDropColumn(DBTable table, String columnName, PlanBuilder builder) {
        assertValidIdentifier(columnName, "column");
        if (!table.contains(columnName)) {
            return;
        }
        builder.addNonAdditive(MigrationChange.Type.DROP_COLUMN, columnName, dialect.dropColumn(
                SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType()),
                SchemaBuildRules.quoteIdentifier(columnName, getDatabaseType())
        ));
    }

    private void checkAndPlanDropIndex(DBTable table, Index index, PlanBuilder builder) {
        index.getColumns().forEach(name -> assertValidIdentifier(name, "index column"));
        String expectedName = SchemaBuildRules.indexName(table.getName(), index);
        table.getIndexList().stream()
                .filter(i -> expectedName.equalsIgnoreCase(i.getName()))
                .findFirst()
                .ifPresent(dbIndex -> builder.addNonAdditive(MigrationChange.Type.DROP_INDEX, dbIndex.getName(), dialect.dropIndex(
                        SchemaBuildRules.quoteIdentifier(table.getSchema(), getDatabaseType()),
                        SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType()),
                        SchemaBuildRules.quoteIdentifier(dbIndex.getName(), getDatabaseType())
                )));
    }

    private void checkAndPlanColumn(DBTable table, Column column, PlanBuilder builder) {
        String schemaDotTable = SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType());
        String quotedColumnName = SchemaBuildRules.quoteIdentifier(column.getName(), getDatabaseType());
        assertValidIdentifier(column.getName(), "column");

        String type = resolveColumnType(column);

        String baseColumnString = buildColumnString(column, type);

        if (!table.contains(column.getName())) {
            String sql = dialect.addColumn(schemaDotTable, baseColumnString);
            if (isNonAdditiveColumnAdd(column)) {
                builder.addValidationRequired(MigrationChange.Type.ADD_COLUMN, column.getName(), sql);
            } else {
                builder.addAdditive(MigrationChange.Type.ADD_COLUMN, column.getName(), sql);
            }
            return;
        }

        DBColumn dbColumn = table.getColumn(column.getName());

        ColumnDiffEvaluator.ColumnDiff columnDiff = ColumnDiffEvaluator.evaluate(column, dbColumn, type, getDatabaseType());

        if (columnDiff.typeChanged()) {
            builder.addNonAdditive(MigrationChange.Type.ALTER_COLUMN_TYPE, column.getName(), dialect.alterColumnType(
                    schemaDotTable,
                    quotedColumnName,
                    type + SchemaBuildRules.columnLength(column, getDatabaseType()),
                    baseColumnString));
        }

        if (columnDiff.nullableChanged()) {
            builder.addNonAdditive(MigrationChange.Type.ALTER_COLUMN_NULLABLE, column.getName(), dialect.alterColumnNullable(schemaDotTable, quotedColumnName, column.isNullable(), baseColumnString));
        }

        if (columnDiff.defaultChanged()) {
            builder.addNonAdditive(MigrationChange.Type.ALTER_COLUMN_DEFAULT, column.getName(), dialect.alterColumnDefault(schemaDotTable, quotedColumnName, column.getDefaultValue(), baseColumnString));
        }

        if (columnDiff.sequenceChanged()) {
            dialect.alterColumnSequence(schemaDotTable, table.getSchema(), table.getName(), column.getName(), column.isSequence())
                    .forEach(sql -> builder.addNonAdditive(MigrationChange.Type.ALTER_COLUMN_SEQUENCE, column.getName(), sql));
        }

        if (columnDiff.commentChanged()) {
            builder.addAdditive(MigrationChange.Type.SET_COLUMN_COMMENT, column.getName(), dialect.setColumnComment(schemaDotTable, quotedColumnName, column.getComment(), baseColumnString));
        }
    }

    private void checkAndPlanIndex(DBTable table, Index index, PlanBuilder builder) {
        List<String> targetColumns = index.getColumns();
        targetColumns.forEach(name -> assertValidIdentifier(name, "index column"));
        String expectedName = SchemaBuildRules.indexName(table.getName(), index);
        Optional<DBIndex> hit = table.getIndexList().stream()
                .filter(i -> expectedName.equalsIgnoreCase(i.getName()))
                .findFirst();
        if (hit.isEmpty() && index.getName() == null) {
            String previousGeneratedName = autoGeneratedCounterpartName(table.getName(), index);
            hit = table.getIndexList().stream()
                    .filter(i -> previousGeneratedName.equalsIgnoreCase(i.getName()))
                    .findFirst();
        }

        if (hit.isPresent()) {
            DBIndex dbIndex = hit.get();
            if (sameIndex(dbIndex, index)) {
                return;
            }

            builder.addNonAdditive(MigrationChange.Type.DROP_INDEX, dbIndex.getName(), dialect.dropIndex(
                    SchemaBuildRules.quoteIdentifier(table.getSchema(), getDatabaseType()),
                    SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType()),
                    SchemaBuildRules.quoteIdentifier(dbIndex.getName(), getDatabaseType())
            ));
        }

        builder.addAdditive(MigrationChange.Type.CREATE_INDEX, indexTarget(table.getName(), index), buildCreateIndexSql(
                SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType()),
                table.getName(),
                index
        ));
    }

    private String buildCreateIndexSql(String schemaDotTable, String tableName, Index index) {
        List<IndexColumn> columns = index.getIndexColumns();
        columns.forEach(column -> assertValidIdentifier(column.name(), "index column"));
        String indexName = SchemaBuildRules.indexName(tableName, index);
        assertValidIdentifier(indexName, "index");

        List<String> quotedColumns = columns.stream()
                .map(column -> SchemaBuildRules.quoteIdentifier(column.name(), getDatabaseType())
                        + (column.direction() == IndexSortDirection.DESC ? " DESC" : " ASC"))
                .toList();
        return dialect.createIndex(
                schemaDotTable,
                SchemaBuildRules.quoteIdentifier(indexName, getDatabaseType()),
                quotedColumns,
                index.isUnique(),
                index.getPredicate()
        );
    }

    private boolean sameIndex(DBIndex actual, Index expected) {
        if (actual.isUnique() != expected.isUnique()) {
            return false;
        }
        List<DBIndexColumn> actualColumns = actual.getIndexColumns();
        if (actualColumns.isEmpty()) {
            if (!actual.getColumns().equals(expected.getColumns())) {
                return false;
            }
        } else if (actualColumns.size() != expected.getIndexColumns().size()) {
            return false;
        } else {
            for (int i = 0; i < actualColumns.size(); i++) {
                DBIndexColumn left = actualColumns.get(i);
                IndexColumn right = expected.getIndexColumns().get(i);
                if (!left.name().equalsIgnoreCase(right.name()) || left.direction() != right.direction()) {
                    return false;
                }
            }
        }
        return normalizePredicate(actual.getPredicate()).equals(normalizePredicate(expected.getPredicate()));
    }

    private static final String SQL_IDENTIFIER = "(?:\"(?:\"\"|[^\"])+\"|[A-Za-z_][A-Za-z0-9_]*)";
    private static final Pattern SIMPLE_IN_PREDICATE = Pattern.compile(
            "^(" + SQL_IDENTIFIER + ")\\s+in\\s*\\((.*)\\)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SIMPLE_ANY_PREDICATE = Pattern.compile(
            "^\\(?(" + SQL_IDENTIFIER + ")\\)?(?:\\s*::\\s*[A-Za-z_][A-Za-z0-9_ ]*)?\\s*=\\s*any\\s*\\((.*)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ARRAY_EXPRESSION = Pattern.compile(
            "^\\(?\\s*array\\s*\\[(.*?)]\\s*\\)?(?:\\s*::\\s*[A-Za-z_][A-Za-z0-9_ ]*\\[\\])?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern POSTGRES_TYPE_CAST = Pattern.compile(
            "^::\\s*(?:character varying|timestamp (?:with|without) time zone|double precision|[a-z_][a-z0-9_]*)(?:\\[\\])?",
            Pattern.CASE_INSENSITIVE);

    private String normalizePredicate(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return "";
        }
        String normalized = stripOuterParentheses(removePostgresTypeCasts(normalizeSqlOutsideLiterals(predicate)));
        Matcher inMatcher = SIMPLE_IN_PREDICATE.matcher(normalized);
        if (inMatcher.matches()) {
            List<String> literals = parseStringLiteralList(inMatcher.group(2));
            if (literals != null) {
                return canonicalIdentifier(inMatcher.group(1)) + " in (" + String.join(",", literals) + ")";
            }
        }
        Matcher anyMatcher = SIMPLE_ANY_PREDICATE.matcher(normalized);
        if (anyMatcher.matches()) {
            Matcher arrayMatcher = ARRAY_EXPRESSION.matcher(anyMatcher.group(2).trim());
            if (arrayMatcher.matches()) {
                List<String> literals = parseStringLiteralList(arrayMatcher.group(1));
                if (literals != null) {
                    return canonicalIdentifier(anyMatcher.group(1)) + " in (" + String.join(",", literals) + ")";
                }
            }
        }
        return normalized;
    }

    private String normalizeSqlOutsideLiterals(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean pendingWhitespace = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (singleQuoted) {
                result.append(ch);
                if (ch == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    result.append(sql.charAt(++i));
                } else if (ch == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                result.append(ch);
                if (ch == '"' && i + 1 < sql.length() && sql.charAt(i + 1) == '"') {
                    result.append(sql.charAt(++i));
                } else if (ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }
            if (Character.isWhitespace(ch)) {
                pendingWhitespace = result.length() > 0;
                continue;
            }
            if (pendingWhitespace) {
                result.append(' ');
                pendingWhitespace = false;
            }
            if (ch == '\'') {
                singleQuoted = true;
                result.append(ch);
            } else if (ch == '"') {
                doubleQuoted = true;
                result.append(ch);
            } else {
                result.append(Character.toLowerCase(ch));
            }
        }
        return result.toString().trim();
    }

    private String stripOuterParentheses(String expression) {
        String result = expression.trim();
        while (result.startsWith("(") && result.endsWith(")") && enclosesWholeExpression(result)) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private String removePostgresTypeCasts(String expression) {
        StringBuilder result = new StringBuilder(expression.length());
        boolean quoted = false;
        for (int i = 0; i < expression.length(); ) {
            char ch = expression.charAt(i);
            if (ch == '\'') {
                result.append(ch);
                if (quoted && i + 1 < expression.length() && expression.charAt(i + 1) == '\'') {
                    result.append(expression.charAt(i + 1));
                    i += 2;
                    continue;
                }
                quoted = !quoted;
                i++;
                continue;
            }
            if (!quoted && ch == ':' && i + 1 < expression.length() && expression.charAt(i + 1) == ':') {
                Matcher cast = POSTGRES_TYPE_CAST.matcher(expression.substring(i));
                if (cast.find()) {
                    i += cast.end();
                    continue;
                }
            }
            result.append(ch);
            i++;
        }
        return result.toString().trim();
    }

    private boolean enclosesWholeExpression(String expression) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '\'') {
                if (quoted && i + 1 < expression.length() && expression.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                quoted = !quoted;
            } else if (!quoted && ch == '(') {
                depth++;
            } else if (!quoted && ch == ')' && --depth == 0 && i < expression.length() - 1) {
                return false;
            }
        }
        return depth == 0 && !quoted;
    }

    private List<String> parseStringLiteralList(String expression) {
        List<String> literals = new ArrayList<>();
        int position = 0;
        while (position < expression.length()) {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) position++;
            if (position >= expression.length() || expression.charAt(position) != '\'') return null;
            int start = position++;
            boolean closed = false;
            while (position < expression.length()) {
                if (expression.charAt(position) == '\'') {
                    if (position + 1 < expression.length() && expression.charAt(position + 1) == '\'') {
                        position += 2;
                    } else {
                        position++;
                        closed = true;
                        break;
                    }
                } else {
                    position++;
                }
            }
            if (!closed) return null;
            literals.add(expression.substring(start, position));
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) position++;
            if (position + 1 < expression.length() && expression.startsWith("::", position)) {
                position += 2;
                while (position < expression.length() && expression.charAt(position) != ',') position++;
            }
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) position++;
            if (position == expression.length()) break;
            if (expression.charAt(position++) != ',') return null;
        }
        return literals.isEmpty() ? null : literals;
    }

    private String canonicalIdentifier(String identifier) {
        return identifier.startsWith("\"") ? identifier : identifier.toLowerCase(Locale.ROOT);
    }

    private String indexTarget(String tableName, Index index) {
        return SchemaBuildRules.indexName(tableName, index);
    }

    private String autoGeneratedCounterpartName(String tableName, Index index) {
        return tableName + "_" + String.join("_", index.getColumns())
                + (index.isUnique() ? "_index" : "_uindex");
    }

    private String inheritanceClause(TableWrapper wrapper, String defaultSchema) {
        if (wrapper.getInherits().isEmpty()) {
            return "";
        }
        if (getDatabaseType() != DBInfo.Type.POSTGRESQL) {
            return "";
        }
        return " inherits (" + wrapper.getInherits().stream().map(parent -> {
            String schema = parent.getSchema() == null || parent.getSchema().isBlank() ? defaultSchema : parent.getSchema();
            assertValidIdentifier(schema, "inherit schema");
            assertValidIdentifier(parent.getName(), "inherit table");
            DBSchema dbSchema = info.getSchema(schema);
            if (dbSchema == null || !dbSchema.containsTable(parent.getName())) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING,
                        "inherited table does not exist: " + schema + "." + parent.getName());
            }
            return SchemaBuildRules.qualifiedName(schema, parent.getName(), getDatabaseType());
        }).collect(java.util.stream.Collectors.joining(", ")) + ")";
    }

    private void planMissingInheritance(DBTable table, TableWrapper wrapper, PlanBuilder builder) {
        if (wrapper.getInherits().isEmpty()) return;
        if (getDatabaseType() != DBInfo.Type.POSTGRESQL) {
            return;
        }
        String child = SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType());
        for (TableBase parent : wrapper.getInherits()) {
            String parentSchema = parent.getSchema() == null || parent.getSchema().isBlank() ? table.getSchema() : parent.getSchema();
            String qualifiedParent = SchemaBuildRules.qualifiedName(parentSchema, parent.getName(), getDatabaseType());
            Map<String, Object> hit = operations.row("select 1 from pg_inherits where inhparent = ?::regclass and inhrelid = ?::regclass",
                    List.of(qualifiedParent, child));
            if (hit == null) {
                builder.addAdditive(MigrationChange.Type.ADD_TABLE_INHERITANCE, qualifiedParent,
                        "alter table " + child + " inherit " + qualifiedParent);
            }
        }
    }

    private void planInheritedColumns(DBTable child, TableWrapper wrapper, PlanBuilder builder) {
        if (wrapper.getInherits().isEmpty()) return;
        for (TableBase parent : wrapper.getInherits()) {
            String parentSchema = parent.getSchema() == null || parent.getSchema().isBlank()
                    ? child.getSchema()
                    : parent.getSchema();
            DBSchema schema = info.getSchema(parentSchema);
            if (schema == null || !schema.containsTable(parent.getName())) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING,
                        "inherited table does not exist: " + parentSchema + "." + parent.getName());
            }
            for (DBColumn column : schema.getTable(parent.getName()).getColumnMap().values()) {
                if (!child.contains(column.getName())) {
                    Column inherited = column.toColumn();
                    checkAndPlanColumn(child, inherited, builder);
                }
            }
        }
    }

    private void planNewInheritedColumns(String schemaDotTable,
                                         TableWrapper wrapper,
                                         String defaultSchema,
                                         PlanBuilder builder) {
        Set<String> declared = new HashSet<>();
        if (wrapper.getPrimaryKey() != null) declared.add(wrapper.getPrimaryKey().getName());
        wrapper.getColumns().stream().map(Column::getName).forEach(declared::add);
        for (TableBase parent : wrapper.getInherits()) {
            String parentSchema = parent.getSchema() == null || parent.getSchema().isBlank()
                    ? defaultSchema
                    : parent.getSchema();
            DBSchema schema = info.getSchema(parentSchema);
            if (schema == null || !schema.containsTable(parent.getName())) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING,
                        "inherited table does not exist: " + parentSchema + "." + parent.getName());
            }
            for (DBColumn dbColumn : schema.getTable(parent.getName()).getColumnMap().values()) {
                if (!declared.add(dbColumn.getName())) continue;
                Column column = dbColumn.toColumn();
                String type = resolveColumnType(column);
                builder.addAdditive(MigrationChange.Type.ADD_COLUMN, column.getName(),
                        dialect.addColumn(schemaDotTable, buildColumnString(column, type)));
                planNewColumnComment(schemaDotTable, column, type, builder);
            }
        }
    }

    private void checkAndPlanPrimaryKey(DBTable table, PrimaryKeyConstraint expected, PlanBuilder builder) {
        if (expected == null) {
            return;
        }
        DBPrimaryKey actual = table.getPrimaryKey();
        if (actual == null) {
            List<String> legacyColumns = table.getColumnMap().values().stream()
                    .filter(DBColumn::isPrimaryKey)
                    .map(DBColumn::getName)
                    .toList();
            if (!legacyColumns.isEmpty()) {
                actual = new DBPrimaryKey(null, legacyColumns);
            }
        }
        boolean sameColumns = actual != null && actual.columns().equals(expected.columns());
        boolean sameName = getDatabaseType() == DBInfo.Type.MYSQL
                || expected.name() == null
                || (actual != null && expected.name().equalsIgnoreCase(actual.name()));
        if (sameColumns && sameName) {
            return;
        }
        String schemaDotTable = SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType());
        if (actual != null) {
            String actualName = actual.name() == null ? table.getName() + "_pkey" : actual.name();
            builder.addNonAdditive(MigrationChange.Type.DROP_PRIMARY_KEY, actualName,
                    dialect.dropPrimaryKey(schemaDotTable, quote(actualName)));
        }
        builder.addValidationRequired(MigrationChange.Type.ADD_PRIMARY_KEY,
                constraintTarget(table.getName(), expected.name(), "pkey"),
                addPrimaryKeySql(schemaDotTable, table.getName(), expected));
    }

    private void checkAndPlanUniqueConstraint(DBTable table, UniqueConstraint expected, PlanBuilder builder) {
        Optional<DBUniqueConstraint> sameName = table.getUniqueConstraints().stream()
                .filter(actual -> expected.name().equalsIgnoreCase(actual.name()))
                .findFirst();
        if (sameName.isPresent() && sameName.get().columns().equals(expected.columns())) {
            return;
        }
        String schemaDotTable = SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType());
        sameName.ifPresent(actual -> builder.addNonAdditive(MigrationChange.Type.DROP_UNIQUE_CONSTRAINT,
                actual.name(), dialect.dropUniqueConstraint(schemaDotTable, quote(actual.name()))));
        builder.addValidationRequired(MigrationChange.Type.ADD_UNIQUE_CONSTRAINT, expected.name(),
                addUniqueConstraintSql(schemaDotTable, expected));
    }

    private void checkAndPlanForeignKey(DBTable table, ForeignKeyConstraint expected, PlanBuilder builder) {
        Optional<DBForeignKey> sameName = table.getForeignKeys().stream()
                .filter(actual -> expected.name().equalsIgnoreCase(actual.name()))
                .findFirst();
        String referencedSchema = expected.referencedSchema() == null || expected.referencedSchema().isBlank()
                ? table.getSchema()
                : expected.referencedSchema();
        boolean aligned = sameName.filter(actual ->
                actual.columns().equals(expected.columns())
                        && Objects.equals(actual.referencedSchema(), referencedSchema)
                        && actual.referencedTable().equalsIgnoreCase(expected.referencedTable())
                        && actual.referencedColumns().equals(expected.referencedColumns())
                        && sameForeignKeyAction(actual.onDelete(), expected.onDelete())).isPresent();
        if (aligned) {
            return;
        }
        String schemaDotTable = SchemaBuildRules.qualifiedName(table.getSchema(), table.getName(), getDatabaseType());
        sameName.ifPresent(actual -> builder.addNonAdditive(MigrationChange.Type.DROP_FOREIGN_KEY,
                actual.name(), dialect.dropForeignKey(schemaDotTable, quote(actual.name()))));
        builder.addValidationRequired(MigrationChange.Type.ADD_FOREIGN_KEY, expected.name(),
                addForeignKeySql(schemaDotTable, table.getSchema(), expected));
    }

    private String addPrimaryKeySql(String schemaDotTable, String table, PrimaryKeyConstraint constraint) {
        String name = constraintTarget(table, constraint.name(), "pkey");
        return dialect.addConstraint(schemaDotTable, quote(name),
                "primary key (" + quoteColumns(constraint.columns()) + ")");
    }

    private String addUniqueConstraintSql(String schemaDotTable, UniqueConstraint constraint) {
        return dialect.addConstraint(schemaDotTable, quote(constraint.name()),
                "unique (" + quoteColumns(constraint.columns()) + ")");
    }

    private String addForeignKeySql(String schemaDotTable, String defaultSchema, ForeignKeyConstraint constraint) {
        String referencedSchema = constraint.referencedSchema() == null || constraint.referencedSchema().isBlank()
                ? defaultSchema
                : constraint.referencedSchema();
        return dialect.addConstraint(schemaDotTable, quote(constraint.name()),
                "foreign key (" + quoteColumns(constraint.columns()) + ") references "
                        + SchemaBuildRules.qualifiedName(referencedSchema, constraint.referencedTable(), getDatabaseType())
                        + " (" + quoteColumns(constraint.referencedColumns()) + ") on delete " + constraint.onDelete().sql());
    }

    private String quoteColumns(List<String> columns) {
        return columns.stream().map(this::quote).collect(java.util.stream.Collectors.joining(", "));
    }

    private String quote(String identifier) {
        assertValidIdentifier(identifier, "constraint or column");
        return SchemaBuildRules.quoteIdentifier(identifier, getDatabaseType());
    }

    private String constraintTarget(String table, String explicitName, String suffix) {
        return explicitName == null || explicitName.isBlank() ? table + "_" + suffix : explicitName;
    }

    private void validateModel(TableWrapper wrapper, String defaultSchema) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.addAll(inheritedColumnNames(wrapper, defaultSchema));
        LinkedHashSet<String> declaredColumns = new LinkedHashSet<>();
        if (wrapper.getPrimaryKey() != null) {
            declaredColumns.add(wrapper.getPrimaryKey().getName());
            columns.add(wrapper.getPrimaryKey().getName());
        }
        wrapper.getColumns().forEach(column -> {
            if (!declaredColumns.add(column.getName())) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING, "duplicate column: " + column.getName());
            }
            columns.add(column.getName());
            validateColumnCapability(column);
        });
        if (wrapper.getPrimaryKey() != null) {
            validateColumnCapability(wrapper.getPrimaryKey());
        }
        PrimaryKeyConstraint primaryKey = wrapper.getPrimaryKeyConstraint();
        if (primaryKey != null) {
            validateLocalColumns(columns, primaryKey.columns(), "primary key");
            if (primaryKey.name() != null) assertValidIdentifier(primaryKey.name(), "primary key");
        }
        for (UniqueConstraint constraint : wrapper.getUniqueConstraints()) {
            assertValidIdentifier(constraint.name(), "unique constraint");
            validateLocalColumns(columns, constraint.columns(), "unique constraint");
        }
        for (ForeignKeyConstraint constraint : wrapper.getForeignKeys()) {
            assertValidIdentifier(constraint.name(), "foreign key");
            validateLocalColumns(columns, constraint.columns(), "foreign key");
            assertValidIdentifier(constraint.referencedTable(), "referenced table");
            assertValidIdentifier(constraint.referencedSchema() == null ? defaultSchema : constraint.referencedSchema(), "referenced schema");
            constraint.referencedColumns().forEach(column -> assertValidIdentifier(column, "referenced column"));
        }
        for (Index index : wrapper.getIndexes()) {
            validateLocalColumns(columns, index.getColumns(), "index");
            if (index.getPredicate() != null) {
                if (getDatabaseType() != DBInfo.Type.POSTGRESQL) {
                    throw new OrmException(OrmException.Code.INVALID_MAPPING,
                            "partial indexes are only supported on PostgreSQL: " + SchemaBuildRules.indexName(wrapper.getName(), index));
                }
                if (index.getPredicate().indexOf('\0') >= 0 || index.getPredicate().contains(";")) {
                    throw new OrmException(OrmException.Code.INVALID_MAPPING, "invalid index predicate");
                }
            }
        }
    }

    private Set<String> inheritedColumnNames(TableWrapper wrapper, String defaultSchema) {
        if (wrapper.getInherits().isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> inheritedColumns = new LinkedHashSet<>();
        for (TableBase parent : wrapper.getInherits()) {
            String schemaName = parent.getSchema() == null || parent.getSchema().isBlank()
                    ? defaultSchema
                    : parent.getSchema();
            DBSchema schema = info.getSchema(schemaName);
            if (schema == null || !schema.containsTable(parent.getName())) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING,
                        "inherited table does not exist: " + schemaName + "." + parent.getName());
            }
            inheritedColumns.addAll(schema.getTable(parent.getName()).getColumnMap().keySet());
        }
        return inheritedColumns;
    }

    private boolean sameForeignKeyAction(ForeignKeyAction actual, ForeignKeyAction expected) {
        if (actual == expected) {
            return true;
        }
        return getDatabaseType() == DBInfo.Type.MYSQL
                && EnumSet.of(ForeignKeyAction.NO_ACTION, ForeignKeyAction.RESTRICT).contains(actual)
                && EnumSet.of(ForeignKeyAction.NO_ACTION, ForeignKeyAction.RESTRICT).contains(expected);
    }

    private void validateColumnCapability(Column column) {
        if (getDatabaseType() == DBInfo.Type.MYSQL
                && (column.getType() == ColumnType.UUID || column.getType() == ColumnType.TIMESTAMP_WITH_TIME_ZONE)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING,
                    column.getType() + " is not supported natively on MySQL: " + column.getName());
        }
    }

    private void validateLocalColumns(Set<String> available, List<String> required, String kind) {
        for (String column : required) {
            assertValidIdentifier(column, kind + " column");
            if (!available.contains(column)) {
                throw new OrmException(OrmException.Code.INVALID_MAPPING,
                        kind + " references unknown column: " + column);
            }
        }
    }

    private boolean isNonAdditiveColumnAdd(Column column) {
        return !column.isNullable() && column.getDefaultValue() == null;
    }

    private boolean isInlinePrimaryKey(TableWrapper wrapper) {
        return getDatabaseType() == DBInfo.Type.MYSQL
                && wrapper.getPrimaryKey() != null
                && "AUTO_INCREMENT".equalsIgnoreCase(wrapper.getPrimaryKey().getDefaultValue());
    }

    private String buildColumnString(Column column, String type) {
        String name = column.getName();
        assertValidIdentifier(name, "column");
        return SchemaBuildRules.columnDefinition(column, type, getDatabaseType());
    }

    private void planNewColumnComment(String schemaDotTable, Column column, String type, PlanBuilder builder) {
        if (column.getComment() == null || column.getComment().isBlank()) return;
        String quotedColumn = SchemaBuildRules.quoteIdentifier(column.getName(), getDatabaseType());
        String definition = buildColumnString(column, type);
        builder.addAdditive(MigrationChange.Type.SET_COLUMN_COMMENT, column.getName(),
                dialect.setColumnComment(schemaDotTable, quotedColumn, column.getComment(), definition));
    }

    private String resolveColumnType(Column column) {
        String type = SchemaBuildRules.columnType(column, getDatabaseType());
        if (ColumnType.UNKNOWN.name().equals(type) || type == null) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "column type not provided: " + column.getName());
        }
        return type;
    }

    private void assertValidIdentifier(String identifier, String type) {
        if (!SchemaBuildRules.isValidIdentifier(identifier)) {
            throw new OrmException(OrmException.Code.INVALID_MAPPING, "Invalid " + type + " identifier: " + identifier);
        }
    }

    private DBInfo.Type getDatabaseType() {
        return info.getDatabaseType();
    }

    private MigrationSqlDialect createDialect(DBInfo.Type databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> new PostgresMigrationSqlDialect();
            default -> new MySqlMigrationSqlDialect();
        };
    }

    static class Plan {
        private final List<MigrationChange> changes;

        Plan(List<MigrationChange> changes) {
            this.changes = changes;
        }

        public List<String> getStatements() {
            return changes.stream().map(MigrationChange::getSql).toList();
        }

        public List<MigrationChange> getChanges() {
            return changes;
        }

        public boolean hasNonAdditive() {
            return changes.stream().anyMatch(MigrationChange::isNonAdditive);
        }

        public boolean isChanged() {
            return !changes.isEmpty();
        }
    }

    private static class PlanBuilder {
        private final List<MigrationChange> changes = new ArrayList<>();

        void addAdditive(MigrationChange.Type type, String target, String sql) {
            changes.add(MigrationChange.additive(type, target, sql));
        }

        void addNonAdditive(MigrationChange.Type type, String target, String sql) {
            changes.add(MigrationChange.nonAdditive(type, target, sql));
        }

        void addValidationRequired(MigrationChange.Type type, String target, String sql) {
            changes.add(MigrationChange.validationRequired(type, target, sql));
        }

        Plan build() {
            return new Plan(List.copyOf(changes));
        }
    }
}
