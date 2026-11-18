package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消已通过选课的原因（教师 / 管理员入口）。
 *
 * <p>取消是软操作：不删除选课行，只把状态推进到 CANCELLED 并留痕。
 * 原因必填，避免管理动作没有可追溯依据。</p>
 */
@Data
public class EnrollmentCancelDTO {

    @NotBlank(message = "取消原因不能为空")
    @Size(min = 2, max = 200, message = "取消原因需为 2–200 个字符")
    private String reason;
}
