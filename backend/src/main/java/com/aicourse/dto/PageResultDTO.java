package com.aicourse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 分页响应包装器（泛型）
 * 用于统一封装所有分页查询接口的返回数据格式
 *
 * @param <T> 数据记录的类型
 */
@Schema(description = "分页响应结果")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResultDTO<T> {

    @Schema(description = "当前页数据记录列表")
    private List<T> records;

    @Schema(description = "总记录数", example = "156")
    private Long total;

    @Schema(description = "每页大小", example = "10")
    private Integer size;

    @Schema(description = "当前页码（从1开始）", example = "1")
    private Long current;

    @Schema(description = "总页数", example = "16")
    private Long pages;

    // ====================== 构造方法 ======================

    public PageResultDTO() {
    }

    public PageResultDTO(List<T> records, Long total, Integer size, Long current) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        if (total != null && size != null && size > 0) {
            this.pages = (total + size - 1) / size;
        }
    }

    /**
     * 快速构建空结果
     */
    public static <T> PageResultDTO<T> empty(Integer size) {
        PageResultDTO<T> result = new PageResultDTO<>();
        result.setRecords(List.of());
        result.setTotal(0L);
        result.setSize(size);
        result.setCurrent(1L);
        result.setPages(0L);
        return result;
    }

    // ====================== Getter & Setter ======================

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getCurrent() {
        return current;
    }

    public void setCurrent(Long current) {
        this.current = current;
    }

    public Long getPages() {
        return pages;
    }

    public void setPages(Long pages) {
        this.pages = pages;
    }
}
