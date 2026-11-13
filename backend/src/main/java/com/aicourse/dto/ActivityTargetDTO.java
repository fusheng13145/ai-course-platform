package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 教学活动目标教学班设置请求（V15 / B-12-P-08 WP6）。
 */
@Data
public class ActivityTargetDTO {

    /**
     * 可见范围：COURSE / CLASSES
     */
    @NotBlank(message = "可见范围不能为空")
    private String visibility;

    /**
     * 目标教学班ID；visibility=CLASSES 时必须非空
     */
    private List<Long> teachingClassIds;
}
