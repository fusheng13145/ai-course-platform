package com.aicourse.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 分页统一返回实体
 * @param <T> 分页数据泛型
 */
public class PagedResponse<T> {

    /** 分页数据列表 */
    private List<T> list;
    /** 总数据条数 */
    private long total;
    /** 当前页码 */
    private long page;
    /** 每页条数 */
    private long pageSize;
    /** 总页数 */
    private long totalPages;
    /** 是否有下一页 */
    private boolean hasNext;
    /** 是否有上一页 */
    private boolean hasPrevious;

    /**
     * 基于 MyBatis-Plus IPage 构建分页响应
     * @param page MyBatis-Plus 分页对象
     * @return 分页返回实体
     */
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

    /**
     * 手动传入分页参数构建分页响应
     * @param list 数据列表
     * @param total 总条数
     * @param page 当前页码
     * @param pageSize 每页条数
     * @return 分页返回实体
     */
    public static <T> PagedResponse<T> of(List<T> list, long total, long page, long pageSize) {
        PagedResponse<T> resp = new PagedResponse<>();
        resp.list = list;
        resp.total = total;
        resp.page = page;
        resp.pageSize = pageSize;

        // 计算总页数，兼容 pageSize = 0 边界
        long totalPages = 0;
        if (pageSize > 0) {
            totalPages = (long) Math.ceil((double) total / pageSize);
        }
        resp.totalPages = totalPages;
        resp.hasNext = page < totalPages;
        resp.hasPrevious = page > 1;
        return resp;
    }

    // ===================== Getter & Setter =====================
    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(long totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }
}