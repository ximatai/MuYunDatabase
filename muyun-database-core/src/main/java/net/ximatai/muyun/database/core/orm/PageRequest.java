package net.ximatai.muyun.database.core.orm;

public class PageRequest {
    private final int offset;
    private final int limit;

    public PageRequest(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        this.offset = offset;
        this.limit = limit;
    }

    public static PageRequest of(int pageNumber, int pageSize) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 1");
        }
        return new PageRequest((pageNumber - 1) * pageSize, pageSize);
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }
}
