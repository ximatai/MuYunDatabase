package net.ximatai.muyun.database.spring.boot;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.metadata.DBInfo;
import net.ximatai.muyun.database.core.orm.DefaultSimpleEntityManager;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.OrmException;
import net.ximatai.muyun.database.core.orm.SchemaManager;
import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
import net.ximatai.muyun.database.jdbi.JdbiTransactionRunner;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@Tag("db")
@Testcontainers
class TransactionalSchemaDiscoveryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private HikariDataSource dataSource;
    private Jdbi jdbi;
    private HookedLoader loader;
    private JdbiDatabaseOperations<String> operations;
    private MuYunSchemaManager manager;
    private TransactionTemplate transactions;
    private String tableName;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        jdbi = Jdbi.create(new TransactionAwareDataSourceProxy(dataSource));
        loader = new HookedLoader(jdbi);
        operations = new JdbiDatabaseOperations<>(jdbi, loader, String.class, "id");
        manager = new MuYunSchemaManager(new DefaultSimpleEntityManager(operations), MigrationOptions.execute(), operations);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tableName = "discovery_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void tearDown() {
        try {
            jdbi.useHandle(handle -> handle.execute("drop table if exists " + tableName));
        } finally {
            dataSource.close();
        }
    }

    private TableWrapper model() {
        return TableWrapper.withName(tableName)
                .setPrimaryKey("id")
                .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
                .addIndex("code");
    }

    @Test
    void configuredDefaultSchemaShouldBePreservedWithoutLeakingUncommittedSchema() {
        String schemaName = tableName + "_s";
        var configuredOperations = new StarterJdbiDatabaseOperations<>(jdbi, loader, String.class, "id", schemaName);
        SchemaManager configuredManager = new SchemaManager(configuredOperations);
        transactions.executeWithoutResult(status -> {
            assertTrue(configuredManager.ensureTable(model()));
            assertFalse(configuredManager.ensureTable(model()));
            assertNull(onOtherThread(() -> loader.getDBInfo().getSchema(schemaName)));
            configuredOperations.withFreshDBInfo(info -> {
                assertTrue(info.getSchema(schemaName).containsTable(tableName));
                return null;
            });
            status.setRollbackOnly();
        });
        loader.withFreshDBInfo(info -> {
            assertNull(info.getSchema(schemaName));
            return null;
        });
    }

    @Test
    void repeatedSpringMigrationShouldRollbackThenRecreateAndCommit() {
        transactions.executeWithoutResult(status -> {
            assertTrue(manager.ensureTable(model()).isChanged());
            assertFalse(manager.ensureTable(model()).isChanged());
            assertFalse(otherConnectionSeesTable());
            status.setRollbackOnly();
        });
        assertFalse(otherConnectionSeesTable());
        transactions.executeWithoutResult(status -> {
            assertTrue(manager.ensureTable(model()).isChanged());
            assertFalse(manager.ensureTable(model()).isChanged());
        });
        assertTrue(otherConnectionSeesTable());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observerRefreshMustNotReplaceTransactionDiscovery(boolean afterResetBeforeDiscovery) {
        transactions.executeWithoutResult(status -> {
            assertTrue(manager.ensureTable(model()).isChanged());
            if (afterResetBeforeDiscovery) {
                operations.resetDBInfo();
                // The observer must finish after reset and before the planner
                // obtains its metadata. Future.get is the deterministic barrier.
                loader.beforeDiscovery = () -> assertFalse(otherConnectionSeesTable());
            } else {
                assertFalse(otherConnectionSeesTable());
            }
            assertFalse(manager.ensureTable(model()).isChanged());
            assertEquals(afterResetBeforeDiscovery ? 1 : 0, loader.hookCalls);
            status.setRollbackOnly();
        });
        assertFalse(otherConnectionSeesTable());
        transactions.executeWithoutResult(status -> assertTrue(manager.ensureTable(model()).isChanged()));
        assertTrue(otherConnectionSeesTable());
    }

    @Test
    void lazyMetadataShouldStayOnTransactionConnectionWithoutPublishingItsSnapshot() {
        assertFalse(otherConnectionSeesTable());
        DBInfo committedSnapshot = loader.getDBInfo();
        transactions.executeWithoutResult(status -> {
            operations.execute("create table " + tableName + " (id varchar primary key, code varchar(64), "
                    + "parent_id varchar, constraint uq_" + tableName + " unique (code), "
                    + "constraint fk_" + tableName + " foreign key (parent_id) references " + tableName + "(id))");
            operations.execute("create index ix_" + tableName + " on " + tableName + "(parent_id)");
            operations.withFreshDBInfo(info -> {
                var table = info.getDefaultSchema().getTable(tableName);
                assertFalse(otherConnectionSeesTable());
                DBInfo observerSnapshot = loader.getDBInfo();
                assertNotSame(info, observerSnapshot);
                assertEquals(3, table.getColumnMap().size());
                assertEquals(List.of("id"), table.getPrimaryKey().columns());
                assertEquals(1, table.getUniqueConstraints().size());
                assertEquals(1, table.getForeignKeys().size());
                assertEquals(1, table.getIndexList().size());
                assertEquals(List.of("parent_id"), table.getIndexList().getFirst().getColumns());
                assertSame(observerSnapshot, loader.getDBInfo());
                return null;
            });
            assertFalse(committedSnapshot.getDefaultSchema().containsTable(tableName));
            status.setRollbackOnly();
        });
        assertFalse(otherConnectionSeesTable());
    }

    @Test
    void explicitJdbiTransactionHandleShouldUseFreshMetadata() {
        // Deliberately use a plain DataSource, without Spring transaction binding.
        Jdbi plainJdbi = Jdbi.create(dataSource);
        JdbiMetaDataLoader plainLoader = new JdbiMetaDataLoader(plainJdbi);
        JdbiTransactionRunner<String> runner = new JdbiTransactionRunner<>(plainJdbi, plainLoader, String.class, "id");
        runner.inTransactionVoid(tx -> {
            SchemaManager schema = new SchemaManager(tx.getOperations());
            assertTrue(schema.ensureTable(model()));
            onOtherThread(() -> {
                plainLoader.resetInfo();
                assertFalse(plainLoader.getDBInfo().getDefaultSchema().containsTable(tableName));
                return null;
            });
            assertFalse(schema.ensureTable(model()));
            tx.getHandle().rollback();
        });
        assertFalse(otherConnectionSeesTable());
        runner.inTransactionVoid(tx -> assertTrue(new SchemaManager(tx.getOperations()).ensureTable(model())));
        assertTrue(otherConnectionSeesTable());
    }

    @Test
    void dryRunStrictAndFailedMigrationShouldLeaveDiscoveryUsable() {
        transactions.executeWithoutResult(status -> {
            SchemaManager schema = new SchemaManager(operations);
            assertTrue(schema.ensureTable(model(), MigrationOptions.dryRunStrict()).isChanged());
            assertFalse(otherConnectionSeesTable());
            assertTrue(schema.ensureTable(model(), MigrationOptions.strict()).isChanged());
            assertFalse(otherConnectionSeesTable());
            assertFalse(schema.ensureTable(model(), MigrationOptions.dryRunStrict()).isChanged());
            TableWrapper shrinking = TableWrapper.withName(tableName)
                    .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(8));
            loader.resetInfo();
            assertThrows(OrmException.class, () -> schema.ensureTable(shrinking, MigrationOptions.strict()));
            assertFalse(onOtherThread(() -> loader.getDBInfo().getDefaultSchema().containsTable(tableName)));
            assertFalse(schema.ensureTable(model(), MigrationOptions.strict()).isChanged());
            status.setRollbackOnly();
        });
        assertFalse(otherConnectionSeesTable());
        assertThrows(RuntimeException.class, () -> transactions.executeWithoutResult(status ->
                manager.ensureTable(TableWrapper.withName(tableName)
                        .addColumn(Column.of("code").setType(ColumnType.VARCHAR)
                                .setDefaultValueAny("missing_discovery_function()")))));
        assertFalse(otherConnectionSeesTable());
        transactions.executeWithoutResult(status -> assertTrue(manager.ensureTable(model()).isChanged()));
        assertTrue(otherConnectionSeesTable());
    }

    private boolean otherConnectionSeesTable() {
        return onOtherThread(() -> {
            loader.resetInfo();
            return loader.getDBInfo().getDefaultSchema().containsTable(tableName);
        });
    }

    private static <T> T onOtherThread(Callable<T> callback) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            return executor.submit(callback).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Observer connection failed", e);
        }
    }

    private static final class HookedLoader extends JdbiMetaDataLoader {
        private Runnable beforeDiscovery;
        private int hookCalls;

        private HookedLoader(Jdbi jdbi) {
            super(jdbi);
        }

        @Override
        public DBInfo getDBInfo() {
            runHook();
            return super.getDBInfo();
        }

        @Override
        public <R> R withFreshDBInfo(Function<DBInfo, R> callback) {
            runHook();
            return super.withFreshDBInfo(callback);
        }

        private void runHook() {
            Runnable hook = beforeDiscovery;
            beforeDiscovery = null;
            if (hook != null) {
                hookCalls++;
                hook.run();
            }
        }
    }
}
