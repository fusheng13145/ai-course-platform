package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 行政班新建/修改请求（V14 / B-12-P-08 WP4 组织表 CRUD）。
 */
@Data
public class OrgAdminClassDTO {

    @NotNull(message = "所属学院ID不能为空")
    private Long collegeId;

    @NotNull(message = "所属专业ID不能为空")
    private Long majorId;

    @NotBlank(message = "行政班名称不能为空")
    @Size(max = 100, message = "行政班名称不能超过 100 个字符")
    private String name;

    @NotBlank(message = "行政班编码不能为空")
    @Size(max = 50, message = "行政班编码不能超过 50 个字符")
    private String code;

    /** 年级，如 2024 / 2025。 */
    @Size(max = 10, message = "年级不能超过 10 个字符")
    private String gradeYear;

    /** 班主任教师ID（可选）。 */
    private Long advisorTeacherId;

    /** 最大容纳人数（可选）。 */
    private Integer maxStudents;

    /** ACTIVE / ARCHIVED；省略按 ACTIVE。 */
    private String status;
}
