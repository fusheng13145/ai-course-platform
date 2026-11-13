package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学院新建/修改请求（V14 / B-12-P-08 WP4 组织表 CRUD）。
 */
@Data
public class OrgCollegeDTO {

    @NotBlank(message = "学院编码不能为空")
    @Size(max = 50, message = "学院编码不能超过 50 个字符")
    private String code;

    @NotBlank(message = "学院名称不能为空")
    @Size(max = 100, message = "学院名称不能超过 100 个字符")
    private String name;

    /** ACTIVE / ARCHIVED；省略按 ACTIVE。 */
    private String status;
}
