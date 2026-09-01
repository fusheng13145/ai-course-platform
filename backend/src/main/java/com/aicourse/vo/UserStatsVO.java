package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户统计出参。
 * 字段名与历史 Map 键一致（userId/username/fullName/role/email/isActive/lastLogin/createdAt/classmateCount）。
 * classmateCount 仅当用户归属班级（classId≠null）时存在；配合 {@code @JsonInclude(NON_NULL)}
 * 精确复刻原 Map「classId 为空则键缺失」行为，保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserStatsVO {

    private Long userId;
    private String username;
    private String fullName;
    private String role;
    private String email;
    private Boolean isActive;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private Long classmateCount;

    public UserStatsVO() {
    }
}
