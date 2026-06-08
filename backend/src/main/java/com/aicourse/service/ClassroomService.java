package com.aicourse.service;

import com.aicourse.entity.Classroom;
import com.aicourse.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 班级管理服务接口
 */
public interface ClassroomService extends IService<Classroom> {

    /**
     * 获取班级详情（含基本信息）
     * @param classroomId 班级ID
     * @return 班级详情信息Map
     */
    Map<String, Object> getClassroomDetail(Long classroomId);

    /**
     * 获取班级学生列表（分页，通过User.classId关联）
     * @param classroomId 班级ID
     * @param keyword     搜索关键字（学生姓名/学号）
     * @param page        页码（从1开始）
     * @param size        每页大小
     * @return 分页学生列表
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> getClassroomStudents(Long classroomId, String keyword, int page, int size);

    /**
     * 添加学生到班级
     * @param classroomId 班级ID
     * @param studentId   学生ID
     */
    void addStudent(Long classroomId, Long studentId);

    /**
     * 从班级移除学生
     * @param classroomId 班级ID
     * @param studentId   学生ID
     */
    void removeStudent(Long classroomId, Long studentId);

    /**
     * 获取教师管理的班级列表
     * @param advisorId 辅导员/教师ID
     * @return 班级列表
     */
    List<Classroom> getAdvisorClassrooms(Long advisorId);

    /**
     * 班级统计信息（人数、男女比例等）
     * @param classroomId 班级ID
     * @return 统计信息Map
     */
    Map<String, Object> getClassroomStats(Long classroomId);

    /**
     * 搜索班级（支持多条件组合查询）
     * @param keyword    搜索关键字（名称/编号/描述）
     * @param gradeLevel 年级筛选
     * @param major      专业筛选
     * @return 匹配的班级列表
     */
    List<Classroom> searchClassrooms(String keyword, String gradeLevel, String major);
}
