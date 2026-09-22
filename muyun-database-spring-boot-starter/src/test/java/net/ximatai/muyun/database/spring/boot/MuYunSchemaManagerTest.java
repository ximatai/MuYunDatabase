package net.ximatai.muyun.database.spring.boot;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.database.core.orm.EntityMetaResolver;
import net.ximatai.muyun.database.core.orm.ManagedTable;
import net.ximatai.muyun.database.core.orm.MigrationOptions;
import net.ximatai.muyun.database.core.orm.MigrationResult;
import net.ximatai.muyun.database.core.orm.OrmException;
import net.ximatai.muyun.database.core.orm.SimpleEntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class MuYunSchemaManagerTest {

    @Test
    void shouldSortForeignKeyDependenciesAcrossSchemaSources() {
        TableWrapper parentTable = TableWrapper.withName("business_parent")
                .setPrimaryKey("id");
        TableWrapper childTable = TableWrapper.withName("technical_child")
                .setPrimaryKey("id")
                .addColumn(Column.of("parent_id").setType(ColumnType.VARCHAR).setLength(64).setNullable(false))
                .addForeignKey(ForeignKeyConstraint.named(
                        "fk_child_parent",
                        List.of("parent_id"),
                        "business_parent",
                        List.of("id"),
                        ForeignKeyAction.NO_ACTION
                ));
        ManagedTable parent = ManagedTable.of("repository:parent", parentTable);
        ManagedTable child = ManagedTable.of("technical", childTable);
        MuYunSchemaManager manager = spy(manager());
        doReturn(MigrationResult.empty(MigrationOptions.dryRun()))
                .when(manager).ensureTable(any(TableWrapper.class));

        manager.ensureTables(List.of(child, parent));

        InOrder order = inOrder(manager);
        order.verify(manager).ensureTable(parentTable);
        order.verify(manager).ensureTable(childTable);
    }

    @Test
    void shouldRejectDuplicatePhysicalTableAcrossSchemaSources() {
        ManagedTable contributed = ManagedTable.of("technical", TableWrapper.withName("shared"));
        ManagedTable repository = ManagedTable.of("repository:entity", TableWrapper.withName("shared"));
        MuYunSchemaManager manager = manager();

        assertThrows(OrmException.class, () -> manager.ensureTables(List.of(contributed, repository)));
    }

    @Test
    void shouldConvertEntityUsingInjectedResolver() {
        TableWrapper table = TableWrapper.withName("resolved_entity")
                .setPrimaryKey("id");
        EntityMetaResolver resolver = new EntityMetaResolver(ignored -> table);
        MuYunSchemaManager manager = new MuYunSchemaManager(
                mock(SimpleEntityManager.class),
                MigrationOptions.dryRun(),
                mock(IDatabaseOperations.class),
                resolver
        );

        ManagedTable managed = manager.managedTableFor(ResolvedEntity.class);

        assertEquals("repository:" + ResolvedEntity.class.getName(), managed.id());
        assertEquals(table, managed.table());
    }

    private MuYunSchemaManager manager() {
        return new MuYunSchemaManager(
                mock(SimpleEntityManager.class),
                MigrationOptions.dryRun(),
                mock(IDatabaseOperations.class)
        );
    }

    private static final class ResolvedEntity {
        @Id
        private String id;
    }
}
