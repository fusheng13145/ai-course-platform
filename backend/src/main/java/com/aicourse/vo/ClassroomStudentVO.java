package com.aicourse.vo;

import com.aicourse.entity.User;

/**
 * 班级名册学生投影（教师视角，2026-11-14 审计 N3/P0-3）。
 *
 * <p>白名单保留名册必需字段，<b>结构性剔除 email/phone</b>：班级名册此前把
 * {@link User} 实体整行下发且无角色限制，任意学生可枚举全校师生联系方式。
 * 管理台确需联系方式的场景（Q6 拍板）由管理员专用路径返回原文实体，教师端一律走本投影。</p>
 */
public record ClassroomStudentVO(
        Long id,
        String username,
        String fullName,
        String studentId,
        String major,
        String grade,
        Long classId) {

    public static ClassroomStudentVO from(User u) {
        return new ClassroomStudentVO(
                u.getId(), u.getUsername(), u.getFullName(),
                u.getStudentId(), u.getMajor(), u.getGrade(), u.getClassId());
    }
}
