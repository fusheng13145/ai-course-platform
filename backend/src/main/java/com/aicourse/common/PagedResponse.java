package com.aicourse.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public class PagedResponse<T> {
    private List<T> list;
    private long total;
    private long page;
    private long pageSize;
    private long totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> PagedResponse<T> of(IPage<T> page) {
        PagedResponse<T> resp = new PagedResponse<>();
        resp.list = page.getRecords();
        resp.total = page.getTotal();
        resp.page = page.getCurrent();
        resp.pageSize = page.getSize();
        resp.totalPages = page.getPages();
        resp.hasNext = page.getCurrent() < page.getPages();
        resp.hasPrevious = page.getCurrent() > 1;
        return resp;
    }

    public static <T> PagedResponse<T> of(List<T> list, long total, long page, long pageSize) {
        PagedResponse<T> resp = new PagedResponse<>();
        resp.list = list;
        resp.total = total;
        resp.page = page;
        resp.pageSize = pageSize;
        long pages = (long) Math.ceil((double) total / pageSize);
        resp.totalPages = pages;
        resp.hasNext = page < pages;
        resp.hasPrevious = page > 1;
        return resp;
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }

    public long getPageSize() { return pageSize; }
    public void setPageSize(long pageSize) { this.pageSize = pageSize; }

    public long getTotalPages() { return totalPages; }
    public void setTotalPages(long totalPages) { this.totalPages = totalPages; }

    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }

    public boolean isHasPrevious() { return hasPrevious; }
    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
}
