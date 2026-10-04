package net.ximatai.muyun.database.core.builder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableWrapperTest {

    @Test
    void shouldSeparateNamedAndColumnIndexDropSelectors() {
        List<String> columns = new ArrayList<>(List.of("code"));
        TableWrapper table = TableWrapper.withName("record").dropIndexByName("unique_code").dropIndex(columns);
        columns.add("tenant_id");

        assertEquals(List.of(IndexDrop.byName("unique_code"), IndexDrop.byColumns(List.of("code"))), table.getDroppedIndexes());
        assertThrows(UnsupportedOperationException.class, () -> table.getDroppedIndexes().get(1).columns().add("other"));
    }

    @Test
    void shouldRejectInvalidIndexDropSelectorsInsteadOfFallingBackToColumns() {
        TableWrapper table = TableWrapper.withName("record");
        assertThrows(IllegalArgumentException.class, () -> table.dropIndexByName(" "));
        assertThrows(NullPointerException.class, () -> table.dropIndexByName(null));
        assertThrows(IllegalArgumentException.class, () -> table.dropIndex(List.of()));
        assertThrows(IllegalArgumentException.class, () -> table.dropIndex(List.of(" ")));
        assertThrows(IllegalArgumentException.class, () -> new IndexDrop("lookup", List.of("code")));
    }

    @Test
    void shouldRejectCompositePrimaryKeyAfterSingleColumnPrimaryKey() {
        TableWrapper table = TableWrapper.withName("record")
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR))
                .setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR));

        assertThrows(IllegalStateException.class,
                () -> table.setPrimaryKey(PrimaryKeyConstraint.of("tenant_id", "id")));
    }

    @Test
    void shouldRejectSingleColumnPrimaryKeyAfterCompositePrimaryKey() {
        TableWrapper table = TableWrapper.withName("record")
                .addColumn(Column.of("tenant_id").setType(ColumnType.VARCHAR))
                .addColumn(Column.of("id").setType(ColumnType.VARCHAR))
                .setPrimaryKey(PrimaryKeyConstraint.of("tenant_id", "id"));

        assertThrows(IllegalStateException.class,
                () -> table.setPrimaryKey(Column.of("id").setType(ColumnType.VARCHAR)));
    }
}
