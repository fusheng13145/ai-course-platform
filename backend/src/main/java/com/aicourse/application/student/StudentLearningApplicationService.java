package com.aicourse.application.student;

import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.TeachingClassService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.CourseDetailVO;
import com.aicourse.vo.CourseVOs;
import com.aicourse.vo.SubmissionVO;
import com.aicourse.vo.TaskVO;
import com.aicourse.vo.TeachingClassOptionVO;
import com.aicourse.vo.WrongQuestionVOs;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生端「学习中心 + 作业查询」应用服务（3E-05B）。
 *
 * <p>改前 {@code StudentController} 把「有效选课」这条口径（{@code is_active AND approval_status=APPROVED}）
 * 在两处各写一遍，把「最新一次提交判定待办」的规则直接写在控制器里，另有 2 处分页转 VO
 * 私有静态方法。本层把它们收成：{@link #validEnrollments(Long)} / {@link #validCourseIds(Long)}
 * 与 {@link #latestByTask(Long)} 三个唯一入口。</p>
 *
 * <p>边界：写操作（加入/退出课程）仍走 {@code CourseEnrollmentService}，控制器一行转发；
 * 作业提交走 {@code SubmissionApplicationService}（3E-02）；错题本与学习进度服务本身已封装，
 * 不在本批范围内。</p>
 */
@Service
public class StudentLearningApplicationService {

    /** 有效选课口径：启用 + 审批通过（V14 / WP5）。 */
    private static final String APPROVED = "APPROVED";
    /** 任务已发布。 */
    private static final String TASK_PUBLISHED = "PUBLISHED";
    /** 提交已完成（待办剔除）：已提交 / 已评分；被退回（RETURNED）仍算待办。 */
    private static final Set<String> SUBMISSION_FINISHED_STATUSES = Set.of("SUBMITTED", "GRADED");

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final TeachingClassService teachingClassService;
    private final WrongQuestionService wrongQuestionService;
    private final LearningRecordService learningRecordService;

    public StudentLearningApplicationService(CourseService courseService,
                                             CourseEnrollmentService enrollmentService,
                                             TaskService taskService,
                                             SubmissionService submissionService,
                                             TeachingClassService teachingClassService,
                                             WrongQuestionService wrongQuestionService,
                                             LearningRecordService learningRecordService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.teachingClassService = teachingClassService;
        this.wrongQuestionService = wrongQuestionService;
        this.learningRecordService = learningRecordService;
    }

    // ==================== 学习中心 ====================

    /** 我的课程（仅审批通过且在有效期内的选课）。 */
    public PagedResponse<CourseVOs.CourseVO> myCourses(Long userId, long page, long size) {
        List<Long> courseIds = validCourseIds(userId);
        if (courseIds.isEmpty()) {
            return PagedResponse.empty();
        }
        Page<Course> coursePage = courseService.page(Page.of(page, size),
                new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
        return PagedResponse.of(coursePage.getRecords().stream().map(CourseVOs.CourseVO::from).toList(),
                coursePage.getTotal(), coursePage.getCurrent(), coursePage.getSize());
    }

    /** 课程学习详情（课程实体 + 本人选课记录；课程不存在 → 404）。 */
    public CourseDetailVO courseDetail(Long userId, Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(com.aicourse.common.ApiCode.NOT_FOUND, "课程不存在");
        }
        CourseEnrollment enrollment = enrollmentService.getOne(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getCourseId, courseId));
        return new CourseDetailVO(course, enrollment);
    }

    /** 加入课程前可选的教学班（仅 ACTIVE；课程未过审或已结束不可选班）。 */
    public List<TeachingClassOptionVO> availableTeachingClasses(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(com.aicourse.common.ApiCode.NOT_FOUND, "课程不存在");
        }
        if (course.getApprovalStatus() != null && !APPROVED.equalsIgnoreCase(course.getApprovalStatus())) {
            throw new BusinessException("课程尚未通过审核，暂不可选班");
        }
        if (course.getStatus() != null && !"ACTIVE".equalsIgnoreCase(course.getStatus())) {
            throw new BusinessException("课程已结束，暂不可选班");
        }
        return teachingClassService.listByCourse(courseId).stream()
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .map(TeachingClassOptionVO::from)
                .toList();
    }

    // ==================== 作业查询 ====================

    /**
     * 待完成作业。
     *
     * <p>口径（2026-11-14 P1-4）：按「最新一次提交的状态」判定 —— 无提交，或最新一次提交为
     * {@code RETURNED}（允许重交）→ 仍属待办；{@code SUBMITTED/GRADED} → 已完成。
     * 指定 {@code courseId} 时按课程精确过滤，否则限定在已加入课程范围内。</p>
     */
    public List<TaskVO> pendingAssignments(Long userId, Long courseId) {
        List<Long> enrolledCourseIds = validCourseIds(userId);
        if (enrolledCourseIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Task> tasks = taskService.list(new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId)
                .in(courseId == null, Task::getCourseId, enrolledCourseIds)
                .eq(Task::getStatus, TASK_PUBLISHED)
                .orderByDesc(Task::getCreatedAt));
        if (tasks.isEmpty()) {
            return TaskVO.from(tasks);
        }
        Set<Long> finishedTaskIds = latestByTask(userId).values().stream()
                .filter(s -> s.getStatus() != null && SUBMISSION_FINISHED_STATUSES.contains(s.getStatus()))
                .map(Submission::getTaskId)
                .collect(Collectors.toSet());
        return TaskVO.from(tasks.stream().filter(t -> !finishedTaskIds.contains(t.getId())).toList());
    }

    /** 已完成作业（指定课程时按「作业→课程」归属过滤）。 */
    public List<SubmissionVO> completedAssignments(Long userId, Long courseId) {
        List<Submission> submissions = submissionService.list(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getStudentId, userId)
                .orderByDesc(Submission::getCreatedAt));
        if (courseId != null && !submissions.isEmpty()) {
            Set<Long> taskIds = submissions.stream().map(Submission::getTaskId).collect(Collectors.toSet());
            Map<Long, Long> taskCourseMap = taskService.listByIds(taskIds).stream()
                    .collect(Collectors.toMap(Task::getId, Task::getCourseId, (a, b) -> a));
            submissions = submissions.stream()
                    .filter(s -> courseId.equals(taskCourseMap.get(s.getTaskId())))
                    .toList();
        }
        return SubmissionVO.from(submissions);
    }

    // ==================== 学习记录 ====================

    /** 我的学习记录（3E-05E：从 LearningRecordController 下移，原口径仅按 userId、无排序）。 */
    public List<com.aicourse.vo.LearningProgressVOs.RecordVO> myRecords(Long userId) {
        return com.aicourse.vo.LearningProgressVOs.RecordVO.from(
                learningRecordService.list(new LambdaQueryWrapper<com.aicourse.entity.LearningRecord>()
                        .eq(com.aicourse.entity.LearningRecord::getUserId, userId)));
    }

    // ==================== 错题本分页出参 ====================


    /** 错题本分页（出参转 VO，键名与历史 JSON 一致）。 */
    public PagedResponse<WrongQuestionVOs.WrongQuestionVO> wrongQuestionPage(
            Long userId, Long courseId, int page, int size) {
        Page<WrongQuestion> result = wrongQuestionService.getWrongQuestionBook(userId, courseId, page, size);
        return PagedResponse.of(result.getRecords().stream().map(WrongQuestionVOs.WrongQuestionVO::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    // ==================== 口径助手 ====================

    /** 有效选课记录（启用 + 审批通过）。 */
    private List<CourseEnrollment> validEnrollments(Long userId) {
        return enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getIsActive, true)
                .eq(CourseEnrollment::getApprovalStatus, APPROVED));
    }

    /** 有效选课对应的课程ID集合。 */
    private List<Long> validCourseIds(Long userId) {
        return validEnrollments(userId).stream().map(CourseEnrollment::getCourseId).collect(Collectors.toList());
    }

    /** 每门作业的最新一次提交（按 attemptNumber 大者为准，null 视为 0）。 */
    private Map<Long, Submission> latestByTask(Long userId) {
        Map<Long, Submission> latest = new HashMap<>();
        for (Submission s : submissionService.list(
                new LambdaQueryWrapper<Submission>().eq(Submission::getStudentId, userId))) {
            Submission cur = latest.get(s.getTaskId());
            int now = s.getAttemptNumber() != null ? s.getAttemptNumber() : 0;
            int prev = cur != null && cur.getAttemptNumber() != null ? cur.getAttemptNumber() : -1;
            if (cur == null || now >= prev) {
                latest.put(s.getTaskId(), s);
            }
        }
        return latest;
    }
}
