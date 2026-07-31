package com.aicourse.service;

import com.aicourse.entity.Course;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CourseService extends IService<Course> {

    /**
     * 创建课程：绑定创建者为授课教师后保存。
     */
    Course createCourse(Course course, Long teacherId);

    /**
     * 更新课程（含权限校验）：课程不存在抛「课程不存在」，非创建者且非管理员抛「无权修改该课程」。
     */
    Course updateCourse(Long id, Course course, Long operatorId, boolean isAdmin);

    /**
     * 仅执行写操作的课程更新（权限已由调用方校验），返回更新后课程。
     */
    Course updateCourseById(Long id, Course course);

    /**
     * 删除课程（软删除）。
     */
    void deleteCourseById(Long id);

    /**
     * 审批通过课程：置 APPROVED/ACTIVE。
     */
    Course approveCourse(Long id);

    /**
     * 切换课程启用/禁用状态。
     */
    Course toggleCourseStatus(Long id);

    /**
     * 审批驳回课程：置 REJECTED 并记录原因。
     */
    Course rejectCourse(Long id, String reason);
}
