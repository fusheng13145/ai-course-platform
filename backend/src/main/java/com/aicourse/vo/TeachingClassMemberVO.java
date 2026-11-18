package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教学班名单行：把选课记录与学生身份合成一条可操作数据。
 *
 * <p>教师端需要看到姓名与学号才能安全取消某位学生的选课；
 * 仅返回 CourseEnrollment 时前端只能显示 #id，不符合管理动作的确认要求。</p>
 */
@Data
public class TeachingClassMemberVO {

    private Long enrollmentId;
    private Long studentId;
    private String studentName;
    private String username;
    private String studentNo;
    private String approvalStatus;
    private Boolean isActive;
    private LocalDateTime enrolledAt;
}
