package net.ximatai.muyun.database.core.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TableWrapperTest {

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
