package com.aicourse.service;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseDefinition;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 课程库定义服务。
 */
public interface CourseDefinitionService extends IService<CourseDefinition> {

    /** 查询课程库；keyword 匹配编号/名称，status 空表示全部。 */
    List<CourseDefinition> search(String keyword, Long collegeId, String status);

    /** 创建课程定义；教师创建时学院取本人学院。 */
    CourseDefinition create(CourseDefinition definition, Long operatorId, boolean isAdmin);

    /** 更新课程定义；仅创建者或管理员可改。 */
    CourseDefinition update(Long id, CourseDefinition patch, Long operatorId, boolean isAdmin);

    /** 读取可用定义；不存在/停用抛业务错误。 */
    CourseDefinition requireActive(Long id);

    /**
     * 兼容旧建课入口：按课程字段生成/复用一条课程定义，返回定义 ID。
     * 该方法只用于没有 definitionId 的历史调用，新入口应显式指定定义。
     */
    Long resolveFromCourse(Course course, Long operatorId);
}
