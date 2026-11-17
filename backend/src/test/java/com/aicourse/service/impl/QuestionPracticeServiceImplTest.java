package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.CourseService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.component.student.StudentTaskCompletionComponent;
import com.aicourse.vo.QuestionPracticeResultVO;
import com.aicourse.vo.QuestionPracticeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 学生题库练习服务单测（P0-A1）。
 * 重点：开关默认关闭、学生必须已选课、题目列表不泄露答案、提交后才返回答案。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionPracticeServiceImplTest {

    @Mock private QuestionService questionService;
    @Mock private CourseService courseService;
    @Mock private CourseAccessControl courseAccessControl;
    @Mock private StudentTaskCompletionComponent taskCompletionComponent;
    @Mock private Authentication auth;

    private QuestionPracticeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionPracticeServiceImpl(questionService, courseService, courseAccessControl, taskCompletionComponent);
        when(auth.getPrincipal()).thenReturn(2L);
        when(courseAccessControl.isStudent(auth)).thenReturn(true);
        when(taskCompletionComponent.normalize(anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class).trim().toUpperCase());
        Course course = new Course();
        course.setId(1L);
        course.setQuestionPracticeEnabled(false);
        when(courseService.getById(1L)).thenReturn(course);
    }

    private Question question() {
        Question q = new Question();
        q.setId(1L);
        q.setCourseId(1L);
        q.setType("SINGLE_CHOICE");
        q.setContent("题干");
        q.setOptions("[\"A\",\"B\"]");
        q.setCorrectAnswers("\"A\"");
        q.setExplanation("解析");
        q.setScore(new BigDecimal("5"));
        q.setDifficulty("EASY");
        q.setIsActive(true);
        return q;
    }

    @Test
    @DisplayName("教师未开放时学生刷题被 403 拒绝")
    void studentFlagOff_forbidden() {
        when(courseAccessControl.isStudent(auth)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.assertPracticeAllowed(1L, auth));
    }

    @Test
    @DisplayName("教师开放后学生可进入，题目列表不含答案与解析")
    void studentFlagOn_listHasNoAnswer() {
        Course course = new Course();
        course.setId(1L);
        course.setQuestionPracticeEnabled(true);
        when(courseService.getById(1L)).thenReturn(course);
        Page<Question> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(question()));
        when(questionService.page(any(), any())).thenReturn(page);

        Page<QuestionPracticeVO> result = service.listPracticeQuestions(1L, null, null, 1, 10, auth);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        // 投影只有题目字段，没有 answer / explanation；选项仍可用于作答
        assertEquals(List.of("A", "B"), result.getRecords().get(0).getOptions());
    }

    @Test
    @DisplayName("提交正确答案后返回 correct=true 与解析")
    void checkAnswer_correct() {
        Course course = new Course();
        course.setId(1L);
        course.setQuestionPracticeEnabled(true);
        when(courseService.getById(1L)).thenReturn(course);
        when(questionService.getById(1L)).thenReturn(question());

        QuestionPracticeResultVO result = service.checkAnswer(1L, "A", auth);
        assertTrue(result.isCorrect());
        assertEquals("A", result.getCorrectAnswer());
        assertEquals("解析", result.getExplanation());
    }

    @Test
    @DisplayName("提交错误答案后返回 correct=false，仍返回正确答案")
    void checkAnswer_wrong() {
        Course course = new Course();
        course.setId(1L);
        course.setQuestionPracticeEnabled(true);
        when(courseService.getById(1L)).thenReturn(course);
        when(questionService.getById(1L)).thenReturn(question());

        QuestionPracticeResultVO result = service.checkAnswer(1L, "B", auth);
        assertFalse(result.isCorrect());
        assertEquals("A", result.getCorrectAnswer());
    }
}
