package net.ximatai.muyun.database.core.builder;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.AnnotationProcessor;
import net.ximatai.muyun.database.core.builder.sql.MySqlTableBuilderSqlDialect;
import net.ximatai.muyun.database.core.builder.sql.PostgresTableBuilderSqlDialect;
import net.ximatai.muyun.database.core.builder.sql.SchemaBuildRules;
import net.ximatai.muyun.database.core.builder.sql.TableBuilderSqlDialect;
import net.ximatai.muyun.database.core.exception.MuYunDatabaseException;
import net.ximatai.muyun.database.core.metadata.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.ximatai.muyun.database.core.metadata.DBInfo.Type.POSTGRESQL;

public class TableBuilder {

    private static final Logger logger = LoggerFactory.getLogger(TableBuilder.class);

    private final DBInfo info;
    private final IDatabaseOperations<?> db;
    private final TableBuilderSqlDialect dialect;

    public TableBuilder(IDatabaseOperations<?> db) {
        this.db = db;
        this.info = db.getDBInfo();
        this.dialect = createDialect();
    }

    public boolean build(Class<?> entityClass) {
        TableWrapper wrapper = AnnotationProcessor.fromEntityClass(entityClass);
        return build(wrapper);
    }

    public boolean build(TableWrapper wrapper) {

        boolean result = false;
        String schema = wrapper.getSchema() != null ? wrapper.getSchema() : info.getDefaultSchemaName();
        String name = wrapper.getName();
        requireValidIdentifier(schema, "schema");
        requireValidIdentifier(name, "table");
        String schemaDotTable = qualifiedName(schema, name);

        logger.info("Table %s.%s build initiated".formatted(schema, name));

        String inheritSQL = "";

        if (info.getSchema(schema) == null) {
            db.execute(dialect.createSchemaIfNotExists(SchemaBuildRules.quoteIdentifier(schema, getDatabaseType())));
            info.addSchema(new DBSchema(schema));
            logger.info("create schema " + schema);
        }

        List<TableBase> inherits = wrapper.getInherits();

        // 检查继承的父表是否存在
        inherits.forEach(inherit -> {
            requireValidIdentifier(inherit.getSchema(), "inherit schema");
            requireValidIdentifier(inherit.getName(), "inherit table");
            DBSchema parentSchema = info.getSchema(inherit.getSchema());
            if (parentSchema == null || !parentSchema.containsTable(inherit.getName())) {
                throw new MuYunDatabaseException("Table " + inherit + " does not exist");
            }
        });

        if (getDatabaseType().equals(POSTGRESQL)) {
            inheritSQL = buildInheritSQLForPostgres(inherits);
        }

        if (!info.getSchema(schema).containsTable(wrapper.getName())) {
            db.execute(dialect.createTableWithTempColumn(schemaDotTable, inheritSQL));
            logger.info("create table " + schemaDotTable);
            result = true;
            info.getSchema(schema).addTable(new DBTable(db.getMetaDataLoader()).setName(name).setSchema(schema));
        }

        DBTable dbTable = info.getSchema(schema).getTable(wrapper.getName());

        buildInheritColumns(dbTable, inherits);

        if (wrapper.getComment() != null && !Objects.equals(dbTable.getDescription(), wrapper.getComment())) {
            db.execute(dialect.setTableComment(schemaDotTable, wrapper.getComment()));
            dbTable.setDescription(wrapper.getComment());
        }

        if (wrapper.getPrimaryKey() != null) {
            checkAndBuildColumn(dbTable, wrapper.getPrimaryKey());
        }

        wrapper.getColumns().forEach(column -> {
            checkAndBuildColumn(dbTable, column);
        });

        if (result) {
            db.execute(dialect.dropTempColumn(schemaDotTable));
        }

        dbTable.resetColumns();

        wrapper.getDroppedColumns().forEach(columnName -> {
            dropColumnIfExists(dbTable, columnName);
        });

        dbTable.resetColumns();

        wrapper.getDroppedIndexes().forEach(index -> {
            dropIndexIfExists(dbTable, index);
        });

        dbTable.resetIndexes();

        dropObsoleteUniqueIndexes(dbTable, wrapper);

        dbTable.resetIndexes();

        wrapper.getIndexes().forEach(index -> {
            checkAndBuildIndex(dbTable, index);
        });

        dbTable.resetIndexes();

        fixTableInherits(dbTable, inherits);

        logger.info("Table %s.%s build finished".formatted(schema, name));

        return result;
    }

    private void fixTableInherits(DBTable dbTable, List<TableBase> inherits) {
        if (!inherits.isEmpty() && getDatabaseType().equals(POSTGRESQL)) {
            inherits.forEach(inherit -> {

                Map<String, Object> row = db.row("SELECT * FROM pg_inherits\n" +
                        "WHERE inhparent = ?::regclass AND inhrelid = ?::regclass;", qualifiedName(inherit.getSchema(), inherit.getName()), qualifiedName(dbTable.getSchema(), dbTable.getName()));

                if (row == null) {
                    db.execute("alter table " + qualifiedName(dbTable.getSchema(), dbTable.getName()) + " inherit " + qualifiedName(inherit.getSchema(), inherit.getName()) + ";");
                    logger.info("table " + qualifiedName(dbTable.getSchema(), dbTable.getName()) + " inherit " + qualifiedName(inherit.getSchema(), inherit.getName()));
                }
            });
        }
    }

    private void buildInheritColumns(DBTable dbTable, List<TableBase> inherits) {
        AtomicBoolean isModify = new AtomicBoolean(false);
        inherits.forEach(inherit -> {
            DBTable table = info.getSchema(inherit.getSchema()).getTable(inherit.getName());

            table.getColumnMap().forEach((columnName, dbColumn) -> {
                checkAndBuildColumn(dbTable, dbColumn.toColumn());
                isModify.set(true);
            });

        });
        if (isModify.get()) {
            dbTable.resetColumns();
        }
    }

    private boolean checkAndBuildColumn(DBTable dbTable, Column column) {
        boolean changed = false;
        String name = column.getName();
        requireValidIdentifier(name, "column");
        String type = SchemaBuildRules.columnType(column, getDatabaseType());

        if (ColumnType.UNKNOWN.name().equals(type)) {
            throw new MuYunDatabaseException("column: " + column + " type not provided");
        } else {
            Objects.requireNonNull(type, "column: " + column + " type not provided");
        }

        String comment = column.getComment();
        String length = SchemaBuildRules.columnLength(column);

        boolean sequence = column.isSequence();
        boolean nullable = column.isNullable();
        boolean primaryKey = column.isPrimaryKey();
        String defaultValue = column.getDefaultValue();

        String baseColumnString = SchemaBuildRules.columnDefinition(column, type, getDatabaseType());
        String quotedSchemaDotTable = qualifiedName(dbTable.getSchema(), dbTable.getName());
        String quotedName = SchemaBuildRules.quoteIdentifier(name, getDatabaseType());

        if (!dbTable.contains(name)) {
            db.execute(dialect.addColumn(quotedSchemaDotTable, baseColumnString));
            dbTable.resetColumns();
            changed = true;
            logger.info("column " + dbTable.getSchemaDotTable() + "." + name + " built");
        }

        DBColumn dbColumn = dbTable.getColumn(name);

        if (!sameColumnType(type, dbColumn) || column.getLength() != null && !column.getLength().equals(dbColumn.getLength())) {
            db.execute(dialect.alterColumnType(quotedSchemaDotTable, quotedName, type + length, baseColumnString));
            dbTable.resetColumns();
            changed = true;
            dbColumn = dbTable.getColumn(name);
        }

        if (primaryKey && !dbColumn.isPrimaryKey()) {
            db.execute("alter table " + quotedSchemaDotTable + " add primary key (" + quotedName + ")");
            dbTable.resetColumns();
            changed = true;
            dbColumn = dbTable.getColumn(name);
        }

        if (dbColumn.isNullable() != nullable) {
            db.execute(dialect.alterColumnNullable(quotedSchemaDotTable, quotedName, nullable, baseColumnString));
            dbTable.resetColumns();
            changed = true;
            dbColumn = dbTable.getColumn(name);
        }

        if (!dbColumn.isSequence() && !SchemaBuildRules.sameColumnDefault(type, dbColumn.getType(), getDatabaseType(), dbColumn.getLength(), defaultValue, dbColumn.getDefaultValueWithString())) {
            db.execute(dialect.alterColumnDefault(quotedSchemaDotTable, quotedName, defaultValue, baseColumnString));
            dbTable.resetColumns();
            changed = true;
            dbColumn = dbTable.getColumn(name);
        }

        if (dbColumn.isSequence() != sequence) {
            dialect.alterColumnSequence(quotedSchemaDotTable, dbTable.getSchema(), dbTable.getName(), name, sequence)
                    .forEach(db::execute);
            dbTable.resetColumns();
            changed = true;
            dbColumn = dbTable.getColumn(name);
        }

        if (comment != null && !Objects.equals(dbColumn.getDescription(), comment)) {
            db.execute(dialect.setColumnComment(quotedSchemaDotTable, quotedName, comment, baseColumnString));
            dbTable.resetColumns();
            changed = true;
        }

        return changed;
    }

    private boolean sameColumnType(String expectedType, DBColumn dbColumn) {
        return SchemaBuildRules.sameColumnType(expectedType, dbColumn.getType(), getDatabaseType(), dbColumn.getLength());
    }

    private boolean dropColumnIfExists(DBTable dbTable, String columnName) {
        requireValidIdentifier(columnName, "column");
        if (!dbTable.contains(columnName)) {
            return false;
        }
        db.execute(dialect.dropColumn(
                qualifiedName(dbTable.getSchema(), dbTable.getName()),
                SchemaBuildRules.quoteIdentifier(columnName, getDatabaseType())
        ));
        logger.info("column " + dbTable.getSchemaDotTable() + "." + columnName + " dropped");
        return true;
    }

    private boolean dropIndexIfExists(DBTable dbTable, Index index) {
        List<String> columns = new ArrayList<>(index.getColumns());
        columns.forEach(columnName -> requireValidIdentifier(columnName, "index column"));
        Set<String> columnSet = new HashSet<>(columns);
        Optional<DBIndex> dbIndexOptional = dbTable.getIndexList().stream()
                .filter(i -> new HashSet<>(i.getColumns()).equals(columnSet))
                .findFirst();
        if (dbIndexOptional.isEmpty()) {
            return false;
        }

        DBIndex dbIndex = dbIndexOptional.get();
        db.execute(dialect.dropIndex(
                SchemaBuildRules.quoteIdentifier(dbTable.getSchema(), getDatabaseType()),
                qualifiedName(dbTable.getSchema(), dbTable.getName()),
                SchemaBuildRules.quoteIdentifier(dbIndex.getName(), getDatabaseType())
        ));
        logger.info("index " + dbTable.getSchemaDotTable() + "." + dbIndex.getName() + " dropped");
        return true;
    }

    private void dropObsoleteUniqueIndexes(DBTable dbTable, TableWrapper wrapper) {
        List<Set<String>> targetUniqueColumnSets = wrapper.getIndexes().stream()
                .filter(Index::isUnique)
                .map(index -> (Set<String>) new LinkedHashSet<>(index.getColumns()))
                .toList();
        for (DBIndex dbIndex : dbTable.getIndexList()) {
            if (!dbIndex.isUnique()) {
                continue;
            }
            Set<String> existingColumns = new LinkedHashSet<>(dbIndex.getColumns());
            boolean stillTargeted = targetUniqueColumnSets.stream().anyMatch(existingColumns::equals);
            boolean replacedByWiderUnique = targetUniqueColumnSets.stream()
                    .anyMatch(targetColumns -> targetColumns.size() > existingColumns.size()
                            && targetColumns.containsAll(existingColumns));
            if (!stillTargeted && replacedByWiderUnique) {
                db.execute(dialect.dropIndex(
                        SchemaBuildRules.quoteIdentifier(dbTable.getSchema(), getDatabaseType()),
                        qualifiedName(dbTable.getSchema(), dbTable.getName()),
                        SchemaBuildRules.quoteIdentifier(dbIndex.getName(), getDatabaseType())
                ));
                logger.info("index " + dbTable.getSchemaDotTable() + "." + dbIndex.getName() + " dropped");
            }
        }
    }

    private boolean checkAndBuildIndex(DBTable dbTable, Index index) {
        List<String> columns = new ArrayList<>(index.getColumns());
        columns.forEach(columnName -> requireValidIdentifier(columnName, "index column"));
        Set<String> columnSet = new HashSet<>(columns);
        List<DBIndex> indexList = dbTable.getIndexList();
        Optional<DBIndex> dbIndexOptional = indexList.stream().filter(i -> new HashSet<>(i.getColumns()).equals(columnSet)).findFirst();

        if (dbIndexOptional.isPresent()) {
            DBIndex dbIndex = dbIndexOptional.get();
            if (dbIndex.isUnique() == index.isUnique()) {
                return false;
            } else {
                db.execute(dialect.dropIndex(
                        SchemaBuildRules.quoteIdentifier(dbTable.getSchema(), getDatabaseType()),
                        qualifiedName(dbTable.getSchema(), dbTable.getName()),
                        SchemaBuildRules.quoteIdentifier(dbIndex.getName(), getDatabaseType())
                ));
                logger.info("index " + dbTable.getSchemaDotTable() + "." + dbIndex.getName() + " dropped");
            }

        }

        String indexName = SchemaBuildRules.indexName(dbTable.getName(), index);
        requireValidIdentifier(indexName, "index");
        String quotedSchemaDotTable = qualifiedName(dbTable.getSchema(), dbTable.getName());
        String quotedIndexName = SchemaBuildRules.quoteIdentifier(indexName, getDatabaseType());
        List<String> quotedColumns = columns.stream()
                .map(column -> SchemaBuildRules.quoteIdentifier(column, getDatabaseType()))
                .toList();

        db.execute(dialect.createIndex(quotedSchemaDotTable, quotedIndexName, quotedColumns, index.isUnique()));

        logger.info("index " + dbTable.getSchemaDotTable() + "." + indexName + " created");

        return true;
    }

    private String buildInheritSQLForPostgres(List<TableBase> inherits) {
        if (inherits.isEmpty()) {
            return "";
        }

        List<String> parents = inherits.stream()
                .peek(inherit -> {
                    requireValidIdentifier(inherit.getSchema(), "inherit schema");
                    requireValidIdentifier(inherit.getName(), "inherit table");
                })
                .map(inherit -> qualifiedName(inherit.getSchema(), inherit.getName()))
                .toList();
        return "inherits (" + String.join(", ", parents) + ")";
    }

    private DBInfo.Type getDatabaseType() {
        return info.getDatabaseType();
    }

    private TableBuilderSqlDialect createDialect() {
        return switch (getDatabaseType()) {
            case POSTGRESQL -> new PostgresTableBuilderSqlDialect();
            default -> new MySqlTableBuilderSqlDialect();
        };
    }

    private void requireValidIdentifier(String identifier, String type) {
        if (!SchemaBuildRules.isValidIdentifier(identifier)) {
            throw new MuYunDatabaseException("Invalid " + type + " identifier: " + identifier);
        }
    }

    private String qualifiedName(String schema, String table) {
        return SchemaBuildRules.qualifiedName(schema, table, getDatabaseType());
    }

}
