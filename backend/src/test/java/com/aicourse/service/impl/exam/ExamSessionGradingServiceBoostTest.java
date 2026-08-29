package com.aicourse.service.impl.exam;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 ExamSessionGradingService 的自动阅卷（各题型）、人工阅卷、
 * 总分计算及私有解析/辅助方法分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSessionGradingServiceBoostTest {

    @Mock
    private ExamQuestionService examQuestionService;
    @Mock
    private QuestionService questionService;
    @Mock
    private WrongQuestionService wrongQuestionService;
    @Mock
    private ExamPaperService examPaperService;
    @Mock
    private ExamSessionMapper examSessionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExamSessionGradingService service;

    @BeforeEach
    void setUp() {
        service = new ExamSessionGradingService(examQuestionService, questionService,
                wrongQuestionService, examPaperService, objectMapper, examSessionMapper);
    }

    private ExamSession submittedSession(Long id, String answers) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        s.setAnswers(answers);
        return s;
    }

    @Test
    @DisplayName("autoGrade 会话不存在抛异常")
    void autoGrade_null() {
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.autoGrade(1L));
    }

    @Test
    @DisplayName("autoGrade 状态未提交抛异常")
    void autoGrade_statusInvalid() {
        ExamSession s = submittedSession(1L, "{\"100\":\"A\"}");
        s.setStatus(ExamSessionStatus.STARTED);
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.autoGrade(1L));
    }

    @Test
    @DisplayName("autoGrade 答案记录为空抛异常")
    void autoGrade_emptyAnswers() {
        ExamSession s = submittedSession(1L, "{}");
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.autoGrade(1L));
    }

    private void stubQuestion(Long qid, String type, String correct, String points) {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(qid);
        eq.setPoints(new BigDecimal(points));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        Question q = new Question();
        q.setId(qid);
        q.setType(type);
        q.setCorrectAnswers(correct);
        when(questionService.listByIds(any())).thenReturn(List.of(q));
    }

    private Map<String, Object> runAutoGrade(Long id, String answers) {
        ExamSession s = submittedSession(id, answers);
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        return service.autoGrade(id);
    }

    @Test
    @DisplayName("autoGrade 单选题答对")
    void autoGrade_singleCorrect() {
        stubQuestion(100L, "SINGLE_CHOICE", "A", "5");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"A\"}");
        assertEquals(1, r.get("correctCount"));
        assertEquals(0, r.get("wrongCount"));
    }

    @Test
    @DisplayName("autoGrade 单选题答错 -> 记录错题")
    void autoGrade_singleWrong() {
        stubQuestion(100L, "SINGLE_CHOICE", "A", "5");
        ExamPaper paper = new ExamPaper();
        paper.setCourseId(5L);
        when(examPaperService.getById(1L)).thenReturn(paper);
        when(wrongQuestionService.count(any())).thenReturn(0L);
        when(wrongQuestionService.save(any())).thenReturn(true);
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"B\"}");
        assertEquals(1, r.get("wrongCount"));
    }

    @Test
    @DisplayName("autoGrade 多选题完全匹配")
    void autoGrade_multipleFull() {
        stubQuestion(100L, "MULTIPLE_CHOICE", "[\"A\",\"B\"]", "10");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"[\\\"A\\\",\\\"B\\\"]\"}");
        assertEquals(0, r.get("wrongCount"));
        assertEquals(0, r.get("pendingReviewCount"));
    }

    @Test
    @DisplayName("autoGrade 多选题部分匹配")
    void autoGrade_multiplePartial() {
        stubQuestion(100L, "MULTIPLE_CHOICE", "[\"A\",\"B\"]", "10");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"[\\\"A\\\"]\"}");
        assertEquals(1, r.get("wrongCount"));
    }

    @Test
    @DisplayName("autoGrade 多选题无标准答案不评分")
    void autoGrade_multipleNoCorrect() {
        stubQuestion(100L, "MULTIPLE_CHOICE", null, "10");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"[\\\"A\\\"]\"}");
        assertEquals(1, r.get("wrongCount"));
    }

    @Test
    @DisplayName("autoGrade 判断题")
    void autoGrade_trueFalse() {
        stubQuestion(100L, "TRUE_FALSE", "true", "5");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"TRUE\"}");
        assertEquals(1, r.get("correctCount"));
    }

    @Test
    @DisplayName("autoGrade 主观题待人工批阅")
    void autoGrade_shortAnswer() {
        stubQuestion(100L, "SHORT_ANSWER", "x", "5");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"abc\"}");
        assertEquals(1, r.get("pendingReviewCount"));
    }

    @Test
    @DisplayName("autoGrade 未知题型待人工批阅")
    void autoGrade_defaultType() {
        stubQuestion(100L, "UNKNOWN_TYPE", "x", "5");
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"abc\"}");
        assertEquals(1, r.get("pendingReviewCount"));
    }

    @Test
    @DisplayName("autoGrade 题目为空跳过")
    void autoGrade_questionNull() {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(999L);
        eq.setPoints(new BigDecimal("5"));
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of());
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"A\"}");
        assertEquals(0, r.get("correctCount"));
    }

    @Test
    @DisplayName("autoGrade 题目分数为 null 按 0 计")
    void autoGrade_pointsNull() {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(100L);
        eq.setPoints(null);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        Question q = new Question();
        q.setId(100L);
        q.setType("SINGLE_CHOICE");
        q.setCorrectAnswers("A");
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        Map<String, Object> r = runAutoGrade(1L, "{\"100\":\"A\"}");
        assertEquals(1, r.get("correctCount"));
    }

    // ===== manualGrade =====

    @Test
    @DisplayName("manualGrade 会话不存在抛异常")
    void manual_null() {
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.manualGrade(1L, 2L, BigDecimal.TEN, "good"));
    }

    @Test
    @DisplayName("manualGrade 状态非 GRADED 抛异常")
    void manual_statusInvalid() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        assertThrows(BusinessException.class,
                () -> service.manualGrade(1L, 2L, BigDecimal.TEN, "good"));
    }

    @Test
    @DisplayName("manualGrade 成功重新计算")
    void manual_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStatus(ExamSessionStatus.GRADED);
        s.setQuestionScores("{\"2\":3}");
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        ExamPaper paper = new ExamPaper();
        paper.setTotalScore(new BigDecimal("10"));
        when(examPaperService.getById(1L)).thenReturn(paper);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        service.manualGrade(1L, 2L, new BigDecimal("7"), "good");
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    @Test
    @DisplayName("manualGrade 无试卷不计算百分比")
    void manual_noPaper() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStatus(ExamSessionStatus.GRADED);
        s.setQuestionScores("{\"2\":3}");
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        when(examPaperService.getById(1L)).thenReturn(null);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        service.manualGrade(1L, 2L, new BigDecimal("7"), "good");
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    // ===== calculateTotalScore =====

    @Test
    @DisplayName("calculateTotalScore 会话不存在抛异常")
    void total_null() {
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.calculateTotalScore(1L));
    }

    @Test
    @DisplayName("calculateTotalScore 得分为空返回 0")
    void total_empty() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setQuestionScores("");
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        assertEquals(0, BigDecimal.ZERO.compareTo(service.calculateTotalScore(1L)));
    }

    @Test
    @DisplayName("calculateTotalScore 正常求和")
    void total_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setQuestionScores("{\"1\":5,\"2\":3}");
        when(examSessionMapper.selectById(any(Serializable.class))).thenReturn(s);
        assertEquals(0, new BigDecimal("8").compareTo(service.calculateTotalScore(1L)));
    }

    // ===== 私有方法（反射覆盖） =====

    @Test
    @DisplayName("parseJsonToMap 空/无效返回空")
    void parseJsonToMap_edge() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("parseJsonToMap", String.class);
        m.setAccessible(true);
        assertTrue(((Map<?, ?>) m.invoke(service, (String) null)).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(service, "")).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(service, "{}")).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(service, "notjson")).isEmpty());
    }

    @Test
    @DisplayName("parseJsonToMap 合法解析")
    void parseJsonToMap_ok() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("parseJsonToMap", String.class);
        m.setAccessible(true);
        Map<?, ?> r = (Map<?, ?>) m.invoke(service, "{\"a\":\"b\"}");
        assertFalse(r.isEmpty());
    }

    @Test
    @DisplayName("parseArrayToStringSet 空/无效")
    void parseArray_edge() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("parseArrayToStringSet", String.class);
        m.setAccessible(true);
        assertTrue(((Set<?>) m.invoke(service, (String) null)).isEmpty());
        assertTrue(((Set<?>) m.invoke(service, "")).isEmpty());
        assertTrue(((Set<?>) m.invoke(service, "xx")).contains("xx"));
    }

    @Test
    @DisplayName("parseArrayToStringSet 合法解析")
    void parseArray_ok() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("parseArrayToStringSet", String.class);
        m.setAccessible(true);
        Set<?> r = (Set<?>) m.invoke(service, "[\"A\",\"B\"]");
        assertEquals(2, r.size());
    }

    @Test
    @DisplayName("scoresMapToJson 空/null 返回 {} ")
    void scoresMap_edge() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("scoresMapToJson", Map.class);
        m.setAccessible(true);
        assertEquals("{}", m.invoke(service, (Map<?, ?>) null));
        assertEquals("{}", m.invoke(service, new HashMap<Long, BigDecimal>()));
    }

    @Test
    @DisplayName("scoresMapToJson 合法序列化")
    void scoresMap_ok() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("scoresMapToJson", Map.class);
        m.setAccessible(true);
        Map<Long, BigDecimal> map = new HashMap<>();
        map.put(1L, new BigDecimal("5"));
        String r = (String) m.invoke(service, map);
        assertTrue(r.contains("5"));
    }

    @Test
    @DisplayName("parseScoresJson 各边界")
    void parseScores_edge() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod("parseScoresJson", String.class);
        m.setAccessible(true);
        assertTrue(((Map<?, ?>) m.invoke(service, (String) null)).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(service, "{}")).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(service, "bad")).isEmpty());
        Map<?, ?> r = (Map<?, ?>) m.invoke(service, "{\"1\":5}");
        assertFalse(r.isEmpty());
    }

    @Test
    @DisplayName("calculateTotalScoreFromScores 排除 -1")
    void totalFromScores() throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod(
                "calculateTotalScoreFromScores", Map.class);
        m.setAccessible(true);
        Map<Long, BigDecimal> map = new HashMap<>();
        map.put(1L, new BigDecimal("5"));
        map.put(2L, new BigDecimal("-1"));
        map.put(3L, null);
        BigDecimal r = (BigDecimal) m.invoke(service, map);
        assertEquals(0, new BigDecimal("5").compareTo(r));
    }

    @Test
    @DisplayName("saveWrongQuestion count==0 新建")
    void saveWrong_new() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setCourseId(5L);
        when(examPaperService.getById(1L)).thenReturn(paper);
        when(wrongQuestionService.count(any())).thenReturn(0L);
        when(wrongQuestionService.save(any())).thenReturn(true);
        invokeSaveWrong(2L, 100L, 1L, "A", "EXAM");
        verify(wrongQuestionService).save(any());
    }

    @Test
    @DisplayName("saveWrongQuestion count>0 更新")
    void saveWrong_update() throws Exception {
        ExamPaper paper = new ExamPaper();
        paper.setCourseId(5L);
        when(examPaperService.getById(1L)).thenReturn(paper);
        when(wrongQuestionService.count(any())).thenReturn(1L);
        WrongQuestion existing = new WrongQuestion();
        existing.setId(7L);
        existing.setPracticeCount(3);
        when(wrongQuestionService.getOne(any())).thenReturn(existing);
        when(wrongQuestionService.updateById(any())).thenReturn(true);
        invokeSaveWrong(2L, 100L, 1L, "A", "EXAM");
        verify(wrongQuestionService).updateById(any());
    }

    @Test
    @DisplayName("saveWrongQuestion 试卷为 null 时 courseId 为 null")
    void saveWrong_paperNull() throws Exception {
        when(examPaperService.getById(1L)).thenReturn(null);
        when(wrongQuestionService.count(any())).thenReturn(0L);
        when(wrongQuestionService.save(any())).thenReturn(true);
        invokeSaveWrong(2L, 100L, 1L, "A", "EXAM");
        verify(wrongQuestionService).save(any());
    }

    @Test
    @DisplayName("saveWrongQuestion 异常被吞掉")
    void saveWrong_exception() throws Exception {
        when(examPaperService.getById(1L)).thenThrow(new RuntimeException("boom"));
        invokeSaveWrong(2L, 100L, 1L, "A", "EXAM");
    }

    private void invokeSaveWrong(Long sid, Long qid, Long paperId, String wa, String src) throws Exception {
        Method m = ExamSessionGradingService.class.getDeclaredMethod(
                "saveWrongQuestion", Long.class, Long.class, Long.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, sid, qid, paperId, wa, src);
    }
}
