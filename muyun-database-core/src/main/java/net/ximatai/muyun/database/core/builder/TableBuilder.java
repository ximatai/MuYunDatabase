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
        requireSafeIdentifier(schema, "schema");
        requireSafeIdentifier(name, "table");

        logger.info("Table %s.%s build initiated".formatted(schema, name));

        String inheritSQL = "";

        if (info.getSchema(schema) == null) {
            db.execute(dialect.createSchemaIfNotExists(schema));
            info.addSchema(new DBSchema(schema));
            logger.info("create schema " + schema);
        }

        List<TableBase> inherits = wrapper.getInherits();

        // 检查继承的父表是否存在
        inherits.forEach(inherit -> {
            requireSafeIdentifier(inherit.getSchema(), "inherit schema");
            requireSafeIdentifier(inherit.getName(), "inherit table");
            DBSchema parentSchema = info.getSchema(inherit.getSchema());
            if (parentSchema == null || !parentSchema.containsTable(inherit.getName())) {
                throw new MuYunDatabaseException("Table " + inherit + " does not exist");
            }
        });

        if (getDatabaseType().equals(POSTGRESQL)) {
            inheritSQL = buildInheritSQLForPostgres(inherits);
        }

        if (!info.getSchema(schema).containsTable(wrapper.getName())) {
            db.execute(dialect.createTableWithTempColumn(schema + "." + name, inheritSQL));
            logger.info("create table " + schema + "." + name);
            result = true;
            info.getSchema(schema).addTable(new DBTable(db.getMetaDataLoader()).setName(name).setSchema(schema));
        }

        DBTable dbTable = info.getSchema(schema).getTable(wrapper.getName());

        buildInheritColumns(dbTable, inherits);

        if (wrapper.getComment() != null) {
            db.execute(dialect.setTableComment(schema + "." + name, wrapper.getComment()));
        }

        if (wrapper.getPrimaryKey() != null) {
            checkAndBuildColumn(dbTable, wrapper.getPrimaryKey());
            dbTable.resetColumns();
        }

        wrapper.getColumns().forEach(column -> {
            checkAndBuildColumn(dbTable, column);
        });

        if (result) {
            db.execute(dialect.dropTempColumn(schema + "." + name));
        }

        dbTable.resetColumns();

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
                        "WHERE inhparent = ?::regclass AND inhrelid = ?::regclass;", inherit.getSchemaDotTable(), dbTable.getSchemaDotTable());

                if (row == null) {
                    db.execute("alter table " + dbTable.getSchemaDotTable() + " inherit " + inherit.getSchemaDotTable() + ";");
                    logger.info("table " + dbTable.getSchemaDotTable() + " inherit " + inherit.getSchemaDotTable());
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
        boolean isNew = false;
        String name = column.getName();
        requireSafeIdentifier(name, "column");
        ColumnType dataType = column.getType();
        String type = SchemaBuildRules.columnTypeTransform(getDatabaseType()).transform(dataType);

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

        String baseColumnString = SchemaBuildRules.columnDefinition(column, type);

        if (!dbTable.contains(name)) {
            db.execute(dialect.addColumn(dbTable.getSchemaDotTable(), baseColumnString));
            dbTable.resetColumns();
            isNew = true;
            logger.info("column " + dbTable.getSchemaDotTable() + "." + name + " built");
        }

        DBColumn dbColumn = dbTable.getColumn(name);

        if (!type.equalsIgnoreCase(dbColumn.getType()) || column.getLength() != null && !column.getLength().equals(dbColumn.getLength())) {
            db.execute(dialect.alterColumnType(dbTable.getSchemaDotTable(), name, type + length, baseColumnString));
        }

        if (primaryKey && !dbColumn.isPrimaryKey()) {
            db.execute("alter table " + dbTable.getSchemaDotTable() + " add primary key (" + name + ")");
        }

        if (dbColumn.isNullable() != nullable) {
            db.execute(dialect.alterColumnNullable(dbTable.getSchemaDotTable(), name, nullable, baseColumnString));

        }

        if ((!dbColumn.isSequence() && !Objects.equals(dbColumn.getDefaultValueWithString(), defaultValue))) {
            db.execute(dialect.alterColumnDefault(dbTable.getSchemaDotTable(), name, defaultValue, baseColumnString));
        }

        if (dbColumn.isSequence() != sequence) {
            dialect.alterColumnSequence(dbTable.getSchemaDotTable(), dbTable.getSchema(), dbTable.getName(), name, sequence)
                    .forEach(db::execute);
        }

        if (comment != null && !Objects.equals(dbColumn.getDescription(), comment)) {
            db.execute(dialect.setColumnComment(dbTable.getSchemaDotTable(), name, comment, baseColumnString));
        }

        return isNew;
    }

    private boolean checkAndBuildIndex(DBTable dbTable, Index index) {
        List<String> columns = new ArrayList<>(index.getColumns());
        columns.forEach(columnName -> requireSafeIdentifier(columnName, "index column"));
        Set<String> columnSet = new HashSet<>(columns);
        List<DBIndex> indexList = dbTable.getIndexList();
        Optional<DBIndex> dbIndexOptional = indexList.stream().filter(i -> new HashSet<>(i.getColumns()).equals(columnSet)).findFirst();

        if (dbIndexOptional.isPresent()) {
            DBIndex dbIndex = dbIndexOptional.get();
            if (dbIndex.isUnique() == index.isUnique()) {
                return false;
            } else {
                db.execute(dialect.dropIndex(dbTable.getSchema(), dbTable.getSchemaDotTable(), dbIndex.getName()));
                logger.info("index " + dbTable.getSchemaDotTable() + "." + dbIndex.getName() + " dropped");
            }

        }

        String indexName = SchemaBuildRules.indexName(dbTable.getName(), index);
        requireSafeIdentifier(indexName, "index");

        db.execute(dialect.createIndex(dbTable.getSchemaDotTable(), indexName, columns, index.isUnique()));

        logger.info("index " + dbTable.getSchemaDotTable() + "." + indexName + " created");

        return true;
    }

    private String buildInheritSQLForPostgres(List<TableBase> inherits) {
        if (inherits.isEmpty()) {
            return "";
        }

        List<String> parents = inherits.stream()
                .peek(inherit -> {
                    requireSafeIdentifier(inherit.getSchema(), "inherit schema");
                    requireSafeIdentifier(inherit.getName(), "inherit table");
                })
                .map(inherit -> inherit.getSchema() + "." + inherit.getName())
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

    private void requireSafeIdentifier(String identifier, String type) {
        if (!SchemaBuildRules.isSafeIdentifier(identifier)) {
            throw new MuYunDatabaseException("Invalid " + type + " identifier: " + identifier);
        }
    }

}
