package net.ximatai.muyun.database.core.builder;

public interface IColumnTypeTransform {

    IColumnTypeTransform DEFAULT = type -> switch (type) {
        case SET, JSON_SET -> "text";
        default -> type.name();
    };

    IColumnTypeTransform POSTGRESQL = type -> {
        switch (type) {
            case VARCHAR_ARRAY:
                return "varchar[]";
            case INT_ARRAY:
                return "int[]";
            case JSON:
                return "jsonb";
            case SET:
            case JSON_SET:
                return "text";
            default:
                return type.name();
        }
    };

    String transform(ColumnType type);

}
