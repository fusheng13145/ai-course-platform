package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Question;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.QuestionAccessSupport;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.QuestionPracticeService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.QuestionBankStatisticsVO;
import com.aicourse.vo.QuestionStatsVO;
import com.aicourse.vo.QuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题库读面控制器
 * 提供题目列表、详情、搜索、随机取题、按知识点查题与统计查询
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "题库管理", description = "题目CRUD、AI生成、随机选题、批量导入")
public class QuestionController {

    private final QuestionService questionService;
    private final CourseAccessControl courseAccessControl;
    private final QuestionPracticeService questionPracticeService;
    private final QuestionAccessSupport questionAccessSupport;

    public QuestionController(QuestionService questionService,
                              CourseAccessControl courseAccessControl,
                              QuestionPracticeService questionPracticeService,
                              QuestionAccessSupport questionAccessSupport) {
        this.questionService = questionService;
        this.courseAccessControl = courseAccessControl;
        this.questionPracticeService = questionPracticeService;
        this.questionAccessSupport = questionAccessSupport;
    }

    /**
     * 分页查询题目列表（支持多条件筛选）
     */
    // 收口（2026-09-23，P0-3）：题库读接口限教师/管理员。
    // 学生端零依赖这些接口——随堂测走 /questions/random，考试取题走 /exam-papers/{id}/questions，
    // 错题本走 /wrong-questions。故学生调用应得 403，而非拿到（虽已脱敏的）全校题库。
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "分页查询题目列表",
            description = "course_id 为空的历史题仍保留在库：不传 courseId 时一并返回，按课程筛选时不返回")
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .like(keyword != null && !keyword.isBlank(), Question::getContent,
                        keyword == null ? null : keyword.trim())
                .in(readableCourseIds != null, Question::getCourseId, readableCourseIds)
                .orderByDesc(Question::getCreatedAt);
        Page<Question> questionPage = readableCourseIds != null && readableCourseIds.isEmpty()
                ? new Page<>(page, size, 0)
                : questionService.page(Page.of(page, size), queryWrapper);
        if (questionPage == null) {
            questionPage = Page.of(page, size);
        }
        if (privileged) {
            return ApiResponse.success(PagedResponse.of(questionPage));
        }
        // 学生不可见答案与解析
        List<QuestionVO> vos = questionPage.getRecords().stream().map(QuestionVO::from).toList();
        return ApiResponse.success(PagedResponse.of(vos, questionPage.getTotal(), questionPage.getCurrent(), questionPage.getSize()));
    }

    /**
     * 根据ID查询题目详情
     */
    // 收口（2026-09-23，P0-3）：题库详情同 list，仅供教师/管理员
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "根据ID查询题目详情")
    public ApiResponse<?> getById(@PathVariable Long id, Authentication auth) {
        Question question = questionAccessSupport.requireQuestion(id);
        if (question.getCourseId() != null) {
            courseAccessControl.assertCanReadCourse(question.getCourseId(), auth);
        }
        if (RoleUtil.isTeacherOrAdmin(auth)) {
            return ApiResponse.success(question);
        }
        // 学生不可见答案与解析
        return ApiResponse.success(QuestionVO.from(question));
    }

    /**
     * 智能搜索题目（全文检索+筛选）
     */
    // 收口（2026-09-23，P0-3）：题库搜索仅教师/管理员使用
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "智能搜索题目")
    public ApiResponse<?> search(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        Page<Question> result = readableCourseIds != null
                ? questionService.searchQuestions(courseId, type, difficulty, keyword, page, size, readableCourseIds)
                : questionService.searchQuestions(courseId, type, difficulty, keyword, page, size);
        if (result == null) {
            result = new Page<>(page, size, 0);
        }
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        if (privileged) {
            return ApiResponse.success(PagedResponse.of(result));
        }
        List<QuestionVO> vos = result.getRecords().stream().map(QuestionVO::from).toList();
        return ApiResponse.success(PagedResponse.of(vos, result.getTotal(), result.getCurrent(), result.getSize()));
    }

    /**
     * 随机抽取题目（用于组卷）
     */
    @GetMapping("/random")
    @Operation(summary = "随机抽取题目")
    public ApiResponse<?> randomSelect(
            @RequestParam Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "10") int count,
            Authentication auth) {
        // 对象级 ACL（2026-11-14 审计 U2）：随堂测取题虽走 VO 脱敏，但题干本身是课程内容——
        // 未选课学生不得跨课程捞题。P0-A1 起同时要求本课程教师已开放学生刷题。
        questionPracticeService.assertPracticeAllowed(courseId, auth);
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        List<Question> questions = questionService.randomSelect(courseId, type, difficulty, count);
        if (questions == null) {
            questions = java.util.Collections.emptyList();
        }
        if (privileged) {
            return ApiResponse.success(questions);
        }
        return ApiResponse.success(questions.stream().map(QuestionVO::from).toList());
    }

    /**
     * 按知识点查询题目
     */
    // 收口（2026-09-23，P0-3）：按知识点查题属题库读范围，仅供教师/管理员
    @GetMapping("/kp/{kpId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "按知识点查询题目")
    public ApiResponse<?> getByKnowledgePoint(@PathVariable Long kpId,
                                               @RequestParam(required = false) Long courseId,
                                               Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        List<Question> questions = questionService.getByKnowledgePoint(kpId);
        if (questions == null) {
            questions = java.util.Collections.emptyList();
        }
        final Long filterCourseId = courseId;
        final List<Long> readable = readableCourseIds;
        questions = questions.stream()
                .filter(q -> filterCourseId == null || filterCourseId.equals(q.getCourseId()))
                .filter(q -> readable == null || (q.getCourseId() != null && readable.contains(q.getCourseId())))
                .toList();
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        if (privileged) {
            return ApiResponse.success(questions);
        }
        return ApiResponse.success(questions.stream().map(QuestionVO::from).toList());
    }

    /**
     * 题库总览统计（总数 + 今日新增）。
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "题库总览统计",
            description = "totalCount 含 courseId 为空的历史题；todayCount 按 created_at 本机时区当日 00:00 起算")
    public ApiResponse<QuestionBankStatisticsVO> getBankStatistics(Authentication auth) {
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        QuestionBankStatisticsVO statistics = readableCourseIds != null
                ? questionService.getBankStatistics(readableCourseIds)
                : questionService.getBankStatistics();
        return ApiResponse.success(statistics);
    }

    /**
     * 获取题目使用统计
     */
    // 收口（2026-09-23，P0-3）：题目使用统计属教学侧数据
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取题目使用统计")
    public ApiResponse<QuestionStatsVO> getStats(@PathVariable Long id, Authentication auth) {
        Question question = questionAccessSupport.requireQuestion(id);
        if (question.getCourseId() != null) {
            courseAccessControl.assertCanReadCourse(question.getCourseId(), auth);
        }
        QuestionStatsVO stats = questionService.getQuestionStats(id);
        return ApiResponse.success(stats);
    }

    /**
     * 获取易错题TOP N
     */
    // 收口（2026-09-23，P0-3）：易错题榜属教学分析数据，仅教师/管理员
    @GetMapping("/error-prone")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取易错题TOP N")
    public ApiResponse<?> errorProne(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        List<Question> questions = readableCourseIds != null
                ? questionService.getErrorProneQuestions(courseId, limit, readableCourseIds)
                : questionService.getErrorProneQuestions(courseId, limit);
        if (questions == null) {
            questions = java.util.Collections.emptyList();
        }
        if (privileged) {
            return ApiResponse.success(questions);
        }
        return ApiResponse.success(questions.stream().map(QuestionVO::from).toList());
    }
}
