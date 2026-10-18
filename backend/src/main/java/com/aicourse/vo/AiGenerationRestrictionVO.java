package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师 AI 出题限制名单行（管理端展示用，含被限制教师的身份信息）。
 */
@Data
public class AiGenerationRestrictionVO {

    /** 被限制的教师用户ID */
    private Long userId;

    /** 登录名（用户已删除时为 null） */
    private String username;

    /** 姓名 */
    private String fullName;

    /** 限制原因 */
    private String reason;

    /** 操作管理员ID（留痕） */
    private Long restrictedBy;

    /** 加入名单时间 */
    private LocalDateTime createdAt;
}
