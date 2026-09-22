package net.ximatai.muyun.database.core.builder;

public interface IColumnTypeTransform {

    IColumnTypeTransform DEFAULT = type -> switch (type) {
        case SET, JSON_SET -> "text";
        case UUID -> "uuid";
        case TIMESTAMP_WITH_TIME_ZONE -> "timestamp with time zone";
        case DOUBLE -> "double precision";
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
            case LONGTEXT:
                return "text";
            case UUID:
                return "uuid";
            case TIMESTAMP_WITH_TIME_ZONE:
                return "timestamp with time zone";
            case DOUBLE:
                return "double precision";
            default:
                return type.name();
        }
    };

    String transform(ColumnType type);

}
