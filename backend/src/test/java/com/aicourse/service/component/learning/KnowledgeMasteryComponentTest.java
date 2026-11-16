package com.aicourse.service.component.learning;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.mapper.ExamQuestionMapper;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.MasteryVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 掌握度真实算法（P0-4）单元测试。
 *
 * <p>锁住四条业务不变式：①没有作答证据时返回“未评估”而不是 0/100；②题目必须按
 * 课程隔离，跨课程同名题不得混入；③考试得分优先于错题本状态，且取最近一次已阅卷
 * 考试；④部分得分按未完全答对计，但平均分仍按得分比例计算。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeMasteryComponent：真实掌握度证据")
class KnowledgeMasteryComponentTest {

    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamSessionMapper examSessionMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;

    private KnowledgeMasteryComponent component;

    @BeforeEach
    void setUp() {
        component = new KnowledgeMasteryComponent(questionService, wrongQuestionService,
                examPaperMapper, examSessionMapper, examQuestionMapper);
        lenient().when(questionService.list(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(List.of());
        lenient().when(examPaperMapper.selectList(any())).thenReturn(List.of());
        lenient().when(examSessionMapper.selectList(any())).thenReturn(List.of());
        lenient().when(examQuestionMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("无关联题目：score=null、answered=false，说明“暂无关联题目”")
    void noLinkedQuestions_returnsUnknownWithoutScore() {
        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertNull(result.getScore());
        assertFalse(result.isAnswered());
        assertEquals("未评估", result.getLevel());
        assertEquals(0, result.getTotalQuestions());
        assertEquals("该知识点暂无关联题目", result.getNote());
    }

    @Test
    @DisplayName("有题但无作答：score=null、answered=false，不把未作答当满分")
    void linkedQuestionWithoutEvidence_returnsUnknown() {
        stubQuestions(question(11L, 100L, "TCP"));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertNull(result.getScore());
        assertFalse(result.isAnswered());
        assertEquals(1, result.getTotalQuestions());
        assertEquals(0, result.getAnsweredQuestions());
        assertEquals("该知识点暂无作答记录", result.getNote());
    }

    @Test
    @DisplayName("错题未掌握：记 0 分，进入待巩固")
    void wrongQuestionNotMastered_returnsZero() {
        stubQuestions(question(11L, 100L, "TCP"));
        stubWrongBook(wrong(11L, false));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getScore().compareTo(BigDecimal.ZERO));
        assertEquals("待巩固", result.getLevel());
        assertTrue(result.isAnswered());
        assertEquals(1, result.getAnsweredQuestions());
        assertEquals(0, result.getCorrectQuestions());
        assertEquals(1, result.getWrongQuestions());
    }

    @Test
    @DisplayName("错题已掌握：记 100 分")
    void wrongQuestionMastered_returnsFullScore() {
        stubQuestions(question(11L, 100L, "TCP"));
        stubWrongBook(wrong(11L, true));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getScore().compareTo(new BigDecimal("100")));
        assertEquals("优秀", result.getLevel());
        assertEquals(1, result.getCorrectQuestions());
    }

    @Test
    @DisplayName("跨课程同名题排除：只统计本课程题目")
    void crossCourseSameNameQuestionExcluded() {
        stubQuestions(question(21L, 200L, "TCP"));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getTotalQuestions());
        assertNull(result.getScore());
        assertEquals("该知识点暂无关联题目", result.getNote());
    }

    @Test
    @DisplayName("最近一次已阅卷考试：后交卷的满分覆盖先交卷的低分")
    void latestGradedSessionWins() {
        stubQuestions(question(11L, 100L, "TCP"));
        stubExam(10L, 100L, 11L, new BigDecimal("5"));
        stubSessions(
                session(10L, LocalDateTime.of(2026, 11, 15, 10, 0), 1, "{\"11\":2}"),
                session(10L, LocalDateTime.of(2026, 11, 16, 10, 0), 2, "{\"11\":5}"));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getScore().compareTo(new BigDecimal("100")));
        assertEquals(1, result.getCorrectQuestions());
    }

    @Test
    @DisplayName("考试部分得分：按得分比例算平均，同时计为未完全答对")
    void partialExamScoreCountsAsWrongButKeepsRatio() {
        stubQuestions(question(11L, 100L, "TCP"));
        stubExam(10L, 100L, 11L, new BigDecimal("5"));
        stubSessions(session(10L, LocalDateTime.of(2026, 11, 16, 10, 0), 1, "{\"11\":3}"));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getScore().compareTo(new BigDecimal("60.00")));
        assertEquals("合格", result.getLevel());
        assertEquals(0, result.getCorrectQuestions());
        assertEquals(1, result.getWrongQuestions());
    }

    @Test
    @DisplayName("考试证据优先于错题本：错题未掌握但有满分考试记录时取 100")
    void examEvidenceTakesPriorityOverWrongBook() {
        stubQuestions(question(11L, 100L, "TCP"));
        stubWrongBook(wrong(11L, false));
        stubExam(10L, 100L, 11L, new BigDecimal("5"));
        stubSessions(session(10L, LocalDateTime.of(2026, 11, 16, 10, 0), 1, "{\"11\":5}"));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getScore().compareTo(new BigDecimal("100")));
        assertEquals(1, result.getCorrectQuestions());
    }

    @Test
    @DisplayName("批量计算：按知识点 ID 返回，未作答点仍保留未评估结果")
    void batchEvaluateKeepsUnknownPerPoint() {
        stubQuestions(question(11L, 100L, "TCP"), question(12L, 100L, "UDP"));
        stubWrongBook(wrong(11L, true));

        Map<Long, MasteryVO> result = component.evaluate(List.of(
                point(1L, 100L, "TCP"), point(2L, 100L, "UDP")), 2L);

        assertEquals(2, result.size());
        assertEquals(0, result.get(1L).getScore().compareTo(new BigDecimal("100")));
        assertFalse(result.get(2L).isAnswered());
        assertNull(result.get(2L).getScore());
    }

    private void stubQuestions(Question... questions) {
        lenient().when(questionService.list(any(Wrapper.class))).thenReturn(List.of(questions));
    }

    private void stubWrongBook(WrongQuestion... records) {
        lenient().when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(List.of(records));
    }

    private void stubExam(Long paperId, Long courseId, Long questionId, BigDecimal points) {
        ExamPaper paper = new ExamPaper();
        paper.setId(paperId);
        paper.setCourseId(courseId);
        lenient().when(examPaperMapper.selectList(any())).thenReturn(List.of(paper));

        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setExamPaperId(paperId);
        examQuestion.setQuestionId(questionId);
        examQuestion.setPoints(points);
        lenient().when(examQuestionMapper.selectList(any())).thenReturn(List.of(examQuestion));
    }

    private void stubSessions(ExamSession... sessions) {
        lenient().when(examSessionMapper.selectList(any())).thenReturn(List.of(sessions));
    }

    private static KnowledgePoint point(Long id, Long courseId, String name) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setCourseId(courseId);
        point.setName(name);
        return point;
    }

    private static Question question(Long id, Long courseId, String knowledgePoints) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(courseId);
        question.setKnowledgePoints("[\"" + knowledgePoints + "\"]");
        return question;
    }

    private static WrongQuestion wrong(Long questionId, boolean mastered) {
        WrongQuestion wrong = new WrongQuestion();
        wrong.setStudentId(2L);
        wrong.setQuestionId(questionId);
        wrong.setMastered(mastered);
        return wrong;
    }

    private static ExamSession session(Long paperId, LocalDateTime submitTime, int attempt,
                                       String questionScores) {
        ExamSession session = new ExamSession();
        session.setStudentId(2L);
        session.setExamPaperId(paperId);
        session.setStatus(ExamSessionStatus.GRADED);
        session.setSubmitTime(submitTime);
        session.setAttemptNumber(attempt);
        session.setQuestionScores(questionScores);
        return session;
    }
}
