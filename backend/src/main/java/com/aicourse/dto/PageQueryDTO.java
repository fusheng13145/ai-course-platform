package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询基类DTO
 * 提供通用的分页参数，供各查询接口继承或使用
 */
@Schema(description = "分页查询参数")
public class PageQueryDTO {

    @Schema(description = "页码（从1开始）", example = "1", minimum = "1")
    private Integer page = 1;

    @Schema(description = "每页大小", example = "10", minimum = "1", maximum = "100")
    private Integer size = 10;

    @Schema(description = "排序字段", example = "createdAt")
    private String sortBy;

    @Schema(description = "排序方向", example = "desc", allowableValues = {"asc", "desc"})
    private String sortOrder = "desc";

    @Schema(description = "搜索关键词", example = "Java")
    private String keyword;

    // ====================== Getter & Setter ======================

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page != null && page > 0 ? page : 1;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size != null && size > 0 ? size : 10;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
