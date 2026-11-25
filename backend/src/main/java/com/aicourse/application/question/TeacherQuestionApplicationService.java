package com.aicourse.application.question;

import com.aicourse.common.PagedResponse;
import com.aicourse.dto.QuestionSaveDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.Question;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.CourseService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.TeacherQuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 教师端题库写面应用服务（3E-03C）。
 *
 * <p>把 {@code TeacherQuestionController} 里的归属校验、教师可见范围拼装、
 * 「更新忽略客户端 courseId」与「createdBy 取登录身份」两条口径收拢到一处。</p>
 *
 * <p>导出仍由控制器写响应流（Excel + servlet 输出属 Web 关注点），本服务只负责
 * 出「该导哪些题」——含课程归属校验。</p>
 */
@Service
public class TeacherQuestionApplicationService {

    private final QuestionService questionService;
    private final CourseService courseService;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherQuestionApplicationService(QuestionService questionService,
                                             CourseService courseService,
                                             TeacherAccessGuard teacherAccessGuard) {
        this.questionService = questionService;
        this.courseService = courseService;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** 创建题目：课程归属校验 → createdBy 取登录身份 → 落库。 */
    public TeacherQuestionVO createQuestion(QuestionSaveDTO dto, Authentication auth) {
        Question question = dto.toEntity();
        if (question.getCourseId() != null) {
            teacherAccessGuard.assertCourseOwner(question.getCourseId(), auth);
        }
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.createQuestion(question);
        return TeacherQuestionVO.from(question);
    }

    /**
     * 教师题目列表。
     *
     * <p>无 {@code courseId} 且非管理员时，范围收敛为「本人课程 ∪ 本人创建的历史题」
     * ——历史题可能落在已不属于本人的课程里，只看课程会漏。</p>
     */
    public PagedResponse<TeacherQuestionVO> pageQuestions(long page, long size, Long courseId, String type,
                                                          String difficulty, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (courseId != null) {
            teacherAccessGuard.assertCourseOwner(courseId, auth);
        }
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty);
        if (courseId == null && !teacherAccessGuard.isAdmin(auth)) {
            List<Long> ownCourseIds = courseService.list(
                            new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId))
                    .stream().map(Course::getId).toList();
            qw.and(w -> {
                if (!ownCourseIds.isEmpty()) {
                    w.in(Question::getCourseId, ownCourseIds).or();
                }
                w.eq(Question::getCreatedBy, userId);
            });
        }
        Page<Question> questionPage = questionService.page(Page.of(page, size), qw);
        return PagedResponse.of(questionPage.convert(TeacherQuestionVO::from));
    }

    /** 题目详情（教师侧）。 */
    public TeacherQuestionVO questionDetail(Long questionId, Authentication auth) {
        teacherAccessGuard.assertQuestionOwner(questionId, auth);
        return TeacherQuestionVO.from(questionService.getById(questionId));
    }

    /**
     * 更新题目。
     *
     * <p>口径（改造前既有约定，原样保留）：{@code courseId} 忽略请求值，始终沿用原题目课程
     * ——否则一次更新就能把题「搬」到别人的课程里。</p>
     */
    public TeacherQuestionVO updateQuestion(Long questionId, QuestionSaveDTO dto, Authentication auth) {
        teacherAccessGuard.assertQuestionOwner(questionId, auth);
        Question existing = questionService.getById(questionId);
        Question question = dto.toEntity();
        question.setId(questionId);
        if (existing != null) {
            question.setCourseId(existing.getCourseId());
        }
        questionService.updateById(question);
        return TeacherQuestionVO.from(questionService.getById(questionId));
    }

    /** 删除题目。 */
    public void deleteQuestion(Long questionId, Authentication auth) {
        teacherAccessGuard.assertQuestionOwner(questionId, auth);
        questionService.removeById(questionId);
    }

    /** 导出所需题目集合（课程可选；指定课程时须本人课程）。 */
    public List<Question> exportQuestions(Long courseId, Authentication auth) {
        if (courseId != null) {
            teacherAccessGuard.assertCourseOwner(courseId, auth);
        }
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId);
        return questionService.list(qw);
    }
}
