package com.aicourse.controller;

import com.aicourse.application.question.QuestionBankApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Question;
import com.aicourse.vo.QuestionBankStatisticsVO;
import com.aicourse.vo.QuestionStatsVO;
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
 * 提供题目列表、详情、搜索、随机取题、按知识点查题与统计查询。
 *
 * <p>3E-03C：查询条件拼装、课程可见范围过滤、统计口径分支与 {@code /random} 的脱敏两档
 * 已全部下移到 {@link QuestionBankApplicationService}；本类只保留路由、权限注解与响应封装。</p>
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "题库管理", description = "题目CRUD、AI生成、随机选题、批量导入")
public class QuestionController {

    private final QuestionBankApplicationService questionBankApplicationService;

    public QuestionController(QuestionBankApplicationService questionBankApplicationService) {
        this.questionBankApplicationService = questionBankApplicationService;
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
    public ApiResponse<PagedResponse<Question>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.pageQuestions(
                page, size, courseId, type, difficulty, keyword, auth));
    }

    /**
     * 根据ID查询题目详情
     */
    // 收口（2026-09-23，P0-3）：题库详情同 list，仅供教师/管理员
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "根据ID查询题目详情")
    public ApiResponse<Question> getById(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.questionDetail(id, auth));
    }

    /**
     * 智能搜索题目（全文检索+筛选）
     */
    // 收口（2026-09-23，P0-3）：题库搜索仅教师/管理员使用
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "智能搜索题目")
    public ApiResponse<PagedResponse<Question>> search(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.searchQuestions(
                courseId, type, difficulty, keyword, page, size, auth));
    }

    /**
     * 随机抽取题目（用于组卷）
     */
    @GetMapping("/random")
    @Operation(summary = "随机抽取题目")
    public ApiResponse<List<?>> randomSelect(
            @RequestParam Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "10") int count,
            Authentication auth) {
        // 对象级 ACL（2026-11-14 审计 U2）：随堂测取题虽走 VO 脱敏，但题干本身是课程内容——
        // 未选课学生不得跨课程捞题。P0-A1 起同时要求本课程教师已开放学生刷题。
        return ApiResponse.success(questionBankApplicationService.randomSelect(courseId, type, difficulty, count, auth));
    }

    /**
     * 按知识点查询题目
     */
    // 收口（2026-09-23，P0-3）：按知识点查题属题库读范围，仅供教师/管理员
    @GetMapping("/kp/{kpId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "按知识点查询题目")
    public ApiResponse<List<Question>> getByKnowledgePoint(@PathVariable Long kpId,
                                                           @RequestParam(required = false) Long courseId,
                                                           Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.questionsByKnowledgePoint(kpId, courseId, auth));
    }

    /**
     * 题库总览统计（总数 + 今日新增）。
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "题库总览统计",
            description = "totalCount 含 courseId 为空的历史题；todayCount 按 created_at 本机时区当日 00:00 起算")
    public ApiResponse<QuestionBankStatisticsVO> getBankStatistics(Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.bankStatistics(auth));
    }

    /**
     * 获取题目使用统计
     */
    // 收口（2026-09-23，P0-3）：题目使用统计属教学侧数据
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取题目使用统计")
    public ApiResponse<QuestionStatsVO> getStats(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.questionStats(id, auth));
    }

    /**
     * 获取易错题TOP N
     */
    // 收口（2026-09-23，P0-3）：易错题榜属教学分析数据，仅教师/管理员
    @GetMapping("/error-prone")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取易错题TOP N")
    public ApiResponse<List<Question>> errorProne(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        return ApiResponse.success(questionBankApplicationService.errorProne(courseId, limit, auth));
    }
}
