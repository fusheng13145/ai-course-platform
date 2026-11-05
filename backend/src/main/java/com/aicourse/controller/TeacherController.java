package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.QuestionSaveDTO;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.aicourse.vo.*;
import com.aicourse.util.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师端统一核心接口
 * <p>
 * 旧 {@code TeacherController} 与重构后的 {@code CourseTeachingController} 已合并为单一控制器：
 * 统一课程建设 / 学情驾驶舱 / 作业题库管理 / 学情分析，并补齐课程归属校验（{@link #assertCourseOwner}），
 * 消除「任何教师可越权查看/修改/删除他人课程」的安全缺陷。
 * </p>
 * <p>
 * 全局前缀 {@code server.servlet.context-path = /api}，故完整有效路径 = {@code /api} + 下列相对路径。
 * 所有方法级均继承类级 {@code @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")}。
 * </p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端核心接口", description = "课程建设/学情驾驶舱/作业题库管理/学情分析（合并统一版）")
public class TeacherController {

    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    private final CourseService courseService;
    private final CourseChapterService chapterService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final QuestionService questionService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;
    private final TeacherCourseService teachingService;
    private final KnowledgePointService knowledgePointService;

    public TeacherController(CourseService courseService,
                             CourseChapterService chapterService,
                             TaskService taskService,
                             SubmissionService submissionService,
                             QuestionService questionService,
                             AnalyticsService analyticsService,
                             CourseEnrollmentService enrollmentService,
                             TeacherCourseService teachingService,
                             KnowledgePointService knowledgePointService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.questionService = questionService;
        this.analyticsService = analyticsService;
        this.enrollmentService = enrollmentService;
        this.teachingService = teachingService;
        this.knowledgePointService = knowledgePointService;
    }

    // ==================== 课程管理（8个接口，已加归属校验） ====================

    /** 创建课程并绑定当前教师 */
    @PostMapping("/courses")
    @Operation(summary = "创建课程")
    public ApiResponse<Course> createCourse(@Valid @RequestBody Course course, Authentication auth) {
        // 绑定授课教师与落库下沉至服务层（事务化）
        Course saved = courseService.createCourse(course, (Long) auth.getPrincipal());
        return ApiResponse.success("创建成功", saved);
    }

    /** 分页获取当前教师拥有的课程列表 */
    @GetMapping("/courses")
    @Operation(summary = "获取我的课程列表")
    public ApiResponse<PagedResponse<Course>> getCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        Page<Course> coursePage = courseService.page(Page.of(page, size),
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId));
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /** 获取课程详情（需课程归属校验） */
    @GetMapping("/courses/{courseId}")
    @Operation(summary = "获取课程详情")
    public ApiResponse<Course> getCourseDetail(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        return ApiResponse.success(course);
    }

    /** 更新课程信息（需课程归属校验） */
    @PutMapping("/courses/{courseId}")
    @Operation(summary = "更新课程")
    public ApiResponse<Course> updateCourse(@PathVariable Long courseId, @Valid @RequestBody Course course, Authentication auth) {
        assertCourseOwner(courseId, auth);
        // 写操作下沉至服务层（事务化）
        Course updated = courseService.updateCourseById(courseId, course);
        return ApiResponse.success("更新成功", updated);
    }

    /** 删除课程（需课程归属校验） */
    @DeleteMapping("/courses/{courseId}")
    @Operation(summary = "删除课程")
    public ApiResponse<Void> deleteCourse(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        // 写操作下沉至服务层（事务化）
        courseService.deleteCourseById(courseId);
        return ApiResponse.success("删除成功", null);
    }

    /** 为指定课程新增章节（需课程归属校验） */
    @PostMapping("/courses/{courseId}/chapters")
    @Operation(summary = "添加课程章节")
    public ApiResponse<CourseChapter> addChapter(@PathVariable Long courseId,
                                                  @Valid @RequestBody CourseChapter chapter,
                                                  Authentication auth) {
        assertCourseOwner(courseId, auth);
        chapter.setCourseId(courseId);
        chapterService.save(chapter);
        return ApiResponse.success("章节添加成功", chapter);
    }

    /** 更新课程章节（需课程归属校验） */
    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    @Operation(summary = "更新课程章节")
    public ApiResponse<CourseChapter> updateChapter(@PathVariable Long courseId,
                                                     @PathVariable Long chapterId,
                                                     @Valid @RequestBody CourseChapter chapter,
                                                     Authentication auth) {
        assertCourseOwner(courseId, auth);
        // 修复 D1（越权 IDOR）：仅校验 courseId 归属不足以挡住「自己的 courseId + 别人的 chapterId」，
        // 必须确认该 chapterId 确实属于 courseId，否则可篡改/越权改写他人课程下的章节。
        assertChapterBelongsToCourse(courseId, chapterId);
        chapter.setId(chapterId);
        chapter.setCourseId(courseId);
        chapterService.updateById(chapter);
        return ApiResponse.success("章节更新成功", chapterService.getById(chapterId));
    }

    /** 删除课程章节（需课程归属校验） */
    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    @Operation(summary = "删除课程章节")
    public ApiResponse<Void> deleteChapter(@PathVariable Long courseId,
                                            @PathVariable Long chapterId,
                                            Authentication auth) {
        assertCourseOwner(courseId, auth);
        // 修复 D1（越权 IDOR）：同 updateChapter，删除前必须校验章节归属，
        // 否则任意教师用自己的 courseId + 他人 chapterId 即可删除别人章节。
        assertChapterBelongsToCourse(courseId, chapterId);
        chapterService.removeById(chapterId);
        return ApiResponse.success("章节删除成功", null);
    }

    // ==================== 作业管理（8个接口，已加归属校验） ====================

    /** 创建作业（若指定课程则需归属校验） */
    @PostMapping("/assignments")
    @Operation(summary = "创建作业")
    public ApiResponse<Task> createAssignment(@Valid @RequestBody Task task, Authentication auth) {
        if (task.getCourseId() != null) assertCourseOwner(task.getCourseId(), auth);
        // 修复（2026-09-14）：显式落定草稿态，使「响应体 = 落库值」，避免前端据响应判断发布状态时得到 null
        if (task.getIsPublished() == null) task.setIsPublished(false);
        if (task.getStatus() == null) task.setStatus("DRAFT");
        taskService.save(task);
        return ApiResponse.success("创建成功", task);
    }

    /** 分页查询作业列表（可按课程筛选） */
    @GetMapping("/assignments")
    @Operation(summary = "分页查询作业列表")
    public ApiResponse<PagedResponse<Task>> getAssignments(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId);
        Page<Task> taskPage = taskService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    /** 获取作业详情（需作业归属校验） */
    @GetMapping("/assignments/{assignmentId}")
    @Operation(summary = "获取作业详情")
    public ApiResponse<Task> getAssignmentDetail(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Task task = taskService.getById(assignmentId);
        if (task == null) throw new BusinessException("作业不存在");
        return ApiResponse.success(task);
    }

    /** 更新作业（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}")
    @Operation(summary = "更新作业")
    public ApiResponse<Task> updateAssignment(@PathVariable Long assignmentId, @Valid @RequestBody Task task, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        // 写操作下沉至服务层（事务化）
        Task updated = taskService.updateTaskById(assignmentId, task);
        return ApiResponse.success("更新成功", updated);
    }

    /** 删除作业（需作业归属校验） */
    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "删除作业")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        taskService.removeById(assignmentId);
        return ApiResponse.success("删除成功", null);
    }

    /** 分页获取某作业的提交列表（需作业归属校验） */
    @GetMapping("/assignments/{assignmentId}/submissions")
    @Operation(summary = "获取作业提交列表")
    public ApiResponse<PagedResponse<Submission>> getSubmissions(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size),
                new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, assignmentId));
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    /** 教师批改某条作业提交（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}/submissions/{submissionId}")
    @Operation(summary = "批改作业")
    public ApiResponse<Submission> gradeSubmission(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @Valid @RequestBody Submission grade,
            Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        // 提交记录存在性/归属一致性校验与批改落库下沉至服务层（事务化）
        Submission submission = submissionService.gradeSubmissionByTeacher(
                assignmentId, submissionId, grade, (Long) auth.getPrincipal());
        return ApiResponse.success("批改完成", submission);
    }

    /** 发布作业（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}/publish")
    @Operation(summary = "发布作业")
    public ApiResponse<Task> publishAssignment(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Task task = taskService.publishTask(assignmentId);
        return ApiResponse.success("发布成功", task);
    }

    // ==================== 题库管理（7个接口，已加归属校验） ====================

    /** 创建题目（若指定课程则需归属校验） */
    @PostMapping("/questions")
    @Operation(summary = "创建题目",
            description = "字段真名：correctAnswers（MySQL JSON 列，必须是合法 JSON 文本：单选 \"A\"、多选 [\"A\",\"B\"]、判断 \"true\"；裸 A 会 400）；"
                    + "options/knowledgePoints 为 JSON 文本字符串列。创建时 courseId 必填。")
    public ApiResponse<Question> createQuestion(@Validated(QuestionSaveDTO.Create.class) @RequestBody QuestionSaveDTO dto,
                                                Authentication auth) {
        Question question = dto.toEntity();
        if (question.getCourseId() != null) assertCourseOwner(question.getCourseId(), auth);
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.createQuestion(question);
        return ApiResponse.success("创建成功", question);
    }

    /** 分页查询题目列表（可按课程/题型/难度筛选） */
    @GetMapping("/questions")
    @Operation(summary = "分页查询题目列表")
    public ApiResponse<PagedResponse<Question>> getQuestions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        if (courseId != null) assertCourseOwner(courseId, auth);
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty);
        Page<Question> questionPage = questionService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(questionPage));
    }

    /** 获取题目详情（需题目归属校验） */
    @GetMapping("/questions/{questionId}")
    @Operation(summary = "获取题目详情")
    public ApiResponse<Question> getQuestionDetail(@PathVariable Long questionId, Authentication auth) {
        assertQuestionOwner(questionId, auth);
        return ApiResponse.success(questionService.getById(questionId));
    }

    /** 更新题目（需题目归属校验） */
    @PutMapping("/questions/{questionId}")
    @Operation(summary = "更新题目",
            description = "字段真名与 JSON 列契约同创建端点；courseId 忽略请求值，始终沿用原题目课程。")
    public ApiResponse<Question> updateQuestion(@PathVariable Long questionId,
                                                @Valid @RequestBody QuestionSaveDTO dto,
                                                Authentication auth) {
        assertQuestionOwner(questionId, auth);
        Question existing = questionService.getById(questionId);
        Question question = dto.toEntity();
        question.setId(questionId);
        if (existing != null) question.setCourseId(existing.getCourseId());
        questionService.updateById(question);
        return ApiResponse.success("更新成功", questionService.getById(questionId));
    }

    /** 删除题目（需题目归属校验） */
    @DeleteMapping("/questions/{questionId}")
    @Operation(summary = "删除题目")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long questionId, Authentication auth) {
        assertQuestionOwner(questionId, auth);
        questionService.removeById(questionId);
        return ApiResponse.success("删除成功", null);
    }

    /** JSON 批量导入题目（兼容保留，推荐改用 Excel 导入） */
    @PostMapping("/questions/import")
    @Operation(summary = "批量导入题目", description = "JSON 批量导入（兼容保留）；Excel(.xlsx) 批量导入请使用 /teacher/questions/import-excel")
    public ApiResponse<TeacherImportResultVO> importQuestions(@Valid @RequestBody List<Question> questions) {
        int count = 0;
        for (Question q : questions) {
            questionService.save(q);
            count++;
        }
        return ApiResponse.success("导入完成", new TeacherImportResultVO(count, null));
    }

    /** 上传 Excel 批量导入题目（需 .xlsx，可选指定归属课程） */
    @PostMapping(value = "/questions/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入题目",
            description = "上传 .xlsx 文件批量导入题目（表头：题型/题干/选项(JSON)/正确答案/难度/分值/知识点/解析），courseId 可指定归属课程")
    public ApiResponse<TeacherImportResultVO> importQuestionsExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID（可选）") @RequestParam(required = false) Long courseId,
            Authentication auth) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
        if (courseId != null) {
            assertCourseOwner(courseId, auth);
        }
        List<Question> questions = ExcelUtil.readQuestions(file);
        if (questions.isEmpty()) {
            throw new BusinessException("文件中没有可导入的题目数据");
        }
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int idx = 0; idx < questions.size(); idx++) {
            Question q = questions.get(idx);
            int seq = idx + 1;
            q.setId(null);
            q.setCourseId(courseId);
            q.setCreatedBy((Long) auth.getPrincipal());
            q.setIsActive(true);
            // 去重（修复 2026-09-23，P2-2）：原实现重复导入同一文件会产生重复题目（实测两次得 10 条）。
            // 口径：同课程 + 同题干视为重复 → 跳过并计数，不做覆盖。
            if (isDuplicateQuestion(q)) {
                skipped++;
                continue;
            }
            try {
                if (questionService.save(q)) {
                    imported++;
                }
            } catch (Exception e) {
                // 逐条容错（修复 2026-09-23，P2-1）：原实现单条失败即整批中止，且回显
                // 「数据完整性校验失败」完全不指明是第几条、什么原因，用户无从自查。
                // 现降级为「跳过该条并记录序号 + 根本原因」，其余条目照常导入。
                String reason = rootCauseMessage(e);
                log.warn("Excel 题目导入失败（第 {} 条）：{}", seq, reason);
                errors.add("第 " + seq + " 条：" + reason);
            }
        }
        TeacherImportResultVO result = new TeacherImportResultVO(imported, questions.size());
        result.setSkippedCount(skipped);
        result.setFailedCount(errors.size());
        result.setErrors(errors.isEmpty() ? null : errors);
        return ApiResponse.success(
                (skipped > 0 || !errors.isEmpty()) ? "导入完成（存在跳过或失败项，详见明细）" : "导入完成",
                result);
    }

    // ==================== 章节 / 知识点批量导入（2026-09-23 新增，P2-4） ====================

    /**
     * 上传 Excel 批量导入章节。
     *
     * <p>父级以「父章节标题」引用（使用者拿到的是大纲文本，不可能知道数据库 ID），
     * 因此分两遍写入：第一遍建全部章节、第二遍回填 {@code parentId} —— 这样父章节出现在
     * 文件任意位置（甚至位于子章节之后）都能正确关联。同课程同标题视为重复并跳过。</p>
     */
    @PostMapping(value = "/chapters/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入章节",
            description = "表头：章节标题/父章节标题/排序序号/时长(分钟)/是否发布/章节内容。"
                    + "父章节以标题引用（支持二级章节）；同课程同标题视为重复并跳过。")
    public ApiResponse<TeacherImportResultVO> importChaptersExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID") @RequestParam Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        assertCourseOwner(courseId, auth);

        List<ExcelUtil.ChapterRow> rows = ExcelUtil.readChapters(file);
        if (rows.isEmpty()) {
            throw new BusinessException("文件中没有可导入的章节数据");
        }

        Set<String> existingTitles = chapterService
                .list(new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId))
                .stream().map(CourseChapter::getTitle).collect(Collectors.toSet());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<CourseChapter> pendingParent = new ArrayList<>();
        Map<Long, String> parentTitleById = new HashMap<>();

        for (int idx = 0; idx < rows.size(); idx++) {
            ExcelUtil.ChapterRow row = rows.get(idx);
            int seq = idx + 1;
            if (existingTitles.contains(row.title())) {
                skipped++;
                continue;
            }
            try {
                CourseChapter chapter = new CourseChapter();
                chapter.setCourseId(courseId);
                chapter.setTitle(row.title());
                chapter.setContent(row.content());
                chapter.setOrderIndex(row.orderIndex() == null ? Integer.valueOf(idx) : row.orderIndex());
                chapter.setDuration(row.duration());
                chapter.setIsPublished(row.isPublished());
                if (chapterService.save(chapter)) {
                    imported++;
                    existingTitles.add(row.title());
                    if (row.parentTitle() != null) {
                        pendingParent.add(chapter);
                        parentTitleById.put(chapter.getId(), row.parentTitle());
                    }
                }
            } catch (Exception e) {
                String reason = rootCauseMessage(e);
                log.warn("Excel 章节导入失败（第 {} 条）：{}", seq, reason);
                errors.add("第 " + seq + " 条（" + row.title() + "）：" + reason);
            }
        }

        // 第二遍：回填父级（父章节可能来自本文件，也可能是库中已有）
        for (CourseChapter child : pendingParent) {
            String parentTitle = parentTitleById.get(child.getId());
            CourseChapter parent = chapterService.getOne(new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCourseId, courseId)
                    .eq(CourseChapter::getTitle, parentTitle)
                    .last("LIMIT 1"));
            if (parent == null || parent.getId().equals(child.getId())) {
                log.warn("章节「{}」的父章节「{}」不存在，已作为顶级章节保留", child.getTitle(), parentTitle);
                errors.add("章节「" + child.getTitle() + "」：未找到父章节「" + parentTitle + "」，已作为顶级章节导入");
                continue;
            }
            child.setParentId(parent.getId());
            chapterService.updateById(child);
        }

        return ApiResponse.success(
                (skipped > 0 || !errors.isEmpty()) ? "导入完成（存在跳过或失败项，详见明细）" : "导入完成",
                buildImportResult(imported, rows.size(), skipped, errors));
    }

    /**
     * 上传 Excel 批量导入知识点。
     *
     * <p>与章节导入同构：父知识点以名称引用、两遍写入、同课程同名称跳过。</p>
     */
    @PostMapping(value = "/knowledge-points/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入知识点",
            description = "表头：知识点名称/父知识点名称/难度/重要程度(1-10)/预计学习时长(分钟)/类型/描述。"
                    + "父知识点以名称引用；同课程同名称视为重复并跳过。")
    public ApiResponse<TeacherImportResultVO> importKnowledgePointsExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID") @RequestParam Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        assertCourseOwner(courseId, auth);

        List<ExcelUtil.KnowledgeRow> rows = ExcelUtil.readKnowledgePoints(file);
        if (rows.isEmpty()) {
            throw new BusinessException("文件中没有可导入的知识点数据");
        }

        Set<String> existingNames = knowledgePointService
                .list(new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId))
                .stream().map(KnowledgePoint::getName).collect(Collectors.toSet());

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<KnowledgePoint> pendingParent = new ArrayList<>();
        Map<Long, String> parentNameById = new HashMap<>();

        for (int idx = 0; idx < rows.size(); idx++) {
            ExcelUtil.KnowledgeRow row = rows.get(idx);
            int seq = idx + 1;
            if (existingNames.contains(row.name())) {
                skipped++;
                continue;
            }
            try {
                KnowledgePoint point = new KnowledgePoint();
                point.setCourseId(courseId);
                point.setName(row.name());
                point.setDescription(row.description());
                point.setDifficultyLevel(row.difficultyLevel());
                // importance / order_index 在库中为 NOT NULL DEFAULT，缺省时显式给值避免依赖列默认
                point.setImportance(row.importance() == null ? Integer.valueOf(5) : row.importance());
                point.setEstimatedTime(row.estimatedTime());
                point.setPointType(row.pointType());
                point.setOrderIndex(idx);
                if (knowledgePointService.save(point)) {
                    imported++;
                    existingNames.add(row.name());
                    if (row.parentName() != null) {
                        pendingParent.add(point);
                        parentNameById.put(point.getId(), row.parentName());
                    }
                }
            } catch (Exception e) {
                String reason = rootCauseMessage(e);
                log.warn("Excel 知识点导入失败（第 {} 条）：{}", seq, reason);
                errors.add("第 " + seq + " 条（" + row.name() + "）：" + reason);
            }
        }

        for (KnowledgePoint child : pendingParent) {
            String parentName = parentNameById.get(child.getId());
            KnowledgePoint parent = knowledgePointService.getOne(new LambdaQueryWrapper<KnowledgePoint>()
                    .eq(KnowledgePoint::getCourseId, courseId)
                    .eq(KnowledgePoint::getName, parentName)
                    .last("LIMIT 1"));
            if (parent == null || parent.getId().equals(child.getId())) {
                log.warn("知识点「{}」的父知识点「{}」不存在，已作为顶级保留", child.getName(), parentName);
                errors.add("知识点「" + child.getName() + "」：未找到父知识点「" + parentName + "」，已作为顶级导入");
                continue;
            }
            child.setParentId(parent.getId());
            knowledgePointService.updateById(child);
        }

        return ApiResponse.success(
                (skipped > 0 || !errors.isEmpty()) ? "导入完成（存在跳过或失败项，详见明细）" : "导入完成",
                buildImportResult(imported, rows.size(), skipped, errors));
    }

    /** 导入文件基础校验（两条导入路径共用，避免重复判断）。 */
    private void checkImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
    }

    /** 组装导入结果（三条导入路径共用）。 */
    private TeacherImportResultVO buildImportResult(int imported, int total, int skipped, List<String> errors) {
        TeacherImportResultVO result = new TeacherImportResultVO(imported, total);
        result.setSkippedCount(skipped);
        result.setFailedCount(errors.size());
        result.setErrors(errors.isEmpty() ? null : errors);
        return result;
    }

    /**
     * 判断题目是否与库中已有记录重复（同课程 + 同题干）。
     *
     * <p>修复（2026-09-23，P2-2）：导入原本不幂等，同一文件重复导入会产生重复题目。</p>
     */
    private boolean isDuplicateQuestion(Question q) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getContent, q.getContent());
        if (q.getCourseId() != null) {
            wrapper.eq(Question::getCourseId, q.getCourseId());
        } else {
            wrapper.isNull(Question::getCourseId);
        }
        return questionService.count(wrapper) > 0;
    }

    /** 取最根本的异常原因并截断过长的 SQL/驱动信息，便于安全回显给用户。 */
    private static String rootCauseMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }

    /** 导出题库为 Excel 文件（可按课程筛选） */
    @GetMapping("/questions/export")
    @Operation(summary = "导出题库", description = "导出题库为 Excel(.xlsx) 文件，可按课程筛选")
    public void exportQuestions(@RequestParam(required = false) Long courseId, Authentication auth,
                                HttpServletResponse response) throws IOException {
        if (courseId != null) {
            assertCourseOwner(courseId, auth);
        }
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId);
        List<Question> questions = questionService.list(qw);
        ExcelUtil.setDownloadHeaders(response, "题库导出.xlsx");
        ExcelUtil.writeQuestions(response.getOutputStream(), questions);
    }

    // ==================== 学情分析（5个接口，课程/作业级已加归属校验） ====================

    /** 获取班级学情分析（需课程归属校验） */
    @GetMapping("/analytics/class/{courseId}")
    @Operation(summary = "获取班级学情分析")
    public ApiResponse<Map<String, Object>> getClassAnalytics(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getCourseAnalysis(courseId);
        return ApiResponse.success(data);
    }

    /** 获取单个学生的学情分析 */
    @GetMapping("/analytics/students/{studentId}")
    @Operation(summary = "获取学生学情分析")
    public ApiResponse<Map<String, Object>> getStudentAnalytics(@PathVariable Long studentId, Authentication auth) {
        // 修复 D2（越权 IDOR）：原方法无 Authentication，任意教师可对任意 studentId 拉全量学情。
        // 复用服务层既有的「教师—学生」归属判定 hasApprovedEnrollmentWithTeacher，
        // 仅当该学生确为当前教师名下课程的在读（有效且审批通过）学生时放行；管理员放行。
        assertTeacherOwnsStudent(studentId, auth);
        Map<String, Object> data = analyticsService.getUserDashboard(studentId);
        return ApiResponse.success(data);
    }

    /** 获取作业学情分析（需作业归属校验） */
    @GetMapping("/analytics/assignments/{assignmentId}")
    @Operation(summary = "获取作业学情分析")
    public ApiResponse<TaskCompletionStatsVO> getAssignmentAnalytics(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        TaskCompletionStatsVO stats = taskService.getTaskCompletionStats(assignmentId);
        return ApiResponse.success(stats);
    }

    /** 获取知识点分析（可按课程筛选） */
    @GetMapping("/analytics/knowledge-points")
    @Operation(summary = "获取知识点分析")
    public ApiResponse<Map<String, Object>> getKnowledgePointAnalytics(
            @RequestParam(required = false) Long courseId, Authentication auth) {
        if (courseId != null) assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getGradeDistribution(courseId);
        return ApiResponse.success(data);
    }

    /** 导出学情报告（返回报告 JSON，需课程归属校验） */
    @GetMapping("/analytics/report/export")
    @Operation(summary = "导出学情报告", description = "返回学情报告 JSON 数据（含报告各章节内容），前端基于该数据生成 PDF 文件下载")
    public ApiResponse<Map<String, Object>> exportReport(@RequestParam Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> report = analyticsService.exportReport(courseId);
        return ApiResponse.success("报告导出成功", report);
    }

    // ==================== 课程建设 / 学情驾驶舱（原 CourseTeachingController 合并） ====================

    /** 课程驾驶舱：聚合课程各项教学指标（需课程归属校验） */
    @GetMapping("/courses/{courseId}/dashboard")
    @Operation(summary = "课程驾驶舱", description = "聚合课程概览/内容量/学习指标/待批改/趋势/预警学生/章节树/AI解读")
    public ApiResponse<Map<String, Object>> courseDashboard(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getCourseTeachingDashboard(courseId, operatorId));
    }

    /** AI 生成建课大纲（章节与任务点建议） */
    @PostMapping("/courses/ai/generate-outline")
    @Operation(summary = "AI 建课大纲", description = "输入主题/分类/难度，由 AI 生成章节与任务点建议大纲")
    public ApiResponse<Map<String, Object>> generateOutline(
            @Parameter(description = "课程主题") @RequestParam String topic,
            @Parameter(description = "课程分类（可选）") @RequestParam(required = false) String category,
            @Parameter(description = "难度等级（可选）") @RequestParam(required = false) String level,
            @Parameter(description = "建议章节数（默认6）") @RequestParam(defaultValue = "6") int chapterCount,
            Authentication auth) {
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.generateCourseOutlineByAI(topic, category, level, chapterCount, operatorId));
    }

    /** 克隆课程为新的草稿课程（需源课程归属校验） */
    @PostMapping("/courses/{courseId}/clone")
    @Operation(summary = "克隆课程", description = "复制课程元信息与章节结构为新课程（状态置为草稿）")
    public ApiResponse<Map<String, Object>> cloneCourse(
            @Parameter(description = "源课程ID") @PathVariable Long courseId,
            @Parameter(description = "新课程名称") @RequestParam(required = false) String newName,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success("克隆成功", teachingService.cloneCourse(courseId, newName, operatorId));
    }

    /** 获取教师每门课程的聚合指标摘要 */
    @GetMapping("/courses/summaries")
    @Operation(summary = "我的课程聚合指标", description = "返回教师每门课程的摘要：学生数/平均进度/完成率/待批改")
    public ApiResponse<List<Map<String, Object>>> myCourseSummaries(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getMyCourseSummaries(teacherId));
    }

    /** 增强型教师仪表盘（多维下钻与预警）。AI 总览已摘出，见 /dashboard/ai-overview */
    @GetMapping("/dashboard/overview")
    @Operation(summary = "增强教师仪表盘", description = "课程下钻卡片/跨课预警学生/任务点完成概览/平台均值对比；aiOverview 只回 deferred 标记")
    public ApiResponse<Map<String, Object>> teacherDashboard(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getTeacherDashboard(teacherId));
    }

    /** 教师 AI 教学总览（独立入口：首页聚合不再同步调用模型，避免被 LLM 阻塞） */
    @GetMapping("/dashboard/ai-overview")
    @Operation(summary = "教师 AI 教学总览", description = "按需获取 AI 学情解读；与 /dashboard/overview 同统计口径，只是额外做一次模型调用")
    public ApiResponse<Map<String, Object>> teacherAiOverview(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getTeacherAIOverview(teacherId));
    }

    /** 获取课程完整章节树（需课程归属校验） */
    @GetMapping("/courses/{courseId}/chapters/tree")
    @Operation(summary = "完整章节树", description = "返回带父子嵌套、内容、发布状态的课程章节树")
    public ApiResponse<List<Map<String, Object>>> chapterTree(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        return ApiResponse.success(teachingService.getFullChapterTree(courseId));
    }

    /** 获取课程关联班级聚合信息（需课程归属校验） */
    @GetMapping("/courses/{courseId}/classes")
    @Operation(summary = "课程关联班级", description = "按选课学生所属班级聚合，返回班级学生数/待批改数/课表说明")
    public ApiResponse<List<Map<String, Object>>> courseClasses(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        return ApiResponse.success(teachingService.getCourseClasses(courseId));
    }

    // ==================== 课程归属校验 ====================

    /**
     * 课程归属校验：教师须为课程授课教师，管理员放行。
     * 任何以 courseId 为入参的接口都应先调用本方法，杜绝越权访问他人课程。
     */
    private void assertCourseOwner(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) return;
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        // 语义修正（2026-09-14）：越权拒绝应为 403 而非默认 400
        if (!userId.equals(course.getTeacherId())) throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该课程");
    }

    private void assertTaskOwner(Long taskId, Authentication auth) {
        Task task = taskService.getById(taskId);
        if (task == null) throw new BusinessException(ApiCode.NOT_FOUND, "作业不存在");
        assertCourseOwner(task.getCourseId(), auth);
    }

    private void assertQuestionOwner(Long questionId, Authentication auth) {
        Question question = questionService.getById(questionId);
        if (question == null) throw new BusinessException("题目不存在");
        if (question.getCourseId() != null) assertCourseOwner(question.getCourseId(), auth);
    }

    /**
     * 章节归属校验：chapterId 必须真实属于 courseId。
     *
     * <p>修复 D1（越权 IDOR）：{@link #assertCourseOwner} 只证明「教师拥有 courseId」，
     * 无法证明「chapterId 挂在该 courseId 下」。若缺失此校验，任意教师用自己名下的 courseId
     * 拼接他人课程的 chapterId，即可改写/删除他人章节。异常类型/文案与同文件其它 assertXxxOwner 对齐：
     * 不存在 → 404，跨课程 → 403。</p>
     */
    private void assertChapterBelongsToCourse(Long courseId, Long chapterId) {
        CourseChapter chapter = chapterService.getById(chapterId);
        if (chapter == null) throw new BusinessException(ApiCode.NOT_FOUND, "章节不存在");
        if (!courseId.equals(chapter.getCourseId())) throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该章节");
    }

    /**
     * 教师—学生归属校验：当前教师须为该学生的授课教师（其在读学生），管理员放行。
     *
     * <p>修复 D2（越权 IDOR）：复用 {@link CourseEnrollmentService#hasApprovedEnrollmentWithTeacher}
     * 的既有服务层归属判定，杜绝越权拉取任意学生学情。拒绝语义与项目一致返回 403。</p>
     */
    private void assertTeacherOwnsStudent(Long studentId, Authentication auth) {
        if (isAdmin(auth)) return;
        Long teacherId = (Long) auth.getPrincipal();
        if (!enrollmentService.hasApprovedEnrollmentWithTeacher(studentId, teacherId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权查看该学生学情");
        }
    }

    /** 是否管理员角色：课程归属类校验对管理员放行。 */
    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
    }
}
