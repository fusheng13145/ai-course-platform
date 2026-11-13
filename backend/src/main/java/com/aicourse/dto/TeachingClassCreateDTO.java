package com.aicourse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建教学班请求（V14 / B-12-P-08 WP4）。
 */
@Data
public class TeachingClassCreateDTO {

    /** 课程ID（必填）。 */
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 教学班名称；省略时使用「课程名 默认教学班」。 */
    @Size(max = 120, message = "教学班名称不能超过 120 个字符")
    private String name;

    /** 教学班编号（可选）。 */
    @Size(max = 50, message = "教学班编号不能超过 50 个字符")
    private String code;

    /** 主讲教师ID；省略时使用课程负责人。 */
    private Long leadTeacherId;

    /** 容量；省略表示不限制。 */
    private Integer capacity;
}
