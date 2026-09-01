package com.aicourse.vo;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 课程学习详情出参（课程实体 + 当前学生选课记录）。
 * 字段名与历史 Map 键一致（course / enrollment），保证前端零改动。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourseDetailVO {

    private Course course;
    private CourseEnrollment enrollment;

    public CourseDetailVO() {
    }

    public CourseDetailVO(Course course, CourseEnrollment enrollment) {
        this.course = course;
        this.enrollment = enrollment;
    }
}
