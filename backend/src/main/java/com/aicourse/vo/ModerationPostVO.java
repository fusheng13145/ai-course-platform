package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核/已审核的讨论回复行（管理端内容审核用）。
 */
@Data
public class ModerationPostVO {

    /** 回复ID */
    private Long id;

    /** 所属话题ID */
    private Long topicId;

    /** 作者ID */
    private Long authorId;

    /** 作者姓名（用户已删除时为 null） */
    private String authorName;

    /** 回复内容 */
    private String content;

    /** NORMAL-正常；HIDDEN-已隐藏 */
    private String moderationStatus;

    /** 审核原因 */
    private String moderationReason;

    /** 最近一次审核时间 */
    private LocalDateTime moderatedAt;

    /** 发表时间 */
    private LocalDateTime createdAt;
}
