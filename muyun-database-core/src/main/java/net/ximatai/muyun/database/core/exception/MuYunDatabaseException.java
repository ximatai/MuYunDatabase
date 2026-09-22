package net.ximatai.muyun.database.core.exception;

public class MuYunDatabaseException extends RuntimeException {

    private final Type type;

    public enum Type {
        DEFAULT,
        DATA_NOT_FOUND,
        READ_METADATA_ERROR,
    }

    @Override
    public String getMessage() {

        switch (type) {
            case DATA_NOT_FOUND:
                return "操作的数据不存在";
            case READ_METADATA_ERROR:
                return "元数据读取失败，" + super.getMessage();
        }

        return super.getMessage();
    }

    public MuYunDatabaseException(Type type) {
        this.type = type;
    }

    public MuYunDatabaseException(String message) {
        super(message);
        this.type = Type.DEFAULT;
    }

    public MuYunDatabaseException(String message, Type type) {
        super(message);
        this.type = type;
    }

    public MuYunDatabaseException(String message, Type type, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
