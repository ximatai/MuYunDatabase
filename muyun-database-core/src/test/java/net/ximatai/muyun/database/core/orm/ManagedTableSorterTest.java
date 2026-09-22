package net.ximatai.muyun.database.core.orm;

import net.ximatai.muyun.database.core.builder.Column;
import net.ximatai.muyun.database.core.builder.ColumnType;
import net.ximatai.muyun.database.core.builder.ForeignKeyAction;
import net.ximatai.muyun.database.core.builder.ForeignKeyConstraint;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedTableSorterTest {

    @Test
    void shouldInferForeignKeyDependencyAndSortParentFirst() {
        ManagedTable child = new ManagedTable("child", TableWrapper.withName("child")
                .setSchema("public")
                .addColumn(Column.of("parent_id").setType(ColumnType.UUID))
                .addForeignKey(ForeignKeyConstraint.named(
                        "fk_child_parent", List.of("parent_id"), "parent", List.of("id"), ForeignKeyAction.CASCADE)), Set.of());
        ManagedTable parent = ManagedTable.of("parent", TableWrapper.withName("parent").setSchema("public")
                .addColumn(Column.of("id").setType(ColumnType.UUID)));

        assertEquals(List.of(parent, child), ManagedTableSorter.sort(List.of(child, parent)));
    }

    @Test
    void shouldRejectUnknownAndCyclicDependencies() {
        ManagedTable unknown = new ManagedTable("a", TableWrapper.withName("a"), Set.of("missing"));
        assertThrows(OrmException.class, () -> ManagedTableSorter.sort(List.of(unknown)));

        ManagedTable a = new ManagedTable("a", TableWrapper.withName("a"), Set.of("b"));
        ManagedTable b = new ManagedTable("b", TableWrapper.withName("b"), Set.of("a"));
        assertThrows(OrmException.class, () -> ManagedTableSorter.sort(List.of(a, b)));
    }

    @Test
    void shouldRejectDuplicatePhysicalTableDeclarations() {
        ManagedTable first = ManagedTable.of("first", TableWrapper.withName("shared").setSchema("public"));
        ManagedTable second = ManagedTable.of("second", TableWrapper.withName("shared").setSchema("public"));

        assertThrows(OrmException.class, () -> ManagedTableSorter.sort(List.of(first, second)));
    }
}
