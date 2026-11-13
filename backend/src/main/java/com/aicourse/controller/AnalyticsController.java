package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.vo.CourseRiskPredictionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据统计分析接口控制器
 * 提供平台总览、学习趋势、成绩分布、课程排行、用户增长、考试统计等数据分析API
 */
@RestController
@RequestMapping("/analytics")
@Tag(name = "数据统计分析", description = "提供平台总览、学习趋势、成绩分布、课程分析等统计功能")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final CourseService courseService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ExamPaperService examPaperService;
    private final AdminScopeGuard adminScopeGuard;

    public AnalyticsController(AnalyticsService analyticsService,
                               CourseService courseService,
                               CourseEnrollmentService courseEnrollmentService,
                               ExamPaperService examPaperService,
                               AdminScopeGuard adminScopeGuard) {
        this.analyticsService = analyticsService;
        this.courseService = courseService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.examPaperService = examPaperService;
        this.adminScopeGuard = adminScopeGuard;
    }

    /**
     * 首页大盘统计数据（基础版）
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    @Operation(summary = "获取基础仪表盘数据", description = "返回用户数、课程数、选课数、任务数等基础统计")
    public ApiResponse<Map<String, Object>> dashboard(
            @Parameter(description = "教师ID（可选，传入时返回教师端数据）") @RequestParam(required = false) Long teacherId,
            Authentication auth) {
        if (!isAdmin(auth)) {
            Long currentUserId = (Long) auth.getPrincipal();
            if (teacherId != null && !currentUserId.equals(teacherId)) {
                throw new BusinessException(ApiCode.FORBIDDEN, "无权查看其他教师的仪表盘");
            }
            teacherId = currentUserId;
        } else if (adminScopeGuard.isCollegeAdmin() && teacherId != null) {
            adminScopeGuard.assertUserAccessById(teacherId);
        }
        log.debug("查询数据大盘 teacherId={}", teacherId);
        Map<String, Object> data;
        if (teacherId != null) {
            data = analyticsService.getTeacherDashboard(teacherId);
        } else if (adminScopeGuard.isCollegeAdmin()) {
            data = analyticsService.getDashboard(adminScopeGuard.currentCollegeId());
        } else {
            data = analyticsService.getDashboard();
        }
        return ApiResponse.success(data);
    }

    /**
     * 平台综合总览（增强版）
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取平台综合总览", description = "返回用户数、活跃度、完成率等多维度平台运营数据")
    public ApiResponse<Map<String, Object>> platformOverview() {
        Map<String, Object> data = adminScopeGuard.isCollegeAdmin()
                ? analyticsService.getPlatformOverview(adminScopeGuard.currentCollegeId())
                : analyticsService.getPlatformOverview();
        return ApiResponse.success(data);
    }

    /**
     * 学生个人学习数据看板
     */
    @GetMapping("/students/{studentId}")
    @Operation(summary = "获取学生个人学习数据", description = "返回学生的选课数、平均进度、累计学习时长等个人统计")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #studentId == authentication.principal")
    public ApiResponse<Map<String, Object>> studentDashboard(
            @Parameter(description = "学生ID") @PathVariable Long studentId,
            Authentication auth) {
        assertCanViewStudentAnalytics(studentId, auth);
        Map<String, Object> data = analyticsService.getUserDashboard(studentId);
        return ApiResponse.success(data);
    }

    /**
     * 学习趋势数据
     */
    @GetMapping("/trends")
    @Operation(summary = "获取学习趋势", description = "返回用户按日期分组的学习时长趋势数据")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Object> learningTrends(
            @Parameter(description = "用户ID（可选）") @RequestParam(required = false) Long userId,
            Authentication auth) {
        Long targetUserId = userId != null ? userId : (Long) auth.getPrincipal();
        assertCanViewStudentAnalytics(targetUserId, auth);
        Object data = analyticsService.getLearningTrends(targetUserId);
        return ApiResponse.success(data);
    }

    /**
     * 学习热力图数据
     */
    @GetMapping("/heatmap")
    @Operation(summary = "获取学习热力图数据", description = "返回指定用户在日期范围内的每日学习时长及强度等级")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> learningHeatmap(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "开始日期（格式：yyyy-MM-dd）") @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期（格式：yyyy-MM-dd）") @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            Authentication auth) {
        assertCanViewStudentAnalytics(userId, auth);
        List<Map<String, Object>> data = analyticsService.getLearningHeatmap(userId, startDate, endDate);
        return ApiResponse.success(data);
    }

    /**
     * 平台级学习热力图（管理员专用，聚合所有用户数据）
     */
    @GetMapping("/heatmap/platform")
    @Operation(summary = "获取平台级热力图", description = "返回平台所有用户的每周各时段学习活跃度分布，仅限管理员")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> platformHeatmap(
            @Parameter(description = "统计天数（默认30天）") @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> data = adminScopeGuard.isCollegeAdmin()
                ? analyticsService.getPlatformHeatmap(days, adminScopeGuard.currentCollegeId())
                : analyticsService.getPlatformHeatmap(days);
        return ApiResponse.success(data);
    }

    /**
     * 课程整体数据分析
     */
    @GetMapping("/courses/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程综合分析", description = "返回课程信息、选课人数、任务数、平均进度和平均成绩")
    public ApiResponse<Map<String, Object>> courseAnalysis(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getCourseAnalysis(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 课程成绩分布（基础版）
     */
    @GetMapping("/courses/{courseId}/grades")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程成绩分布", description = "返回课程的平均分、及格人数等基础成绩统计")
    public ApiResponse<Map<String, Object>> gradeDistribution(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getGradeDistribution(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 课程成绩分布直方图（增强版）
     */
    @GetMapping("/courses/{courseId}/grades/histogram")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取成绩分布直方图", description = "返回按分数段（0-59/60-69/70-79/80-89/90-100）统计的人数分布、最高分、最低分、及格率、优秀率")
    public ApiResponse<Map<String, Object>> gradeDistributionHistogram(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getGradeDistributionHistogram(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 课程学生学情进度列表（教师端：按课程查看每个学生的学习情况）
     */
    @GetMapping("/courses/{courseId}/student-progress")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "课程学生学情进度列表", description = "返回该课程全部选课学生的姓名、学号、学习进度、任务点完成数、作业均分、考试均分、最后活跃时间")
    public ApiResponse<List<Map<String, Object>>> studentCourseProgressList(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        List<Map<String, Object>> data = analyticsService.getStudentCourseProgressList(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 学生在指定课程的个体学情明细（任务点完成情况 + 作业成绩 + 考试成绩）
     */
    @GetMapping("/courses/{courseId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "学生在课程的个体学情明细", description = "返回某学生在指定课程下的章节任务点完成明细、作业成绩列表与考试成绩列表")
    public ApiResponse<Map<String, Object>> studentCourseDetail(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            @Parameter(description = "学生ID") @PathVariable Long studentId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        if (!isAdmin(auth) && !courseEnrollmentService.hasApprovedEnrollment(studentId, courseId)) {
            throw new BusinessException(ApiCode.NOT_FOUND, "该学生不属于此课程");
        }
        Map<String, Object> data = analyticsService.getStudentCourseDetail(courseId, studentId);
        return ApiResponse.success(data);
    }

    /**
     * 热门课程排行
     */
    @GetMapping("/courses/ranking")
    @Operation(summary = "获取热门课程排行", description = "按选课人数降序排列，返回前20门热门课程及其选课数")
    public ApiResponse<List<Map<String, Object>>> courseRanking() {
        List<Map<String, Object>> data = adminScopeGuard.isCollegeAdmin()
                ? analyticsService.getCourseRanking(adminScopeGuard.currentCollegeId())
                : analyticsService.getCourseRanking();
        return ApiResponse.success(data);
    }

    /**
     * 教学监督 / 成绩概览共用聚合（2026-11-07 全量实现批，管理员只读）。
     * <p>每课一行：在学人数、平均进度/总评、预警数、进行中考试、待批阅提交。</p>
     */
    @GetMapping("/teaching-summary")
    @Operation(summary = "教学运行总览", description = "按课程聚合在学人数、平均进度/总评、预警数、进行中考试、待批阅提交；管理员只读")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> teachingSummary() {
        return ApiResponse.success(analyticsService.getTeachingSummary());
    }

    /**
     * 用户增长趋势
     */
    @GetMapping("/users/growth")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取用户增长趋势", description = "按天统计最近N天的新增注册用户数和累计用户总数")
    public ApiResponse<List<Map<String, Object>>> userGrowthTrend(
            @Parameter(description = "统计天数（默认30天）") @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> data = adminScopeGuard.isCollegeAdmin()
                ? analyticsService.getUserGrowthTrend(days, adminScopeGuard.currentCollegeId())
                : analyticsService.getUserGrowthTrend(days);
        return ApiResponse.success(data);
    }

    /**
     * 考试统计详情
     */
    @GetMapping("/exams/{examPaperId}/statistics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取考试统计详情", description = "返回试卷的参考人数、平均分、最高/最低分、及格率、优秀率、分数段分布、平均用时等详细统计")
    public ApiResponse<Map<String, Object>> examStatistics(
            @Parameter(description = "试卷ID") @PathVariable Long examPaperId,
            Authentication auth) {
        if (!isAdmin(auth)) {
            ExamPaper examPaper = examPaperService.getById(examPaperId);
            if (examPaper == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
            }
            assertCourseOwner(examPaper.getCourseId(), auth);
        } else if (adminScopeGuard.isCollegeAdmin()) {
            ExamPaper examPaper = examPaperService.getById(examPaperId);
            if (examPaper == null) {
                throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在");
            }
            assertCollegeCourseAccess(examPaper.getCourseId());
        }
        Map<String, Object> data = analyticsService.getExamStatistics(examPaperId);
        return ApiResponse.success(data);
    }

    /**
     * 全校/多维「学习掉队」预警聚合（教学运行监管核心）
     */
    @GetMapping("/at-risk/students")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取掉队预警聚合", description = "按 school/course/department/major/class 维度聚合掉队学生（进度<40% 或 成绩<60），支持院系/专业/班级/课程过滤下钻")
    public ApiResponse<Map<String, Object>> atRiskStudents(
            @Parameter(description = "聚合维度：school(默认)/course/department/major/class") @RequestParam(required = false) String groupBy,
            @Parameter(description = "课程ID（可选过滤）") @RequestParam(required = false) Long courseId,
            @Parameter(description = "院系（可选过滤）") @RequestParam(required = false) String department,
            @Parameter(description = "专业（可选过滤）") @RequestParam(required = false) String major,
            @Parameter(description = "班级ID（可选过滤）") @RequestParam(required = false) Long classId) {
        if (adminScopeGuard.isCollegeAdmin() && courseId != null) {
            assertCollegeCourseAccess(courseId);
        }
        Map<String, Object> data = adminScopeGuard.isCollegeAdmin()
                ? analyticsService.getAtRiskStudents(groupBy, courseId, department, major, classId,
                        adminScopeGuard.currentCollegeId())
                : analyticsService.getAtRiskStudents(groupBy, courseId, department, major, classId);
        return ApiResponse.success(data);
    }

    /**
     * 课程学生学情风险预测（教师端预警清单，P2 预测性学情预警）
     */
    @GetMapping("/courses/{courseId}/risk-prediction")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "课程学生学情风险预测", description = "基于成绩/进度/登录活跃度/学习行为/时长五维信号，返回每名学生的风险分、等级与可解释原因，按风险分降序")
    public ApiResponse<CourseRiskPredictionVO> courseRiskPrediction(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        CourseRiskPredictionVO data = analyticsService.predictCourseRisk(courseId);
        return ApiResponse.success(data);
    }

    /** 教师只能查看自己课程中的学生学情；管理员按作用域查看（院系仅本院课程，校级跨课程）。 */
    private void assertCourseOwner(Long courseId, Authentication auth) {
        if (isAdmin(auth)) {
            assertCollegeCourseAccess(courseId);
            return;
        }

        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        Long userId = (Long) auth.getPrincipal();
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权查看该课程学情");
        }
    }

    /**
     * 院系管理员课程作用域校验；校级管理员放行。
     * @param courseId 课程ID
     */
    private void assertCollegeCourseAccess(Long courseId) {
        if (!adminScopeGuard.isCollegeAdmin()) {
            return;
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        adminScopeGuard.assertCourseAccess(course);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void assertCanViewStudentAnalytics(Long studentId, Authentication auth) {
        Long currentUserId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) {
            adminScopeGuard.assertUserAccessById(studentId);
            return;
        }
        if (currentUserId.equals(studentId)) return;
        boolean isTeacher = auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()));
        if (isTeacher && courseEnrollmentService.hasApprovedEnrollmentWithTeacher(studentId, currentUserId)) return;
        throw new BusinessException(ApiCode.FORBIDDEN, "无权查看该学生的学习数据");
    }
}
