package com.aicourse.service;

import com.aicourse.entity.CourseEnrollment;
import com.baomidou.mybatisplus.extension.service.IService;

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
}
