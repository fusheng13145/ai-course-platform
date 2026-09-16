package com.aicourse.service;

import com.aicourse.entity.CourseEnrollment;
import com.baomidou.mybatisplus.extension.service.IService;


/**
 * 课程选课接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface CourseEnrollmentService extends IService<CourseEnrollment> {

    /**
     * 报名课程（/enrollments 接口业务下沉）：
     * 有效报名查重 → 软退课记录恢复 → 按课程报名类型判定审核状态后新建。
     *
     * @param studentId 学生ID
     * @param courseId  课程ID
     * @return 报名记录
     */
    CourseEnrollment enroll(Long studentId, Long courseId);

    /**
     * 学生加入课程（/student/courses/{courseId}/enroll 接口业务下沉）：
     * 校验课程存在与未重复加入后新建有效报名记录。
     *
     * @param studentId 学生ID
     * @param courseId  课程ID
     */
    void enrollStudent(Long studentId, Long courseId);

    /**
     * 软退课（/enrollments/{id} 接口业务下沉）：校验报名记录归属后置 isActive=false。
     *
     * @param id     报名记录ID
     * @param userId 操作人ID
     */
    void unenroll(Long id, Long userId);

    /**
     * 判断学生是否为课程**有效成员**（选课有效 is_active=1 且审批通过 approval_status=APPROVED）。
     *
     * <p>用于所有「以课程成员身份获得的权利」的统一判定：资源下载、参加考试等。
     * 语义收口原因（2026-09-14）：此前各处自行按 {@code is_active=1} 判权，
     * 会让**待审批(PENDING)**、甚至历史上**已驳回**的报名记录获得成员权利；
     * 审批型课程（enrollment_type=APPROVAL）必须在教师审批通过后才算成员。</p>
     */
    boolean hasApprovedEnrollment(Long studentId, Long courseId);
}
