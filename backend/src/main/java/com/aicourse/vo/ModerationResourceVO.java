package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核/已审核的教学资源行（管理端内容审核用；"平台全部资源"口径）。
 */
@Data
public class ModerationResourceVO {

    /** 资源ID */
    private Long id;

    /** 文件名 */
    private String fileName;

    /** 文件类型 */
    private String fileType;

    /** 所属课程ID */
    private Long courseId;

    /** 上传者ID */
    private Long uploaderId;

    /** 上传者姓名（用户已删除时为 null） */
    private String uploaderName;

    /** NORMAL-正常；HIDDEN-已隐藏 */
    private String moderationStatus;

    /** 审核原因 */
    private String moderationReason;

    /** 最近一次审核时间 */
    private LocalDateTime moderatedAt;

    /** 上传时间 */
    private LocalDateTime uploadTime;
}
