package com.aicourse.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改教学班基础信息请求（V14 / B-12-P-08 WP4）。
 * 字段为 null 表示不修改；主讲变更走独立端点。
 */
@Data
public class TeachingClassUpdateDTO {

    @Size(max = 120, message = "教学班名称不能超过 120 个字符")
    private String name;

    @Size(max = 50, message = "教学班编号不能超过 50 个字符")
    private String code;

    private Integer capacity;

    /** DRAFT / ACTIVE / CLOSED；ACTIVE 要求课程已通过审批且启用。 */
    private String status;
}
