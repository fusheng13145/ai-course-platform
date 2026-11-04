package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.AIService;
import com.aicourse.service.ClassroomService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.TeacherCourseService;
import com.aicourse.service.UserService;
import com.aicourse.service.component.teacher.TeacherChapterComponent;
import com.aicourse.service.component.teacher.TeacherCourseComponent;
import com.aicourse.service.component.teacher.TeacherDashboardComponent;
import com.aicourse.service.component.teacher.TeacherOutlineComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 课程教学核心服务门面：驾驶舱/仪表盘/预警、AI 建课/克隆、章节树/班级职责委托给协作者组件。
 * <p>拆分自原 813 行单类（2026-08-29），组件位于 service.component 包。</p>
 */
@Service
public class TeacherCourseServiceImpl implements TeacherCourseService {

    private final TeacherDashboardComponent dashboardComponent;
    private final TeacherOutlineComponent outlineComponent;
    private final TeacherChapterComponent chapterComponent;

    @Autowired
    public TeacherCourseServiceImpl(TeacherDashboardComponent dashboardComponent,
                                    TeacherOutlineComponent outlineComponent,
                                    TeacherChapterComponent chapterComponent) {
        this.dashboardComponent = dashboardComponent;
        this.outlineComponent = outlineComponent;
        this.chapterComponent = chapterComponent;
    }

    @Override
    public Map<String, Object> getCourseTeachingDashboard(Long courseId, Long operatorId) {
        return dashboardComponent.getCourseTeachingDashboard(courseId, operatorId);
    }

    @Override
    public List<Map<String, Object>> computeAtRisk(List<CourseEnrollment> enrollments) {
        return dashboardComponent.computeAtRisk(enrollments);
    }

    @Override
    public Map<String, Object> generateCourseOutlineByAI(String topic, String category, String level,
                                                         int chapterCount, Long operatorId) {
        return outlineComponent.generateCourseOutlineByAI(topic, category, level, chapterCount, operatorId);
    }

    @Override
    public Map<String, Object> cloneCourse(Long sourceCourseId, String newName, Long operatorId) {
        return outlineComponent.cloneCourse(sourceCourseId, newName, operatorId);
    }

    @Override
    public List<Map<String, Object>> getMyCourseSummaries(Long teacherId) {
        return dashboardComponent.getMyCourseSummaries(teacherId);
    }

    @Override
    public Map<String, Object> getTeacherDashboard(Long teacherId) {
        return dashboardComponent.getTeacherDashboard(teacherId);
    }

    @Override
    public List<Map<String, Object>> getFullChapterTree(Long courseId) {
        return chapterComponent.getFullChapterTree(courseId);
    }

    @Override
    public List<Map<String, Object>> getCourseClasses(Long courseId) {
        return chapterComponent.getCourseClasses(courseId);
    }
}
