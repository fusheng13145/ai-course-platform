package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 复制任务请求体（3E-04A）。
 *
 * <p>原 {@code POST /tasks/{id}/copy} 用 {@code Map<String, String>} 接参，键名
 * {@code newTitle} 靠字符串约定；收成 DTO 后字段名即契约，缺省仍表示「沿用原标题」。</p>
 */
@Schema(description = "复制任务请求体")
public class TaskCopyDTO {

    @Schema(description = "新标题（缺省沿用原标题）", example = "作业1-副本")
    private String newTitle;

    public String getNewTitle() {
        return newTitle;
    }

    public void setNewTitle(String newTitle) {
        this.newTitle = newTitle;
    }
}
