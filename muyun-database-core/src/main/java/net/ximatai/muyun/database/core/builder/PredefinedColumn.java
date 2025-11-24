package net.ximatai.muyun.database.core.builder;

public final class PredefinedColumn {

    public enum Id {
        CUSTOM,
        POSTGRES,
        MYSQL;

        public Column toColumn() {
            switch (this) {
                case POSTGRES:
                    return Ids.POSTGRES;
                case MYSQL:
                    return Ids.MYSQL;
                default:
                    throw new IllegalArgumentException("Unsupported id type: " + this);
            }
        }
    }

    public static final class Ids {
        public static final Column POSTGRES = new Column("id")
                .setPrimaryKey()
                .setType(ColumnType.VARCHAR)
                .setDefaultValueAny("gen_random_uuid()");

        public static final Column MYSQL = new Column("id")
                .setPrimaryKey()
                .setType(ColumnType.BIGINT)
                .setSequence()
                .setDefaultValueAny("AUTO_INCREMENT");
    }

    public static final class System {
        public static final Column DELETE_FLAG = new Column("b_delete")
                .setType(ColumnType.BOOLEAN)
                .setDefaultValueAny("false");

        public static final Column TREE_PID = new Column("pid")
                .setType(ColumnType.VARCHAR)
                .setIndexed();

        public static final Column ORDER = new Column("n_order")
                .setSequence()
                .setIndexed();

        public static final Column CODE = new Column("v_code")
                .setType(ColumnType.VARCHAR)
                .setIndexed();

        public static final Column CREATE = new Column("t_create")
                .setIndexed();
    }

}
