package com.aicourse.service.component.learning;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * KnowledgeMasteryComponent 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖空入参、题库脏数据、错题本空记录、考试证据缺失/负分/分值缺失/超满分、
 * 分值 JSON 的非法与文本形态、最近考试比较器的全部空值分支、等级边界。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeMasteryComponentCoverageTest {

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
    @DisplayName("evaluate：null/空列表/null 学生/无课程/无 ID 知识点均返回空结果")
    void evaluate_emptyInputs() {
        assertFalse(component.evaluate((KnowledgePoint) null, 2L).isAnswered());
        assertTrue(component.evaluate(List.of(), 2L).isEmpty());
        assertTrue(component.evaluate((List<KnowledgePoint>) null, 2L).isEmpty());
        assertTrue(component.evaluate(List.of(point(1L, 100L, "TCP")), null).isEmpty());

        KnowledgePoint noCourse = point(2L, null, "TCP");
        assertTrue(component.evaluate(List.of(noCourse), 2L).isEmpty());

        KnowledgePoint noId = point(null, 100L, "TCP");
        assertTrue(component.evaluate(List.of(noId), 2L).isEmpty());
    }

    @Test
    @DisplayName("题库脏数据：null/无 ID/无课程/空知识点/重复 ID 均被跳过")
    void indexQuestions_skipsDirtyRows() {
        Question noId = question(null, 100L, "TCP");
        Question noCourse = question(12L, null, "TCP");
        Question blank = question(13L, 100L, "");
        Question normal = question(11L, 100L, "TCP");
        Question duplicate = question(11L, 100L, "TCP");
        when(questionService.list(any(Wrapper.class))).thenReturn(
                java.util.Arrays.asList(noId, noCourse, blank, normal, duplicate));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(1, result.getTotalQuestions());
        assertFalse(result.isAnswered());
        assertEquals("该知识点暂无作答记录", result.getNote());
    }

    @Test
    @DisplayName("错题本：null 记录与无题目 ID 记录被跳过，不产生作答证据")
    void wrongBook_skipsNullRecords() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(question(11L, 100L, "TCP")));
        WrongQuestion nullRecord = null;
        WrongQuestion noQuestion = new WrongQuestion();
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(
                java.util.Arrays.asList(nullRecord, noQuestion));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertFalse(result.isAnswered());
        assertEquals("该知识点暂无作答记录", result.getNote());
    }

    @Test
    @DisplayName("考试证据：脏试卷题与空会话被跳过，不产生作答证据")
    void examEvidence_skipsDirtyExamQuestionsAndSessions() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(question(11L, 100L, "TCP")));
        when(examPaperMapper.selectList(any())).thenReturn(List.of(paper(10L, 100L)));

        ExamQuestion noPaper = new ExamQuestion();
        noPaper.setQuestionId(11L);
        ExamQuestion noQuestion = new ExamQuestion();
        noQuestion.setExamPaperId(10L);
        ExamQuestion noPoints = new ExamQuestion();
        noPoints.setExamPaperId(10L);
        noPoints.setQuestionId(11L);
        when(examQuestionMapper.selectList(any())).thenReturn(
                java.util.Arrays.asList(null, noPaper, noQuestion, noPoints));

        ExamSession nullPaper = session(null, LocalDateTime.of(2026, 11, 16, 10, 0), 1, null);
        ExamSession emptyScores = session(10L, LocalDateTime.of(2026, 11, 15, 10, 0), 1, "");
        when(examSessionMapper.selectList(any())).thenReturn(
                java.util.Arrays.asList(null, nullPaper, emptyScores));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertFalse(result.isAnswered());
        assertEquals("该知识点暂无作答记录", result.getNote());
    }

    @Test
    @DisplayName("数据源返回 null：列表、错题、试卷、试卷题、会话均按空集合处理")
    void safeList_nullSources() {
        when(questionService.list(any(Wrapper.class))).thenReturn(null);
        when(wrongQuestionService.list(any(Wrapper.class))).thenReturn(null);
        when(examPaperMapper.selectList(any())).thenReturn(null);
        when(examQuestionMapper.selectList(any())).thenReturn(null);
        when(examSessionMapper.selectList(any())).thenReturn(null);

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertFalse(result.isAnswered());
        assertEquals("该知识点暂无关联题目", result.getNote());
    }

    @Test
    @DisplayName("考试证据：无试卷 ID、脏试卷题、空会话、无分数 JSON 均不产生证据")
    void examEvidence_skipsMissingRows() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(question(11L, 100L, "TCP")));
        ExamPaper noId = new ExamPaper();
        when(examPaperMapper.selectList(any())).thenReturn(List.of(noId));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertFalse(result.isAnswered());
        assertEquals("该知识点暂无作答记录", result.getNote());
    }

    @Test
    @DisplayName("考试证据：试卷题缺分值/分值为零、负分、超满分按规则跳过或截断")
    void examEvidence_scoreRules() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(question(11L, 100L, "TCP"), question(12L, 100L, "TCP")));
        ExamPaper paper = paper(10L, 100L);
        when(examPaperMapper.selectList(any())).thenReturn(List.of(paper));

        ExamQuestion noPoints = new ExamQuestion();
        noPoints.setExamPaperId(10L);
        noPoints.setQuestionId(11L);
        ExamQuestion zeroPoints = new ExamQuestion();
        zeroPoints.setExamPaperId(10L);
        zeroPoints.setQuestionId(12L);
        zeroPoints.setPoints(BigDecimal.ZERO);
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(noPoints, zeroPoints));

        ExamSession negative = session(10L, LocalDateTime.of(2026, 11, 16, 10, 0),
                1, "{\"11\":-1}");
        ExamSession overFull = session(10L, LocalDateTime.of(2026, 11, 15, 10, 0),
                1, "{\"12\":10}");
        when(examSessionMapper.selectList(any())).thenReturn(List.of(negative, overFull));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertFalse(result.isAnswered(), "无分值/负分不产生证据，零分题也不应被当成证据");
    }

    @Test
    @DisplayName("考试证据：同题多次考试只保留最近一次，超满分截断为 100")
    void examEvidence_latestAndClamp() {
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(question(11L, 100L, "TCP")));
        when(examPaperMapper.selectList(any())).thenReturn(List.of(paper(10L, 100L)));
        ExamQuestion eq = new ExamQuestion();
        eq.setExamPaperId(10L);
        eq.setQuestionId(11L);
        eq.setPoints(new BigDecimal("5"));
        when(examQuestionMapper.selectList(any())).thenReturn(List.of(eq));

        ExamSession older = session(10L, LocalDateTime.of(2026, 11, 15, 10, 0),
                1, "{\"11\":2}");
        ExamSession newer = session(10L, LocalDateTime.of(2026, 11, 16, 10, 0),
                1, "{\"11\":10}");
        when(examSessionMapper.selectList(any())).thenReturn(List.of(older, newer));

        MasteryVO result = component.evaluate(point(1L, 100L, "TCP"), 2L);

        assertEquals(0, result.getScore().compareTo(new BigDecimal("100")));
        assertEquals(1, result.getCorrectQuestions());
    }

    @Test
    @DisplayName("分值 JSON：null/空白/非法/非对象/文本数字/脏键均安全处理")
    @SuppressWarnings("unchecked")
    void parseQuestionScores_edges() {
        assertEquals(0, invokeParse(null).size());
        assertEquals(0, invokeParse("   ").size());
        assertEquals(0, invokeParse("not-json").size());
        assertEquals(0, invokeParse("[]").size());
        assertEquals(0, invokeParse("null").size());

        Map<Long, BigDecimal> numeric = invokeParse("{\"11\":5}");
        assertEquals(new BigDecimal("5"), numeric.get(11L));

        Map<Long, BigDecimal> textual = invokeParse("{\"11\":\"5\",\"bad\":1,\"12\":null}");
        assertEquals(new BigDecimal("5"), textual.get(11L));
        assertFalse(textual.containsKey(12L), "null 分值不应进入结果");
        assertEquals(1, textual.size());
    }

    @Test
    @DisplayName("最近考试比较器：时间优先，时间相同看次数，缺失值时缺失者靠后")
    void latestSessionFirst_allBranches() {
        Comparator<ExamSession> comparator = ReflectionTestUtils.invokeMethod(component, "latestSessionFirst");
        assertNotNull(comparator);

        LocalDateTime t1 = LocalDateTime.of(2026, 11, 15, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 16, 10, 0);

        assertTrue(comparator.compare(session(10L, t2, 1, null), session(10L, t1, 1, null)) < 0);
        assertTrue(comparator.compare(session(10L, t1, 2, null), session(10L, t1, 1, null)) < 0);
        assertTrue(comparator.compare(session(10L, t1, null, null), session(10L, null, 1, null)) < 0);
        assertTrue(comparator.compare(session(10L, null, 1, null), session(10L, t1, null, null)) > 0);
        assertTrue(comparator.compare(session(10L, null, 2, null), session(10L, null, 1, null)) < 0);
        assertTrue(comparator.compare(session(10L, null, 1, null), session(10L, null, null, null)) < 0);
        assertTrue(comparator.compare(session(10L, null, null, null), session(10L, null, 1, null)) > 0);
        assertEquals(0, comparator.compare(session(10L, null, null, null), session(10L, null, null, null)));
    }

    @Test
    @DisplayName("等级边界：85/70/60 与以下分别对应优秀/良好/合格/待巩固")
    void levelBoundaries() {
        assertEquals("优秀", invokeLevel(new BigDecimal("85")));
        assertEquals("良好", invokeLevel(new BigDecimal("84.99")));
        assertEquals("良好", invokeLevel(new BigDecimal("70")));
        assertEquals("合格", invokeLevel(new BigDecimal("69.99")));
        assertEquals("合格", invokeLevel(new BigDecimal("60")));
        assertEquals("待巩固", invokeLevel(new BigDecimal("59.99")));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, BigDecimal> invokeParse(String raw) {
        return ReflectionTestUtils.invokeMethod(component, "parseQuestionScores", raw);
    }

    private String invokeLevel(BigDecimal score) {
        return ReflectionTestUtils.invokeMethod(component, "levelOf", score);
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
        question.setKnowledgePoints(knowledgePoints);
        return question;
    }

    private static ExamPaper paper(Long id, Long courseId) {
        ExamPaper paper = new ExamPaper();
        paper.setId(id);
        paper.setCourseId(courseId);
        return paper;
    }

    private static ExamSession session(Long paperId, LocalDateTime submitTime, Integer attempt,
                                       String scores) {
        ExamSession session = new ExamSession();
        session.setStudentId(2L);
        session.setExamPaperId(paperId);
        session.setSubmitTime(submitTime);
        session.setAttemptNumber(attempt);
        session.setQuestionScores(scores);
        return session;
    }
}
