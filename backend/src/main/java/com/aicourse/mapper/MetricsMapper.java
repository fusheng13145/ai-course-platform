package com.aicourse.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 运维监控聚合查询。
 * <p>集中承载 Grafana 业务面板所需的轻量计数查询，避免向各业务 Mapper 散落统计方法。
 * 所有方法均为只读聚合（COUNT），由 {@code BusinessMetricsService} 周期性调用刷新 Micrometer Gauge。</p>
 */
@Mapper
public interface MetricsMapper {

    /** 进行中且已通过审批的课程数 */
    @Select("SELECT COUNT(*) FROM courses WHERE status = 'ACTIVE' AND approval_status = 'APPROVED'")
    long countActiveCourses();

    /** 已通过审批的选课记录数 */
    @Select("SELECT COUNT(*) FROM course_enrollments WHERE approval_status = 'APPROVED'")
    long countApprovedEnrollments();

    /** 已激活（is_active=1）的学生数 */
    @Select("SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = 1")
    long countActiveStudents();

    /** 教师数 */
    @Select("SELECT COUNT(*) FROM users WHERE role = 'TEACHER'")
    long countTeachers();

    /** 作业提交总数（含草稿转正后的正式提交） */
    @Select("SELECT COUNT(*) FROM submissions")
    long countSubmissions();

    /** 待批改作业数（status = SUBMITTED） */
    @Select("SELECT COUNT(*) FROM submissions WHERE status = 'SUBMITTED'")
    long countPendingSubmissions();

    /** 试卷总数 */
    @Select("SELECT COUNT(*) FROM exam_papers")
    long countExams();

    /** 题库题目总数 */
    @Select("SELECT COUNT(*) FROM questions")
    long countQuestions();

    /** 错题本条目总数 */
    @Select("SELECT COUNT(*) FROM wrong_questions")
    long countWrongQuestions();

    /** 学习记录总数 */
    @Select("SELECT COUNT(*) FROM learning_records")
    long countLearningRecords();

    /** 讨论区帖子总数 */
    @Select("SELECT COUNT(*) FROM discussion_posts")
    long countDiscussionPosts();

    // ===================== 课程维度下钻（tag=课程编号+课程名，val=统计值）=====================

    /** 每门课程已审批选课人数 */
    @Select("SELECT c.code AS `course`, c.name AS `course_name`, " +
            "c.teacher_id AS `teacher`, u.full_name AS `teacher_name`, COUNT(ce.student_id) AS val " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id " +
            "LEFT JOIN course_enrollments ce ON ce.course_id = c.id AND ce.approval_status = 'APPROVED' " +
            "GROUP BY c.id, c.code, c.name, c.teacher_id, u.full_name")
    java.util.List<java.util.Map<String, Object>> courseEnrollments();

    /** 每门课程作业提交总数（经 tasks.course_id 关联 submissions） */
    @Select("SELECT c.code AS `course`, c.name AS `course_name`, " +
            "c.teacher_id AS `teacher`, u.full_name AS `teacher_name`, COUNT(s.id) AS val " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id " +
            "LEFT JOIN tasks t ON t.course_id = c.id " +
            "LEFT JOIN submissions s ON s.task_id = t.id " +
            "GROUP BY c.id, c.code, c.name, c.teacher_id, u.full_name")
    java.util.List<java.util.Map<String, Object>> courseSubmissions();

    /** 每门课程待批改作业数（status=SUBMITTED） */
    @Select("SELECT c.code AS `course`, c.name AS `course_name`, " +
            "c.teacher_id AS `teacher`, u.full_name AS `teacher_name`, " +
            "COUNT(CASE WHEN s.status = 'SUBMITTED' THEN s.id END) AS val " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id " +
            "LEFT JOIN tasks t ON t.course_id = c.id " +
            "LEFT JOIN submissions s ON s.task_id = t.id " +
            "GROUP BY c.id, c.code, c.name, c.teacher_id, u.full_name")
    java.util.List<java.util.Map<String, Object>> coursePendingSubmissions();

    /** 每门课程题库题目数 */
    @Select("SELECT c.code AS `course`, c.name AS `course_name`, " +
            "c.teacher_id AS `teacher`, u.full_name AS `teacher_name`, COUNT(q.id) AS val " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id " +
            "LEFT JOIN questions q ON q.course_id = c.id " +
            "GROUP BY c.id, c.code, c.name, c.teacher_id, u.full_name")
    java.util.List<java.util.Map<String, Object>> courseQuestions();

    /** 每门课程试卷数 */
    @Select("SELECT c.code AS `course`, c.name AS `course_name`, " +
            "c.teacher_id AS `teacher`, u.full_name AS `teacher_name`, COUNT(e.id) AS val " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id " +
            "LEFT JOIN exam_papers e ON e.course_id = c.id " +
            "GROUP BY c.id, c.code, c.name, c.teacher_id, u.full_name")
    java.util.List<java.util.Map<String, Object>> courseExams();

    // ===================== 班级维度下钻（tag=班级编号+班级名，val=统计值）=====================

    /** 每个班级的学生总数 */
    @Select("SELECT cl.code AS `class`, cl.name AS `class_name`, COUNT(u.id) AS val " +
            "FROM classrooms cl " +
            "LEFT JOIN users u ON u.class_id = cl.id AND u.role = 'STUDENT' " +
            "GROUP BY cl.id, cl.code, cl.name")
    java.util.List<java.util.Map<String, Object>> classroomStudents();

    /** 每个班级已审批选课人次 */
    @Select("SELECT cl.code AS `class`, cl.name AS `class_name`, COUNT(ce.student_id) AS val " +
            "FROM classrooms cl " +
            "LEFT JOIN users u ON u.class_id = cl.id " +
            "LEFT JOIN course_enrollments ce ON ce.student_id = u.id AND ce.approval_status = 'APPROVED' " +
            "GROUP BY cl.id, cl.code, cl.name")
    java.util.List<java.util.Map<String, Object>> classroomEnrollments();

    /** 每个班级作业提交总数 */
    @Select("SELECT cl.code AS `class`, cl.name AS `class_name`, COUNT(s.id) AS val " +
            "FROM classrooms cl " +
            "LEFT JOIN users u ON u.class_id = cl.id " +
            "LEFT JOIN submissions s ON s.student_id = u.id " +
            "GROUP BY cl.id, cl.code, cl.name")
    java.util.List<java.util.Map<String, Object>> classroomSubmissions();

    /** 每个班级待批改作业数（status=SUBMITTED） */
    @Select("SELECT cl.code AS `class`, cl.name AS `class_name`, " +
            "COUNT(CASE WHEN s.status = 'SUBMITTED' THEN s.id END) AS val " +
            "FROM classrooms cl " +
            "LEFT JOIN users u ON u.class_id = cl.id " +
            "LEFT JOIN submissions s ON s.student_id = u.id " +
            "GROUP BY cl.id, cl.code, cl.name")
    java.util.List<java.util.Map<String, Object>> classroomPendingSubmissions();
}
