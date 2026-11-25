package com.aicourse.application.question;

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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 题库读面应用服务（3E-03C）。
 *
 * <p>把 {@code QuestionController} 里的查询条件拼装、课程可见范围过滤、统计口径分支
 * 收拢到一处。三个历史口子在这里成为单一事实源：</p>
 *
 * <ul>
 *   <li><b>P0-3（2026-09-23）</b>：题库读接口限教师/管理员——学生端零依赖
 *       （随堂测走 {@code /questions/random}、考试取题走 {@code /exam-papers/{id}/questions}、
 *       错题本走 {@code /wrong-questions}），故除 {@code /random} 外的端点由
 *       {@code @PreAuthorize} 拦在 Web 层。</li>
 *   <li><b>U2（2026-11-14）</b>：{@code /random} 虽是学生可达的取题口，仍须
 *       {@code assertPracticeAllowed}——题干属课程内容，未选课学生不得跨课程捞题，
 *       且要求本课程教师已开放学生刷题。</li>
 *   <li><b>courseId 为空的历史题</b>：不传 {@code courseId} 时一并返回，按课程筛选时不返回。</li>
 * </ul>
 *
 * <p>改造前每个端点各写一遍 {@code privileged ? 实体 : QuestionVO} 分支。除 {@code /random}
 * 外这些端点在 {@code @PreAuthorize} 下只可能被教师/管理员调用，学生分支不可达；本批
 * 把这些死分支删掉，仅在 {@code /random} 保留学生脱敏。</p>
 */
@Service
public class QuestionBankApplicationService {

    private final QuestionService questionService;
    private final CourseAccessControl courseAccessControl;
    private final QuestionPracticeService questionPracticeService;
    private final QuestionAccessSupport questionAccessSupport;

    public QuestionBankApplicationService(QuestionService questionService,
                                          CourseAccessControl courseAccessControl,
                                          QuestionPracticeService questionPracticeService,
                                          QuestionAccessSupport questionAccessSupport) {
        this.questionService = questionService;
        this.courseAccessControl = courseAccessControl;
        this.questionPracticeService = questionPracticeService;
        this.questionAccessSupport = questionAccessSupport;
    }

    /**
     * 分页查询题目列表（口径见类注释：courseId 为空的历史题在不筛选时保留）。
     */
    public PagedResponse<Question> pageQuestions(long page, long size, Long courseId, String type,
                                                 String difficulty, String keyword, Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .like(keyword != null && !keyword.isBlank(), Question::getContent,
                        keyword == null ? null : keyword.trim())
                .in(readableCourseIds != null, Question::getCourseId, readableCourseIds)
                .orderByDesc(Question::getCreatedAt);
        // 教师名下无任何可读课程时直接给空页，不查库（与改造前一致）
        Page<Question> questionPage = readableCourseIds != null && readableCourseIds.isEmpty()
                ? new Page<>(page, size, 0)
                : questionService.page(Page.of(page, size), queryWrapper);
        if (questionPage == null) {
            questionPage = Page.of(page, size);
        }
        return PagedResponse.of(questionPage);
    }

    /** 题目详情（含课程可见范围校验）。 */
    public Question questionDetail(Long id, Authentication auth) {
        Question question = questionAccessSupport.requireQuestion(id);
        if (question.getCourseId() != null) {
            courseAccessControl.assertCanReadCourse(question.getCourseId(), auth);
        }
        return question;
    }

    /** 智能搜索（全文检索 + 筛选），可读课程集合非空时下推给服务层。 */
    public PagedResponse<Question> searchQuestions(Long courseId, String type, String difficulty, String keyword,
                                                   int page, int size, Authentication auth) {
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
        return PagedResponse.of(result);
    }

    /**
     * 随机取题（随堂测 / 组卷）。
     *
     * <p>本端点是唯一学生可达的题库读口，故保留两档：教师/管理员取实体，
     * 学生取脱敏 VO（不可见答案与解析）。</p>
     */
    public List<?> randomSelect(Long courseId, String type, String difficulty, int count, Authentication auth) {
        questionPracticeService.assertPracticeAllowed(courseId, auth);
        boolean privileged = RoleUtil.isTeacherOrAdmin(auth);
        List<Question> questions = questionService.randomSelect(courseId, type, difficulty, count);
        if (questions == null) {
            return Collections.emptyList();
        }
        return privileged ? questions : questions.stream().map(QuestionVO::from).toList();
    }

    /** 按知识点查题：课程筛选与可读课程过滤在内存里做（顺序与改造前一致）。 */
    public List<Question> questionsByKnowledgePoint(Long kpId, Long courseId, Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        List<Question> questions = questionService.getByKnowledgePoint(kpId);
        if (questions == null) {
            return Collections.emptyList();
        }
        final Long filterCourseId = courseId;
        final List<Long> readable = readableCourseIds;
        return questions.stream()
                .filter(q -> filterCourseId == null || filterCourseId.equals(q.getCourseId()))
                .filter(q -> readable == null || (q.getCourseId() != null && readable.contains(q.getCourseId())))
                .toList();
    }

    /** 题库总览统计（可读课程集合非空时按课程统计）。 */
    public QuestionBankStatisticsVO bankStatistics(Authentication auth) {
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        return readableCourseIds != null
                ? questionService.getBankStatistics(readableCourseIds)
                : questionService.getBankStatistics();
    }

    /** 题目使用统计。 */
    public QuestionStatsVO questionStats(Long id, Authentication auth) {
        Question question = questionAccessSupport.requireQuestion(id);
        if (question.getCourseId() != null) {
            courseAccessControl.assertCanReadCourse(question.getCourseId(), auth);
        }
        return questionService.getQuestionStats(id);
    }

    /** 易错题 TOP N。 */
    public List<Question> errorProne(Long courseId, int limit, Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        List<Question> questions = readableCourseIds != null
                ? questionService.getErrorProneQuestions(courseId, limit, readableCourseIds)
                : questionService.getErrorProneQuestions(courseId, limit);
        return questions == null ? Collections.emptyList() : questions;
    }
}
