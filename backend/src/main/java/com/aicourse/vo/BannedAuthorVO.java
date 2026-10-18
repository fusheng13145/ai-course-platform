package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 被封禁作者行（管理端内容审核用）。
 *
 * <p>只列**经内容审核封禁**的账号（{@code banned_at IS NOT NULL}）——其它原因停用的账号
 * （如生产上被演示种子加固器停用的账号）不属于内容审核的处置面，不应混入本表。</p>
 */
@Data
public class BannedAuthorVO {

    /** 用户ID */
    private Long userId;

    /** 登录名 */
    private String username;

    /** 姓名 */
    private String fullName;

    /** 角色 */
    private String role;

    /** 封禁原因 */
    private String banReason;

    /** 封禁时间 */
    private LocalDateTime bannedAt;

    /** 执行封禁的管理员ID */
    private Long bannedBy;
}
