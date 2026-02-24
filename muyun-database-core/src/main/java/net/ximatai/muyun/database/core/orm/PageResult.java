package net.ximatai.muyun.database.core.orm;

import java.util.List;
import java.util.Objects;

public class PageResult<T> {

    public static final long UNKNOWN_TOTAL = -1L;
    public static final long UNKNOWN_PAGES = -1L;

    private final List<T> records;
    private final long total;
    private final int pageNum;
    private final int pageSize;
    private final long pages;

    public PageResult(List<T> records, long total, int pageNum, int pageSize, long pages) {
        this.records = Objects.requireNonNull(records, "records must not be null");
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pages;
    }

    public static <T> PageResult<T> of(List<T> records, long total, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        int pageSize = pageRequest.getLimit();
        int pageNum = pageRequest.getOffset() / pageSize + 1;
        long pages = total < 0 ? UNKNOWN_PAGES : (total == 0 ? 0 : (total + pageSize - 1) / pageSize);
        return new PageResult<>(records, total, pageNum, pageSize, pages);
    }

    public static <T> PageResult<T> unknownTotal(List<T> records, PageRequest pageRequest) {
        return of(records, UNKNOWN_TOTAL, pageRequest);
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getPages() {
        return pages;
    }

    public boolean isTotalKnown() {
        return total >= 0;
    }
}
