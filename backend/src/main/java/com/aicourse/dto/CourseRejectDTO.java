package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 课程审批驳回请求体（3E-04B）。
 *
 * <p>原 {@code POST /courses/{id}/reject} 用 {@code Map<String, String>} 接参，
 * 键 {@code reason} 靠字符串约定，且 {@code @RequestBody(required=false)}；
 * 收成 DTO 后字段名即契约，整体仍可缺省。</p>
 */
@Schema(description = "课程审批驳回请求体")
public class CourseRejectDTO {

    @Schema(description = "驳回原因（可选）", example = "课程目标描述不完整")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
