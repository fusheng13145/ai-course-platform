package com.aicourse.service;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 教学班业务接口（V14 / B-12-P-08 WP4）。
 */
public interface TeachingClassService extends IService<TeachingClass> {

    /**
     * 查询某课程下的教学班。
     */
    List<TeachingClass> listByCourse(Long courseId);

    /**
     * 课程负责人创建教学班；课程未通过审批或已停用时强制 DRAFT。
     */
    TeachingClass create(Long operatorId, Long courseId, String name, String code,
                         Long leadTeacherId, Integer capacity);

    /**
     * 课程负责人变更主讲（仅限本院教师）。
     */
    TeachingClass assignLeadTeacher(Long teachingClassId, Long teacherId, Long operatorId);

    /**
     * 课程负责人修改教学班基础信息。
     */
    TeachingClass updateBasic(Long teachingClassId, Long operatorId, String name, String code,
                              Integer capacity, String status);

    /**
     * 课程负责人删除教学班；有成员时拒绝。
     */
    void delete(Long teachingClassId, Long operatorId);

    /**
     * 教学班主讲查看名单与成绩；其他教学班主讲 403，管理员只读放行。
     */
    List<CourseEnrollment> listMembers(Long teachingClassId, Long operatorId);
}
