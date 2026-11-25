package com.aicourse.vo;

import com.aicourse.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户出参（管理台/个人资料用）。
 *
 * <p>字段集合 = `User` 实体当前实际序列化的全部字段，**含 email / phone / studentId**。
 * 本类不承担脱敏职责：脱敏仍由 Controller 在映射前调用 `maskSensitive`（口径见
 * `docs/脱敏核查-2026-11-07.md`），本类只负责切断「HTTP 出参 = 表结构」这条耦合。
 *
 * <p>刻意排除实体的 4 个 `@JsonIgnore` 字段（password / failedLoginAttempts / lockedUntil /
 * deleted / registerIp），与改动前的 JSON 形状严格一致，前端无需调整。
 * 也不加 `@JsonInclude(NON_NULL)`，避免 null 字段从响应中消失。
 */
@Data
public class UserVO {

    private Long id;
    private String email;
    private String username;
    private String fullName;
    private String role;
    private Boolean isActive;
    private Boolean isVerified;
    private String avatarUrl;
    private String phone;
    private String gender;
    private String studentId;
    private String major;
    private String grade;
    private Long schoolId;
    private Long collegeId;
    private Long majorId;
    private Long adminClassId;
    private String adminScope;
    private String department;
    private String title;
    private String bio;
    private String teacherId;
    private String notificationSettings;
    private String banReason;
    private LocalDateTime bannedAt;
    private Long bannedBy;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 关联查询回填项（非表字段）。 */
    private String schoolName;
    private String collegeName;
    private String majorName;
    private String adminClassName;

    /** 实体 → 出参。 */
    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setEmail(user.getEmail());
        vo.setUsername(user.getUsername());
        vo.setFullName(user.getFullName());
        vo.setRole(user.getRole());
        vo.setIsActive(user.getIsActive());
        vo.setIsVerified(user.getIsVerified());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setStudentId(user.getStudentId());
        vo.setMajor(user.getMajor());
        vo.setGrade(user.getGrade());
        vo.setSchoolId(user.getSchoolId());
        vo.setCollegeId(user.getCollegeId());
        vo.setMajorId(user.getMajorId());
        vo.setAdminClassId(user.getAdminClassId());
        vo.setAdminScope(user.getAdminScope());
        vo.setDepartment(user.getDepartment());
        vo.setTitle(user.getTitle());
        vo.setBio(user.getBio());
        vo.setTeacherId(user.getTeacherId());
        vo.setNotificationSettings(user.getNotificationSettings());
        vo.setBanReason(user.getBanReason());
        vo.setBannedAt(user.getBannedAt());
        vo.setBannedBy(user.getBannedBy());
        vo.setLastLogin(user.getLastLogin());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        vo.setSchoolName(user.getSchoolName());
        vo.setCollegeName(user.getCollegeName());
        vo.setMajorName(user.getMajorName());
        vo.setAdminClassName(user.getAdminClassName());
        return vo;
    }
}
