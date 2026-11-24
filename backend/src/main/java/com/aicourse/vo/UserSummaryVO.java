package com.aicourse.vo;

import com.aicourse.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 同学 / 学生名册出参（脱敏模型）。
 *
 * <p>由 {@code GET /users/{id}/classmates} 与 {@code GET /users/teacher/students} 使用。
 * 这两个接口的可见范围是「同班同学」与「任课教师」，不是管理员台，
 * 因此**不输出** {@code email}、{@code phone}、{@code studentId}、{@code gender}：
 * 它们属于可识别到个人的联系方式与学籍信息，一旦随列表下发即形成批量导出面。
 *
 * <p>字段集合与历史实体直返相比是“只减不增”，前端展示（姓名/头像/角色/专业/年级/状态）零改动；
 * 管理员需要的完整联系方式仍走 {@code GET /users}（管理台运营口径，见 docs/脱敏核查）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSummaryVO {

    private Long id;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String major;
    private String grade;
    private Boolean isActive;
    private Boolean isVerified;

    public UserSummaryVO() {
    }

    /**
     * 实体 → 脱敏出参。仅搬运白名单字段，禁止整实体透传。
     *
     * @param user 用户实体，可为 null
     * @return 脱敏出参；入参为 null 时返回 null
     */
    public static UserSummaryVO from(User user) {
        if (user == null) {
            return null;
        }
        UserSummaryVO vo = new UserSummaryVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setFullName(user.getFullName());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        vo.setMajor(user.getMajor());
        vo.setGrade(user.getGrade());
        vo.setIsActive(user.getIsActive());
        vo.setIsVerified(user.getIsVerified());
        return vo;
    }
}
