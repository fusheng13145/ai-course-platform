package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.util.ExcelUtil;
import com.aicourse.vo.TeacherImportResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师端批量导入接口（2026-11-18 从 TeacherController 拆出）。
 *
 * <p>题目 JSON / 题目 Excel / 章节 Excel / 知识点 Excel 四条导入路径集中在此，
 * 共用文件校验、结果组装与根因截断，避免拆分后各控制器重复实现。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端批量导入接口", description = "题库/章节/知识点 Excel 与 JSON 批量导入")
public class TeacherImportController {

    private static final Logger log = LoggerFactory.getLogger(TeacherImportController.class);

    private final QuestionService questionService;
    private final CourseChapterService chapterService;
    private final KnowledgePointService knowledgePointService;
    private final CourseAccessControl courseAccessControl;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherImportController(QuestionService questionService,
                                   CourseChapterService chapterService,
                                   KnowledgePointService knowledgePointService,
                                   CourseAccessControl courseAccessControl,
                                   TeacherAccessGuard teacherAccessGuard) {
        this.questionService = questionService;
        this.chapterService = chapterService;
        this.knowledgePointService = knowledgePointService;
        this.courseAccessControl = courseAccessControl;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** JSON 批量导入题目（兼容保留，逐题课程归属校验）。 */
    @PostMapping("/questions/import")
    @Operation(summary = "批量导入题目", description = "JSON 批量导入（兼容保留，逐题校验课程归属）；Excel(.xlsx) 批量导入请使用 /teacher/questions/import-excel")
    public ApiResponse<TeacherImportResultVO> importQuestions(@Valid @RequestBody List<Question> questions,
                                                              Authentication auth) {
        for (Question question : questions) {
            courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        }
        int count = 0;
        for (Question q : questions) {
            questionService.save(q);
            count++;
        }
        return ApiResponse.success("导入完成", new TeacherImportResultVO(count, null));
    }

    /** 上传 Excel 批量导入题目（需 .xlsx，可选指定归属课程）。 */
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
            teacherAccessGuard.assertCourseOwner(courseId, auth);
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
            if (isDuplicateQuestion(q)) {
                skipped++;
                continue;
            }
            try {
                if (questionService.save(q)) {
                    imported++;
                }
            } catch (Exception e) {
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

    /** 上传 Excel 批量导入章节；父级以标题引用，两遍写入。 */
    @PostMapping(value = "/chapters/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入章节",
            description = "表头：章节标题/父章节标题/排序序号/时长(分钟)/是否发布/章节内容。"
                    + "父章节以标题引用（支持二级章节）；同课程同标题视为重复并跳过。")
    public ApiResponse<TeacherImportResultVO> importChaptersExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID") @RequestParam Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        teacherAccessGuard.assertCourseOwner(courseId, auth);

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

    /** 上传 Excel 批量导入知识点；父知识点以名称引用，两遍写入。 */
    @PostMapping(value = "/knowledge-points/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Excel 批量导入知识点",
            description = "表头：知识点名称/父知识点名称/难度/重要程度(1-10)/预计学习时长(分钟)/类型/描述。"
                    + "父知识点以名称引用；同课程同名称视为重复并跳过。")
    public ApiResponse<TeacherImportResultVO> importKnowledgePointsExcel(
            @Parameter(description = "Excel 文件(.xlsx)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "归属课程ID") @RequestParam Long courseId,
            Authentication auth) throws IOException {
        checkImportFile(file);
        teacherAccessGuard.assertCourseOwner(courseId, auth);

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

    /** 导入文件基础校验（三条 Excel 导入路径共用）。 */
    private void checkImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
    }

    /** 组装导入结果（章节/知识点两条路径共用）。 */
    private TeacherImportResultVO buildImportResult(int imported, int total, int skipped, List<String> errors) {
        TeacherImportResultVO result = new TeacherImportResultVO(imported, total);
        result.setSkippedCount(skipped);
        result.setFailedCount(errors.size());
        result.setErrors(errors.isEmpty() ? null : errors);
        return result;
    }

    /** 判断题目是否与库中已有记录重复（同课程 + 同题干）。 */
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
}
