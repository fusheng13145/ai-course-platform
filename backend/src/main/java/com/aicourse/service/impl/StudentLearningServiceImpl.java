package com.aicourse.service.impl;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SignInService;
import com.aicourse.service.StudentLearningService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.VideoLearningRecordService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.component.student.StudentDashboardComponent;
import com.aicourse.service.component.student.StudentLearningCenterComponent;
import com.aicourse.service.component.student.StudentTaskComponent;
import com.aicourse.service.component.student.StudentTaskCompletionComponent;
import com.aicourse.service.component.student.StudentVisualizationComponent;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 学生端学习核心服务门面：驾驶舱/学习中心/任务点/成绩/学情可视化职责委托给协作者组件。
 * <p>拆分自原 944 行单类（2026-08-29），组件位于 service.component 包，各组件只持有自己需要的依赖。</p>
 */
@Service
public class StudentLearningServiceImpl implements StudentLearningService {

    private final StudentDashboardComponent dashboardComponent;
    private final StudentLearningCenterComponent learningCenterComponent;
    private final StudentTaskComponent taskComponent;
    private final StudentVisualizationComponent visualizationComponent;
    private final StudentTaskCompletionComponent taskSupport;

    @Autowired
    public StudentLearningServiceImpl(StudentDashboardComponent dashboardComponent,
                                      StudentLearningCenterComponent learningCenterComponent,
                                      StudentTaskComponent taskComponent,
                                      StudentVisualizationComponent visualizationComponent,
                                      StudentTaskCompletionComponent taskSupport) {
        this.dashboardComponent = dashboardComponent;
        this.learningCenterComponent = learningCenterComponent;
        this.taskComponent = taskComponent;
        this.visualizationComponent = visualizationComponent;
        this.taskSupport = taskSupport;
    }

    /**
     * 兼容构造器（临时）：组装真实组件 + 调用方注入的底层依赖。
     * <p>供旧单测沿用（原 15 参数签名），组件单测重构完成后移除。</p>
     */
    @Deprecated
    public StudentLearningServiceImpl(CourseEnrollmentService enrollmentService,
                                      CourseService courseService,
                                      CourseChapterService courseChapterService,
                                      TaskService taskService,
                                      SubmissionService submissionService,
                                      TaskCompletionMapper taskCompletionMapper,
                                      NotificationService notificationService,
                                      SignInService signInService,
                                      ExamPaperService examPaperService,
                                      ExamSessionService examSessionService,
                                      AnalyticsService analyticsService,
                                      LearningRecordService learningRecordService,
                                      QuestionService questionService,
                                      WrongQuestionService wrongQuestionService,
                                      VideoLearningRecordService videoLearningRecordService) {
        this(
                new StudentDashboardComponent(enrollmentService, courseService, taskService, submissionService,
                        examPaperService, examSessionService, signInService, notificationService,
                        learningRecordService, analyticsService),
                new StudentLearningCenterComponent(courseService, courseChapterService, taskService,
                        submissionService, taskCompletionMapper,
                        new StudentTaskCompletionComponent(taskService, taskCompletionMapper, enrollmentService)),
                new StudentTaskComponent(taskService, videoLearningRecordService, questionService,
                        wrongQuestionService,
                        new StudentTaskCompletionComponent(taskService, taskCompletionMapper, enrollmentService)),
                new StudentVisualizationComponent(learningRecordService, wrongQuestionService, courseService),
                new StudentTaskCompletionComponent(taskService, taskCompletionMapper, enrollmentService)
        );
    }

    /** 自愈合建表：委托 task 支持组件（幂等） */
    @PostConstruct
    public void ensureTable() {
        taskSupport.ensureTable();
    }

    @Override
    public Map<String, Object> getStudentDashboard(Long userId) {
        return dashboardComponent.getStudentDashboard(userId);
    }

    @Override
    public Map<String, Object> getCourseLearningCenter(Long userId, Long courseId) {
        return learningCenterComponent.getCourseLearningCenter(userId, courseId);
    }

    @Override
    public Map<String, Object> getCourseProgressSummary(Long userId, Long courseId) {
        return learningCenterComponent.getCourseProgressSummary(userId, courseId);
    }

    @Override
    public Map<String, Object> markTaskComplete(Long userId, Long courseId, Long taskId, String completionType) {
        return taskComponent.markTaskComplete(userId, courseId, taskId, completionType);
    }

    @Override
    public Map<String, Object> getVideoState(Long userId, Long courseId, Long taskId) {
        return taskComponent.getVideoState(userId, courseId, taskId);
    }

    @Override
    public Map<String, Object> reportVideoHeartbeat(Long userId, Long courseId, Long taskId, Long resourceId,
                                                    Integer position, Integer watchedDuration,
                                                    Integer totalDuration, Double playbackSpeed) {
        return taskComponent.reportVideoHeartbeat(userId, courseId, taskId, resourceId,
                position, watchedDuration, totalDuration, playbackSpeed);
    }

    @Override
    public Map<String, Object> submitQuiz(Long userId, Long courseId, Long taskId, Map<String, String> answers) {
        return taskComponent.submitQuiz(userId, courseId, taskId, answers);
    }

    @Override
    public Map<String, Object> getGrades(Long userId) {
        return dashboardComponent.getGrades(userId);
    }

    @Override
    public Map<String, Object> getVisualizationData(Long userId) {
        return visualizationComponent.getVisualizationData(userId);
    }
}
