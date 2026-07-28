package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import com.aicourse.common.RoleConstants;

/**
 * 用户信息响应VO（View Object）
 * 用于返回给前端的用户详细信息，包含基础信息、关联班级和学习统计数据
 */
@Schema(description = "用户信息响应")
@Data
public class UserInfoVO {

    @Schema(description = "用户ID", example = "1001")
    private Long id;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "真实姓名", example = "张三")
    private String fullName;

    @Schema(description = "头像URL地址", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "用户角色", example = RoleConstants.STUDENT,
            allowableValues = {RoleConstants.STUDENT, RoleConstants.TEACHER, RoleConstants.ADMIN})
    private String role;

    @Schema(description = "账户状态", example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE", "LOCKED"})
    private String status;

    @Schema(description = "最后登录时间", example = "2024-12-01T10:30:00")
    private LocalDateTime lastLoginAt;

    @Schema(description = "注册时间", example = "2024-09-01T08:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "所属班级名称", example = "计算机科学2401班")
    private String className;

    @Schema(description = "学习统计信息")
    private UserStatsVO stats;

    /**
     * 用户学习统计内部类
     */
    @Schema(description = "学习统计信息")
    public static class UserStatsVO {

        @Schema(description = "已选课程数量", example = "5")
        private Integer courseCount;

        @Schema(description = "已完成课程数量", example = "2")
        private Integer completedCount;

        @Schema(description = "累计学习时长（小时）", example = "128.5")
        private Double totalStudyHours;

    }

}
