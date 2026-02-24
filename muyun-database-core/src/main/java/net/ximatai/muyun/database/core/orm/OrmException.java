package net.ximatai.muyun.database.core.orm;

public class OrmException extends RuntimeException {

    public enum Code {
        INVALID_ENTITY,
        INVALID_MAPPING,
        INVALID_CRITERIA,
        STRICT_MIGRATION_REJECTED
    }

    private final Code code;

    public OrmException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public OrmException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
