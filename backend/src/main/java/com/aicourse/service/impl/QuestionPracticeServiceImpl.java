package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.Course;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.CourseService;
import com.aicourse.service.QuestionPracticeService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.component.student.StudentTaskCompletionComponent;
import com.aicourse.util.JsonColumnNormalizer;
import com.aicourse.vo.QuestionPracticeResultVO;
import com.aicourse.vo.QuestionPracticeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 学生题库练习服务实现（P0-A1）。
 */
@Service
public class QuestionPracticeServiceImpl implements QuestionPracticeService {

    private final QuestionService questionService;
    private final CourseService courseService;
    private final CourseAccessControl courseAccessControl;
    private final StudentTaskCompletionComponent taskCompletionComponent;

    public QuestionPracticeServiceImpl(QuestionService questionService,
                                       CourseService courseService,
                                       CourseAccessControl courseAccessControl,
                                       StudentTaskCompletionComponent taskCompletionComponent) {
        this.questionService = questionService;
        this.courseService = courseService;
        this.courseAccessControl = courseAccessControl;
        this.taskCompletionComponent = taskCompletionComponent;
    }

    @Override
    public Page<QuestionPracticeVO> listPracticeQuestions(Long courseId, String type, String difficulty,
                                                          long page, long size, Authentication auth) {
        assertPracticeAllowed(courseId, auth);
        long safePage = Math.max(1, page);
        long safeSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getCourseId, courseId)
                .eq(Question::getIsActive, true)
                .eq(type != null && !type.isBlank(), Question::getType, type)
                .eq(difficulty != null && !difficulty.isBlank(), Question::getDifficulty, difficulty)
                .orderByDesc(Question::getCreatedAt);
        Page<Question> questionPage = questionService.page(Page.of(safePage, safeSize), wrapper);
        Page<QuestionPracticeVO> result = Page.of(safePage, safeSize);
        if (questionPage == null) {
            result.setTotal(0);
            return result;
        }
        result.setTotal(questionPage.getTotal());
        result.setRecords(questionPage.getRecords().stream()
                .map(QuestionPracticeVO::from)
                .toList());
        return result;
    }

    @Override
    public QuestionPracticeResultVO checkAnswer(Long questionId, String answer, Authentication auth) {
        if (questionId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "题目ID不能为空");
        }
        Question question = questionService.getById(questionId);
        if (question == null || !Boolean.TRUE.equals(question.getIsActive())) {
            throw new BusinessException(ApiCode.NOT_FOUND, "题目不存在或已停用");
        }
        if (question.getCourseId() == null) {
            throw new BusinessException(ApiCode.FORBIDDEN, "该题目未归属课程，不能用于学生练习");
        }
        assertPracticeAllowed(question.getCourseId(), auth);
        String correctAnswer = JsonColumnNormalizer.toPlainAnswer(question.getCorrectAnswers());
        String studentAnswer = JsonColumnNormalizer.toPlainAnswer(answer);
        boolean correct = taskCompletionComponent.normalize(correctAnswer)
                .equals(taskCompletionComponent.normalize(studentAnswer));
        return new QuestionPracticeResultVO(question.getId(), correct, correctAnswer, question.getExplanation());
    }

    @Override
    public void assertPracticeAllowed(Long courseId, Authentication auth) {
        if (courseId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程ID不能为空");
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        // 先做成员关系/教师归属/管理员作用域校验，再判断教师开关，避免未选课学生探测课程设置。
        courseAccessControl.assertCanReadCourse(courseId, auth);
        if (courseAccessControl.isStudent(auth)
                && !Boolean.TRUE.equals(course.getQuestionPracticeEnabled())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "本课程教师尚未开放题库刷题");
        }
    }
}
