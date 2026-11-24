package com.aicourse.application.teacher;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师端批量导入应用服务（3E-05C）。
 *
 * <p>四条导入路径（题目 JSON / 题目 Excel / 章节 Excel / 知识点 Excel）的逐条容错循环、
 * 重复判定、两遍写入（父级回填）与结果组装集中在此，控制器只剩路由。5 处
 * {@code LambdaQueryWrapper} 随之下移。</p>
 *
 * <p>口径逐字保留：</li>
 * <ul>
 *   <li>题目 JSON 导入先<b>全量</b>过课程归属校验，再逐条 save（部分成功不写库的语义不变）；</li>
 *   <li>题目 Excel 导入：id 置空、courseId 取请求参数、createdBy 取登录身份、isActive 置 true；</li>
 *   <li>章节 / 知识点：同课程同标题（名称）视为重复跳过，支持文件内重复；父级以标题/名称引用，
 *       两遍写入；父级缺失或自引用时保留为顶级并进明细；</li>
 *   <li>单条异常逐条容错，回显「根本原因」并截断 200 字符（避免整段 SQL 泄露）。</li>
 * </ul>
 */
@Service
public class TeacherImportApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TeacherImportApplicationService.class);

    private static final int MAX_REASON_LENGTH = 200;
    private static final int DEFAULT_IMPORTANCE = 5;

    private final QuestionService questionService;
    private final CourseChapterService chapterService;
    private final KnowledgePointService knowledgePointService;
    private final CourseAccessControl courseAccessControl;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherImportApplicationService(QuestionService questionService,
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

    // ==================== 题目 ====================

    /** JSON 批量导入题目（兼容保留，逐题课程归属校验）。 */
    public TeacherImportResultVO importQuestions(List<Question> questions, Authentication auth) {
        for (Question question : questions) {
            courseAccessControl.assertCanManageCourse(question.getCourseId(), auth);
        }
        int count = 0;
        for (Question q : questions) {
            questionService.save(q);
            count++;
        }
        return new TeacherImportResultVO(count, null);
    }

    /** Excel 批量导入题目（需 .xlsx，可选指定归属课程）。 */
    public ImportOutcome importQuestionsExcel(List<Question> questions, Long courseId, Authentication auth) {
        if (courseId != null) {
            teacherAccessGuard.assertCourseOwner(courseId, auth);
        }
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
        return new ImportOutcome(buildImportResult(imported, questions.size(), skipped, errors),
                skipped > 0 || !errors.isEmpty());
    }

    // ==================== 章节 ====================

    /** Excel 批量导入章节；父级以标题引用，两遍写入。 */
    public ImportOutcome importChaptersExcel(List<ExcelUtil.ChapterRow> rows, Long courseId, Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
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

        return new ImportOutcome(buildImportResult(imported, rows.size(), skipped, errors),
                skipped > 0 || !errors.isEmpty());
    }

    // ==================== 知识点 ====================

    /** Excel 批量导入知识点；父知识点以名称引用，两遍写入。 */
    public ImportOutcome importKnowledgePointsExcel(List<ExcelUtil.KnowledgeRow> rows, Long courseId,
                                                    Authentication auth) {
        teacherAccessGuard.assertCourseOwner(courseId, auth);
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
                point.setImportance(row.importance() == null ? Integer.valueOf(DEFAULT_IMPORTANCE) : row.importance());
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

        return new ImportOutcome(buildImportResult(imported, rows.size(), skipped, errors),
                skipped > 0 || !errors.isEmpty());
    }

    // ==================== 内部助手 ====================

    /** 导入结果 + 是否「存在跳过或失败项」（决定提示语，控制器不再判）。 */
    public record ImportOutcome(TeacherImportResultVO result, boolean hasIssues) {
    }

    /** 组装导入结果（章节/知识点/题目三条路径共用）。 */
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
    static String rootCauseMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > MAX_REASON_LENGTH ? msg.substring(0, MAX_REASON_LENGTH) + "…" : msg;
    }
}
