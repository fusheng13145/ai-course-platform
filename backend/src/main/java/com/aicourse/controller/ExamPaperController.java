package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.exception.BusinessException;
import com.aicourse.vo.QuestionVO;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamPaperStatus;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperDetailVO;
import com.aicourse.vo.ExamPaperQuestionVO;
import com.aicourse.vo.ExamPaperStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 试卷管理接口控制器
 */
@Tag(name = "试卷管理", description = "试卷的新增、查询、修改、删除、发布、自动组卷等功能")
@RestController
@RequestMapping("/exam-papers")
public class ExamPaperController {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperController.class);

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ExamSessionService examSessionService;
    private final com.aicourse.security.ExamPaperAccessControl examPaperAccessControl;
    private final CourseService courseService;

    public ExamPaperController(ExamPaperService examPaperService,
                               ExamQuestionService examQuestionService,
                               QuestionService questionService,
                               ExamSessionService examSessionService,
                               com.aicourse.security.ExamPaperAccessControl examPaperAccessControl,
                               CourseService courseService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.examSessionService = examSessionService;
        this.examPaperAccessControl = examPaperAccessControl;
        this.courseService = courseService;
    }

    /**
     * 分页查询试卷列表（支持课程、状态、关键词筛选）
     * @param page 当前页码
     * @param size 每页条数
     * @param courseId 课程ID
     * @param status 试卷状态（仅教师/管理员生效；学生恒为已发布）
     * @param keyword 标题/描述关键词
     * @param auth 当前登录主体（用于按角色过滤可见范围）
     * @return 分页试卷数据
     */
    @GetMapping
    @Operation(summary = "分页查询试卷列表")
    public ApiResponse<PagedResponse<ExamPaper>> listByParams(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        LambdaQueryWrapper<ExamPaper> queryWrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            queryWrapper.eq(ExamPaper::getCourseId, courseId);
        }
        if (status != null && !status.isBlank() && RoleUtil.isTeacherOrAdmin(auth)) {
            queryWrapper.eq(ExamPaper::getStatus, status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            queryWrapper.and(w -> w.like(ExamPaper::getTitle, trimmed)
                    .or().like(ExamPaper::getDescription, trimmed));
        }
        // 修复（2026-09-21，P1-2）：学生仅可见已发布试卷。
        // 原实现无任何状态过滤，学生「我的考试」列表会列出教师尚未发布的草稿卷并渲染
        // 「开始考试」按钮——点击后虽被 startExam 拦截（试卷尚未发布），但界面已构成误导，
        // 且向学生暴露了未发布试卷的存在与标题。教师/管理员保留全量可见（需管理自己的草稿）。
        if (!RoleUtil.isTeacherOrAdmin(auth)) {
            queryWrapper.eq(ExamPaper::getStatus, ExamPaperStatus.PUBLISHED.name());
        }
        Page<ExamPaper> examPaperPage = examPaperService.page(Page.of(page, size), queryWrapper);
        fillCourseNames(examPaperPage.getRecords());
        return ApiResponse.success(PagedResponse.of(examPaperPage));
    }

    /**
     * 回填试卷所属课程名称（{@code courseName} 为非持久化展示字段）。
     *
     * <p>修复（2026-09-23，P1-4）：此前 {@code ExamPaper} 只含 courseId，前端
     * {@code exam.courseName || '未知课程'} 必然兜底，学生端考试卡片恒显示「未知课程」。
     * 此处去重后一次批量查询，避免 N+1。</p>
     */
    private void fillCourseNames(List<ExamPaper> papers) {
        if (papers == null || papers.isEmpty()) {
            return;
        }
        List<Long> courseIds = papers.stream()
                .map(ExamPaper::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (courseIds.isEmpty()) {
            return;
        }
        // 不用 Collectors.toMap：课程名理论上可为 null，toMap 遇 null value 会抛 NPE
        Map<Long, String> nameById = new HashMap<>();
        for (Course course : courseService.listByIds(courseIds)) {
            if (course.getId() != null) {
                nameById.put(course.getId(), course.getName());
            }
        }
        for (ExamPaper paper : papers) {
            paper.setCourseName(nameById.get(paper.getCourseId()));
        }
    }

    /**
     * 根据课程ID分页查询试卷
     * @param courseId 课程ID
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页试卷数据
     */
    @GetMapping("/course/{courseId}")
    @Operation(summary = "根据课程ID分页查询试卷")
    public ApiResponse<PagedResponse<ExamPaper>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        LambdaQueryWrapper<ExamPaper> queryWrapper = new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getCourseId, courseId);
        // 与 /exam-papers 列表口径保持一致（2026-09-23 补漏）：学生不得看到未发布试卷。
        // 本端点由 CourseExamPanel 使用，学生在课程页可到达，此前 P1-2 只收了主列表，漏了这一处。
        if (!RoleUtil.isTeacherOrAdmin(auth)) {
            queryWrapper.eq(ExamPaper::getStatus, ExamPaperStatus.PUBLISHED.name());
        }
        Page<ExamPaper> examPaperPage = examPaperService.page(Page.of(page, size), queryWrapper);
        fillCourseNames(examPaperPage.getRecords());
        return ApiResponse.success(PagedResponse.of(examPaperPage));
    }

    /**
     * 根据ID查询试卷详情
     * @param id 试卷ID
     * @return 试卷信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询试卷详情")
    public ApiResponse<ExamPaper> getById(@PathVariable Long id) {
        ExamPaper examPaper = examPaperService.getById(id);
        return ApiResponse.success(examPaper);
    }

    /**
     * 新增试卷（教师/管理员权限）
     * @param exam 试卷信息
     * @param auth 认证信息
     * @return 新增试卷数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "新增试卷")
    public ApiResponse<ExamPaper> create(@Valid @RequestBody ExamPaper exam, Authentication auth) {
        exam.setCreatorId((Long) auth.getPrincipal());
        examPaperService.save(exam);
        return ApiResponse.success("创建成功", exam);
    }

    /**
     * 修改试卷（教师/管理员权限）
     * @param id 试卷ID
     * @param exam 待更新试卷数据
     * @return 更新后试卷信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "修改试卷")
    public ApiResponse<ExamPaper> update(@PathVariable Long id, @Valid @RequestBody ExamPaper exam, Authentication auth) {
        // 归属校验（2026-09-14 修复）：仅试卷所属课程的授课教师或管理员可修改
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        exam.setId(id);
        examPaperService.updateById(exam);
        ExamPaper updatedExam = examPaperService.getById(id);
        return ApiResponse.success("更新成功", updatedExam);
    }

    /**
     * 删除试卷（教师/管理员权限）
     * @param id 试卷ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除试卷",
            description = "无考试记录时物理删除；已产生考试会话的试卷返回 409，避免外键级联销毁考试记录")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        // 归属校验（2026-09-14 修复）：仅试卷所属课程的授课教师或管理员可删除
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        // 修复（2026-11-01，B-13 ⑳）：禁止删除已产生考试记录的试卷。
        // 外键 fk_exam_questions_paper / fk_exam_sessions_paper 均为 ON DELETE CASCADE，
        // fk_proctor_sessions_exam 又对 exam_sessions 级联 —— 直接物理删会连坐销毁
        // 题目行、考试会话与监考事件。项目主裁定（2026-11-01）：存在 exam_sessions 时返回 409。
        long sessionCount = examSessionService.count(
                new LambdaQueryWrapper<com.aicourse.entity.ExamSession>()
                        .eq(com.aicourse.entity.ExamSession::getExamPaperId, id));
        if (sessionCount > 0) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "该试卷已产生 " + sessionCount + " 条考试记录，不能删除；请改为归档");
        }
        examPaperService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 查询试卷关联的题目列表（学生答题 / 教师预览共用，返回体统一脱敏）
     * @param id 试卷ID
     * @return 题目详情集合（不含标准答案与解析）
     */
    @GetMapping("/{id}/questions")
    @Operation(summary = "查询试卷关联的题目列表")
    public ApiResponse<List<ExamPaperQuestionVO>> getQuestions(@PathVariable Long id, Authentication auth) {
        // 对象级授权：管理员放行；学生须有已通过审批的选课；教师须为该课程授课教师。
        // 修复（2026-09-21，P0）：本端点原无任何归属校验，非选课学生可跨课程读取他人试卷题目。
        examPaperAccessControl.assertCanRead(examPaperService.getById(id), auth);

        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, id);
        List<ExamQuestion> examQuestionList = examQuestionService.list(queryWrapper);
        List<ExamPaperQuestionVO> resultList = new ArrayList<>();

        // 答案可见性按角色区分（2026-09-23 修正）：学生只看作答所需字段（去答案/解析），
        // 教师/管理员可见完整题目——讲评试卷需要标准答案。
        // 此前 P0 修复为「对所有角色统一脱敏」，误伤了教师视角（实测教师也取不到 correctAnswers）。
        boolean answerVisible = RoleUtil.isTeacherOrAdmin(auth);
        for (ExamQuestion examQuestion : examQuestionList) {
            Question question = questionService.getById(examQuestion.getQuestionId());
            if (question == null) {
                continue;
            }
            resultList.add(new ExamPaperQuestionVO(
                    examQuestion.getId(), examQuestion.getOrderIndex(),
                    examQuestion.getPoints(),
                    answerVisible ? question : toAnswerHidden(question)));
        }
        return ApiResponse.success(resultList);
    }

    /**
     * 作答可见的题目投影：<b>白名单</b>保留作答所需字段，剔除标准答案与解析。
     *
     * <p><b>安全修复（2026-09-21，P0）</b>：本端点此前直接下发 {@code Question} 实体（含
     * {@code correctAnswers} / {@code explanation}），实测学生在考前/考中调接口即可获取全部答案。
     * 现改为白名单投影——{@code correctAnswers}、{@code explanation}、{@code knowledgePoints}
     * 等字段在返回体中根本不存在，从结构上杜绝泄露（而非依赖调用方自觉）。</p>
     *
     * <p>{@code options} 保持原始 JSON 字符串形态，与前端 {@code parseOptions} 的解析逻辑一致
     * （避免二次序列化导致含逗号的选项被错误拆分）。教师如需查看答案与解析，走题库接口。</p>
     */
    private static Question toAnswerHidden(Question q) {
        Question safe = new Question();
        safe.setId(q.getId());
        safe.setCourseId(q.getCourseId());
        safe.setType(q.getType());
        safe.setContent(q.getContent());
        safe.setOptions(q.getOptions());
        safe.setDifficulty(q.getDifficulty());
        safe.setScore(q.getScore());
        safe.setUsageCount(q.getUsageCount());
        safe.setCorrectRate(q.getCorrectRate());
        return safe;
    }

    /**
     * 查询试卷完整详情（含题目列表）
     * @param id 试卷ID
     * @return 试卷详情（包含题目信息）
     */
    @GetMapping("/{id}/detail")
    @Operation(summary = "查询试卷完整详情")
    public ApiResponse<ExamPaperDetailVO> getDetail(@PathVariable Long id, Authentication auth) {
        // 对象级授权（同 /questions）：详情含全部题目内容，学生须已选课、教师须授课
        examPaperAccessControl.assertCanRead(examPaperService.getById(id), auth);
        ExamPaperDetailVO detail = examPaperService.getExamPaperDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 发布试卷（教师/管理员权限）
     * @param id 试卷ID
     * @return 发布后试卷信息
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "发布试卷 / 提交审核",
            description = "管理员直接发布（DRAFT/REJECTED/ARCHIVED → PUBLISHED）；教师提交审核（DRAFT/REJECTED → PENDING_REVIEW），学生仍不可见")
    public ApiResponse<ExamPaper> publish(@PathVariable Long id, Authentication auth) {
        // 归属校验：仅试卷所属课程的授课教师或管理员可发布（修复他师越权发布）
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        if (RoleUtil.isAdmin(auth)) {
            examPaperService.publishExamPaper(id);
            return ApiResponse.success("发布成功", examPaperService.getById(id));
        }
        examPaperService.submitExamPaperForReview(id);
        return ApiResponse.success("已提交审核", examPaperService.getById(id));
    }

    /**
     * 审核通过（管理员）：仅待审核态可操作。
     * @param id 试卷ID
     * @return 审核后的试卷
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "审核通过试卷", description = "PENDING_REVIEW → PUBLISHED，并推送选课学生通知")
    public ApiResponse<ExamPaper> approve(@PathVariable Long id) {
        examPaperService.approveExamPaper(id);
        return ApiResponse.success("审核通过", examPaperService.getById(id));
    }

    /**
     * 审核驳回（管理员）：仅待审核态可操作。
     * @param id 试卷ID
     * @param body 驳回原因（reason，可选）
     * @return 驳回后的试卷
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "审核驳回试卷", description = "PENDING_REVIEW → REJECTED，并向创建人发送驳回通知；原因仅进通知与日志，不落库")
    public ApiResponse<ExamPaper> reject(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        examPaperService.rejectExamPaper(id, reason);
        return ApiResponse.success("已驳回", examPaperService.getById(id));
    }

    /**
     * 批量替换试卷题目（教师/管理员权限，手动选题保存通道）。
     * @param id 试卷ID
     * @param body 题目清单（questionId/points/orderIndex，与 ExamQuestion 实体对齐）
     * @param auth 认证信息
     * @return 写入后的关联行列表
     */
    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "批量替换试卷题目")
    public ApiResponse<List<ExamQuestion>> replaceQuestions(@PathVariable Long id,
            @Valid @RequestBody ExamQuestionBatchDTO body, Authentication auth) {
        // 归属校验：与 update/delete/publish 同口径，仅试卷所属课程的授课教师或管理员可操作
        examPaperAccessControl.assertCanManage(examPaperService.getById(id), auth);
        List<ExamQuestion> saved = examPaperService.replaceExamQuestions(id, body.getQuestions());
        return ApiResponse.success("保存成功", saved);
    }

    /**
     * 自动组卷（教师/管理员权限）
     * 按题型数量和难度比例从题库随机抽取题目生成试卷
     * @param dto 组卷参数
     * @param auth 认证信息
     * @return 生成的试卷
     */
    @PostMapping("/auto-compose")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "自动组卷")
    public ApiResponse<ExamPaper> autoCompose(@Valid @RequestBody AutoComposeDTO dto, Authentication auth) {
        Long creatorId = (Long) auth.getPrincipal();
        // 归属校验：仅目标课程的授课教师或管理员可组卷（修复他师越权为他人课程组卷）
        examPaperAccessControl.assertCanCompose(dto.getCourseId(), auth);
        ExamPaper paper = examPaperService.autoComposeExamPaper(dto, creatorId);
        return ApiResponse.success("自动组卷成功", paper);
    }


    /**
     * 复制试卷（教师/管理员权限）
     * @param id 源试卷ID
     * @param body 包含新标题
     * @return 新试卷信息
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制试卷")
    public ApiResponse<ExamPaper> copy(@PathVariable Long id, @Valid @RequestBody Map<String, String> body) {
        String newTitle = body.get("newTitle");
        ExamPaper newPaper = examPaperService.copyExamPaper(id, newTitle);
        return ApiResponse.success("复制成功", newPaper);
    }

    /**
     * 获取试卷统计（题型分布、分值分布）
     * @param id 试卷ID
     * @return 统计数据
     */
    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取试卷统计")
    public ApiResponse<ExamPaperStatsVO> statistics(@PathVariable Long id, Authentication auth) {
        // 统计含题型/分值分布，属教学侧数据；学生无使用场景（前端仅教师端调用）
        examPaperAccessControl.assertCanRead(examPaperService.getById(id), auth);
        ExamPaperStatsVO stats = examPaperService.getPaperStatistics(id);
        return ApiResponse.success(stats);
    }

    public ExamSessionService getExamSessionService() {
        return examSessionService;
    }
}