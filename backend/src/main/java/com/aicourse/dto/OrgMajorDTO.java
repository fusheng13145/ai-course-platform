package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 专业新建/修改请求（V14 / B-12-P-08 WP4 组织表 CRUD）。
 */
@Data
public class OrgMajorDTO {

    @NotNull(message = "所属学院ID不能为空")
    private Long collegeId;

    @NotBlank(message = "专业编码不能为空")
    @Size(max = 50, message = "专业编码不能超过 50 个字符")
    private String code;

    @NotBlank(message = "专业名称不能为空")
    @Size(max = 100, message = "专业名称不能超过 100 个字符")
    private String name;

    /** ACTIVE / ARCHIVED；省略按 ACTIVE。 */
    private String status;
}
