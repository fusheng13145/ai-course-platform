package com.aicourse.application.exam;

import com.aicourse.common.ActivityTargetsPayload;
import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.dto.ActivityTargetDTO;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamPaperSaveDTO;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamPaperStatus;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.ExamPaperAccessControl;
import com.aicourse.security.RoleUtil;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperQuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 试卷域用例编排（application 层，批次 3E-03A）。
 *
 * <p><b>为什么从控制器搬到这里</b>：试卷这批端点的实质不是「转发一个请求」，而是几段有顺序的业务动作：</p>
 * <ul>
 *   <li><b>建卷/改卷</b>：对象级授权 → 创建者落身份 → 管理员不得设定教学班可见范围 →
 *       可见范围归一化与目标校验 → 落库 → 回写目标教学班关联。六步里任何一步挪动顺序都会改变授权语义，
 *       而这些原先都写在 {@code ExamPaperController} 的方法体里，改一个动作只能靠读代码确认上下文。</li>
 *   <li><b>删卷</b>：归属校验 → 统计考试会话数 → 有会话则 409（禁物理删除）→ 才能删。
 *       其中「统计」要直接拼 {@code LambdaQueryWrapper<ExamSession>}，持久化细节缩在 Web 层。</li>
 *   <li><b>列表</b>：拼查询条件（状态/关键词/教学班可见范围）+ 学生强制只看已发布 +
 *       批量回填 {@code courseName} 防 N+1。三件事各自有历史修复记录（P1-2、P1-4、WP6），
 *       混在控制器里只能靠注释传承。</li>
 *   <li><b>题目读取</b>：答案可见性按角色裁剪 + 逐题 {@code getById} 的 N+1 —— 后者本批顺手改成批量取。</li>
 * </ul>
 *
 * <p><b>事务口径</b>：本层<b>刻意不开事务</b>，与改造前一致 —— 每个用例都是若干服务调用的组合，
 * 而 {@code examPaperService}／{@code activityTargetService} 的写方法各自已带事务。
 * 「建卷 = 落库 + 回写目标教学班」这两步的原子性<b>仍是改造前的旧账</b>（回写失败会留下可见范围半写的卷），
 * 本批只做迁移、不夹带行为变更；补强需在阶段5 用 {@code @SpringBootTest} 写事务用例（WebMvcTest 切片没有事务管理器）。</p>
 */
@Slf4j
@Service
public class ExamPaperApplicationService {

    private final ExamPaperService examPaperService;
    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final ExamSessionService examSessionService;
    private final ExamPaperAccessControl examPaperAccessControl;
    private final CourseService courseService;
    private final ActivityVisibilityGuard activityVisibilityGuard;
    private final ActivityTargetService activityTargetService;

    public ExamPaperApplicationService(ExamPaperService examPaperService,
                                       ExamQuestionService examQuestionService,
                                       QuestionService questionService,
                                       ExamSessionService examSessionService,
                                       ExamPaperAccessControl examPaperAccessControl,
                                       CourseService courseService,
                                       ActivityVisibilityGuard activityVisibilityGuard,
                                       ActivityTargetService activityTargetService) {
        this.examPaperService = examPaperService;
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.examSessionService = examSessionService;
        this.examPaperAccessControl = examPaperAccessControl;
        this.courseService = courseService;
        this.activityVisibilityGuard = activityVisibilityGuard;
        this.activityTargetService = activityTargetService;
    }

    // ===================== 列表（含历史修复口径） =====================

    /**
     * 分页查询试卷列表。
     *
     * <p>口径（三处历史修复，逐条保留）：</p>
     * <ol>
     *   <li>学生恒只见 {@code PUBLISHED}（P1-2：否则学生列表出现教师草稿卷，界面误导且泄漏未发布标题）；</li>
     *   <li>教学班可见范围由 {@code ActivityVisibilityGuard} 收口（WP6：COURSE 全课程、CLASSES 仅目标班）；</li>
     *   <li>{@code courseName} 批量回填（P1-4：否则前端恒显示「未知课程」，且不能逐行查造成 N+1）。</li>
     * </ol>
     */
    public Page<ExamPaper> listByParams(long page, long size, Long courseId, Long teachingClassId,
                                        String status, String keyword, Authentication auth) {
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
        if (!RoleUtil.isTeacherOrAdmin(auth)) {
            queryWrapper.eq(ExamPaper::getStatus, ExamPaperStatus.PUBLISHED.name());
        }
        activityVisibilityGuard.applyExamPaperScope(queryWrapper, auth, teachingClassId);
        Page<ExamPaper> examPaperPage = examPaperService.page(Page.of(page, size), queryWrapper);
        fillCourseNames(examPaperPage.getRecords());
        return examPaperPage;
    }

    /** 按课程分页查询试卷（与学生端课程页口径一致，同样强制学生只看已发布）。 */
    public Page<ExamPaper> listByCourse(Long courseId, long page, long size, Long teachingClassId,
                                        Authentication auth) {
        LambdaQueryWrapper<ExamPaper> queryWrapper = new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getCourseId, courseId);
        if (!RoleUtil.isTeacherOrAdmin(auth)) {
            queryWrapper.eq(ExamPaper::getStatus, ExamPaperStatus.PUBLISHED.name());
        }
        activityVisibilityGuard.applyExamPaperScope(queryWrapper, auth, teachingClassId);
        Page<ExamPaper> examPaperPage = examPaperService.page(Page.of(page, size), queryWrapper);
        fillCourseNames(examPaperPage.getRecords());
        return examPaperPage;
    }

    // ===================== 建卷 / 改卷 =====================

    /**
     * 新建试卷：授权 → 创建者 → 『管理员不得指定教学班』→ 可见范围归一化 → 落库 → 回写目标。
     *
     * <p>事务：整体回滚。落库成功而目标回写失败，会留下「可见范围写了一半」的试卷。</p>
     */
    public ExamPaper create(ExamPaperSaveDTO dto, Authentication auth) {
        ExamPaper exam = dto.toEntity();
        // 对象级授权：试卷必须归属当前教师（管理员按治理口径放行），防止甲课教师为乙课创建试卷。
        examPaperAccessControl.assertCanCompose(exam.getCourseId(), auth);
        exam.setCreatorId((Long) auth.getPrincipal());
        String requestedVisibility = ActivityVisibility.normalizeOrNull(exam.getVisibility());
        if (ActivityVisibility.CLASSES.equals(requestedVisibility)
                || (exam.getTeachingClassIds() != null && !exam.getTeachingClassIds().isEmpty())) {
            // 管理员只读治理，不得自行圈定目标教学班（WP6 验收第 4 条）
            activityVisibilityGuard.assertNotAdmin(auth);
        }
        String visibility = activityTargetService.normalizeAndValidate(
                null, exam.getCourseId(), exam.getVisibility(), exam.getTeachingClassIds(), true);
        exam.setVisibility(visibility);
        examPaperService.save(exam);
        activityTargetService.replaceExamPaperTargets(exam.getId(), visibility, exam.getTeachingClassIds());
        return exam;
    }

    /**
     * 修改试卷：授权 → 判断是否触碰可见范围 → 归一化 → 局部更新 → 回写目标。
     *
     * <p>{@code visibility}/{@code teachingClassIds} 任一非空即视为「触碰」目标教学班——这正是
     * {@code ExamPaperSaveDTO} 必须保持 {@code null} 语义（不能给默认值）的原因。</p>
     */
    public ExamPaper update(Long id, ExamPaperSaveDTO dto, Authentication auth) {
        ExamPaper exam = dto.toEntity();
        ExamPaper existing = requirePaper(id);
        examPaperAccessControl.assertCanManage(existing, auth);
        boolean targetsTouched = exam.getVisibility() != null || exam.getTeachingClassIds() != null;
        String normalizedVisibility = null;
        if (targetsTouched) {
            activityVisibilityGuard.assertNotAdmin(auth);
            String effectiveVisibility = exam.getVisibility() != null
                    ? exam.getVisibility() : existing.getVisibility();
            normalizedVisibility = activityTargetService.normalizeAndValidate(
                    id, existing.getCourseId(), effectiveVisibility, exam.getTeachingClassIds(), true);
            exam.setVisibility(normalizedVisibility);
        }
        // status/isActive/creatorId 由服务层白名单剔除（审核流转只走 publish/approve/reject），
        // 并校验显式传入的 totalScore 与题目分值合计一致
        ExamPaper updatedExam = examPaperService.updateExamPaper(id, exam);
        if (targetsTouched) {
            activityTargetService.replaceExamPaperTargets(id, normalizedVisibility, exam.getTeachingClassIds());
        }
        return updatedExam;
    }

    // ===================== 删除 / 归档 =====================

    /**
     * 删除试卷：归属校验 → 有考试记录则 409 → 物理删除。
     *
     * <p>为什么禁止硬删：{@code fk_exam_questions_paper}/{@code fk_exam_sessions_paper} 是
     * {@code ON DELETE CASCADE}，而 {@code fk_proctor_sessions_exam} 又对 {@code exam_sessions} 级联，
     * 直接物理删会连坐销毁题目行、考试会话与监考事件。归档是文案指定的出路。</p>
     */
    public void delete(Long id, Authentication auth) {
        examPaperAccessControl.assertCanManage(requirePaper(id), auth);
        long sessionCount = examSessionService.count(
                new LambdaQueryWrapper<ExamSession>().eq(ExamSession::getExamPaperId, id));
        if (sessionCount > 0) {
            throw new BusinessException(ApiCode.CONFLICT,
                    "该试卷已产生 " + sessionCount + " 条考试记录，不能删除；请改为归档");
        }
        examPaperService.removeById(id);
    }

    /** 归档试卷：{@code PUBLISHED → ARCHIVED}，幂等；返回归档后的实体供出参使用。 */
    public ExamPaper archive(Long id, Authentication auth) {
        examPaperAccessControl.assertCanManage(requirePaper(id), auth);
        examPaperService.archiveExamPaper(id);
        return requirePaper(id);
    }

    // ===================== 审核流转 =====================

    /** 教师提交审核：管理员走不通（管理员只经 {@code /approve} 完成审核动作）。 */
    public ExamPaper publish(Long id, Authentication auth) {
        activityVisibilityGuard.assertNotAdmin(auth);
        examPaperAccessControl.assertCanManage(requirePaper(id), auth);
        examPaperService.submitExamPaperForReview(id);
        return requirePaper(id);
    }

    /** 管理员审核通过（{@code PENDING_REVIEW → PUBLISHED}，并由服务层推送学生通知）。 */
    public ExamPaper approve(Long id) {
        examPaperService.approveExamPaper(id);
        return requirePaper(id);
    }

    /** 管理员驳回（原因只进通知与日志，不落库）。 */
    public ExamPaper reject(Long id, String reason) {
        examPaperService.rejectExamPaper(id, reason);
        return requirePaper(id);
    }

    // ===================== 组卷 / 复制 =====================

    /** 自动组卷：授权 → 调服务层按题型/难度抽题。 */
    public ExamPaper autoCompose(AutoComposeDTO dto, Long creatorId, Authentication auth) {
        examPaperAccessControl.assertCanCompose(dto.getCourseId(), auth);
        return examPaperService.autoComposeExamPaper(dto, creatorId);
    }

    /** 复制试卷：源卷必须可管理，否则可借「复制」拿到他人题目与标准答案。 */
    public ExamPaper copy(Long id, String newTitle, Authentication auth) {
        examPaperAccessControl.assertCanManage(requirePaper(id), auth);
        return examPaperService.copyExamPaper(id, newTitle);
    }

    // ===================== 题目读取 / 批量替换 =====================

    /**
     * 查询试卷题目（学生答题 / 教师预览共用）。
     *
     * <p>两处口径：</p>
     * <ol>
     *   <li>对象级授权 + 可见范围断言，缺一即可读他人课程试题；</li>
     *   <li>答案可见性按角色裁剪：学生只拿作答所需字段（去标准答案/解析）。</li>
     * </ol>
     * <p>性能：原实现逐题 {@code questionService.getById} 形成 N+1，本批改为一次 {@code listByIds}，
     * 但**保持原顺序**（按 {@code order_index} 的题目行顺序输出，题目缺失时跳过）。</p>
     */
    public List<ExamPaperQuestionVO> listQuestions(Long paperId, Authentication auth) {
        ExamPaper paper = requirePaper(paperId);
        examPaperAccessControl.assertCanRead(paper, auth);
        activityVisibilityGuard.assertVisibleExamPaper(paper, auth);

        List<ExamQuestion> examQuestionList = examQuestionService.list(
                new LambdaQueryWrapper<ExamQuestion>().eq(ExamQuestion::getExamPaperId, paperId));
        boolean answerVisible = RoleUtil.isTeacherOrAdmin(auth);

        List<Long> questionIds = examQuestionList.stream()
                .map(ExamQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Question> questionById = new HashMap<>();
        if (!questionIds.isEmpty()) {
            List<Question> questions = questionService.listByIds(questionIds);
            if (questions != null) {
                for (Question question : questions) {
                    if (question != null && question.getId() != null) {
                        questionById.put(question.getId(), question);
                    }
                }
            }
        }

        List<ExamPaperQuestionVO> resultList = new ArrayList<>(examQuestionList.size());
        for (ExamQuestion examQuestion : examQuestionList) {
            Question question = questionById.get(examQuestion.getQuestionId());
            if (question == null) {
                continue;
            }
            resultList.add(new ExamPaperQuestionVO(
                    examQuestion.getId(), examQuestion.getOrderIndex(), examQuestion.getPoints(),
                    answerVisible ? question : toAnswerHidden(question)));
        }
        return resultList;
    }

    /** 批量替换试卷题目（与 update/delete/publish 同口径的归属校验）。 */
    public List<ExamQuestion> replaceQuestions(Long paperId, ExamQuestionBatchDTO body, Authentication auth) {
        examPaperAccessControl.assertCanManage(requirePaper(paperId), auth);
        return examPaperService.replaceExamQuestions(paperId, body.getQuestions());
    }

    /**
     * 查询试卷可见范围与目标教学班（3E-03C 从 {@code ExamPaperTargetController} 迁入）。
     */
    public Map<String, Object> targets(Long paperId, Authentication auth) {
        ExamPaper paper = requirePaper(paperId);
        activityVisibilityGuard.assertVisibleExamPaper(paper, auth);
        return ActivityTargetsPayload.of(
                ActivityVisibility.normalize(paper.getVisibility()),
                activityTargetService.examPaperTargetClassIds(paperId));
    }

    /**
     * 设置试卷目标教学班（教师权限；管理员调用 403）。
     */
    public Map<String, Object> updateTargets(Long paperId, ActivityTargetDTO targets, Authentication auth) {
        activityVisibilityGuard.assertNotAdmin(auth);
        ExamPaper existing = requirePaper(paperId);
        examPaperAccessControl.assertCanManage(existing, auth);
        String visibility = activityTargetService.normalizeAndValidate(
                paperId, existing.getCourseId(), targets.getVisibility(), targets.getTeachingClassIds(), true);
        ExamPaper patch = new ExamPaper();
        patch.setId(paperId);
        patch.setVisibility(visibility);
        examPaperService.updateById(patch);
        activityTargetService.replaceExamPaperTargets(paperId, visibility, targets.getTeachingClassIds());
        return ActivityTargetsPayload.of(visibility, activityTargetService.examPaperTargetClassIds(paperId));
    }

    // ===================== 内部 =====================

    private ExamPaper requirePaper(Long id) {
        ExamPaper paper = examPaperService.getById(id);
        if (paper == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在：" + id);
        }
        return paper;
    }

    /** 回填 {@code courseName}（非持久化展示字段），去重后一次批量查询，避免 N+1。 */
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

    /** 作答可见的题目投影：白名单保留作答所需字段，剔除标准答案与解析。 */
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
}
