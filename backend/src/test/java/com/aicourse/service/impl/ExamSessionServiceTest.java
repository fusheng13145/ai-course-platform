package com.aicourse.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话服务单元测试
 * 测试 ExamSessionServiceImpl 的考试开始、答案提交和自动评分核心流程。
 * <p>实现为直算模式：直接使用 ExamSessionMapper（baseMapper）与 ExamPaperService /
 * ExamQuestionService / QuestionService / WrongQuestionService，不依赖门面协作者。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceTest {

    @Mock private ExamPaperService examPaperService;
    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ExamSessionMapper examSessionMapper;

    @InjectMocks private ExamSessionServiceImpl examSessionService;

    private ExamSession sampleSession;
    private ExamPaper publishedPaper;
    private Long examPaperId;
    private Long studentId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        // 显式注入 baseMapper，使 MyBatis-Plus ServiceImpl 的内部方法可用
        ReflectionTestUtils.setField(examSessionService, "baseMapper", examSessionMapper);
        // 注册 ExamSession 的 lambda 元数据，使 LambdaUpdateWrapper 在无 Spring 上下文时可用
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamSession.class);

        examPaperId = 100L;
        studentId = 200L;
        sessionId = 1L;

        publishedPaper = new ExamPaper();
        publishedPaper.setId(examPaperId);
        publishedPaper.setTitle("期末测试");
        publishedPaper.setStatus("PUBLISHED");
        publishedPaper.setStartTime(LocalDateTime.now().minusHours(1));
        publishedPaper.setEndTime(LocalDateTime.now().plusHours(1));
        publishedPaper.setTotalScore(new BigDecimal("100"));
        publishedPaper.setMaxAttempts(3);
        publishedPaper.setDuration(120);

        sampleSession = new ExamSession();
        sampleSession.setId(sessionId);
        sampleSession.setExamPaperId(examPaperId);
        sampleSession.setStudentId(studentId);
        sampleSession.setStatus("STARTED");
        sampleSession.setAttemptNumber(1);
        sampleSession.setStartTime(LocalDateTime.now());
        sampleSession.setIpAddress("192.168.1.1");
        sampleSession.setUserAgent("Mozilla/5.0 Test");
    }

    // ===================== 考试开始测试 =====================

    @Test
    @DisplayName("开始考试-成功创建会话")
    void testStartExam() {
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0 Test";

        when(examPaperService.getById(examPaperId)).thenReturn(publishedPaper);
        when(examSessionMapper.selectCount(any())).thenReturn(0L, 0L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(examSessionMapper.insert(any(ExamSession.class))).thenReturn(1);

        ExamSession result = examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);

        assertNotNull(result);
        assertEquals(examPaperId, result.getExamPaperId());
        assertEquals(studentId, result.getStudentId());
        assertEquals("STARTED", result.getStatus());
        assertEquals(1, result.getAttemptNumber());
        assertEquals(ipAddress, result.getIpAddress());
        assertEquals(userAgent, result.getUserAgent());
        assertNotNull(result.getStartTime());

        verify(examSessionMapper).insert(any(ExamSession.class));
    }

    @Test
    @DisplayName("开始考试-委托验证参数正确传递")
    void testStartExam_ParameterPropagation() {
        String ipAddress = "10.0.0.1";
        String userAgent = "Chrome/120.0";

        when(examPaperService.getById(examPaperId)).thenReturn(publishedPaper);
        when(examSessionMapper.selectCount(any())).thenReturn(0L, 0L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(examSessionMapper.insert(any(ExamSession.class))).thenReturn(1);

        examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);

        // 捕获插入的会话实体，验证入参被完整透传到持久化对象
        ArgumentCaptor<ExamSession> captor = ArgumentCaptor.forClass(ExamSession.class);
        verify(examSessionMapper).insert(captor.capture());
        ExamSession saved = captor.getValue();
        assertEquals(examPaperId, saved.getExamPaperId());
        assertEquals(studentId, saved.getStudentId());
        assertEquals(ipAddress, saved.getIpAddress());
        assertEquals(userAgent, saved.getUserAgent());
        assertEquals("STARTED", saved.getStatus());
    }

    // ===================== 答案提交测试 =====================

    @Test
    @DisplayName("提交答案-成功提交并返回已更新会话")
    void testSubmitAnswer() {
        Map<String, Object> answers = new HashMap<>();
        answers.put("1", "A");
        answers.put("2", "B,C");
        answers.put("3", "True");

        ExamSession submittedSession = new ExamSession();
        submittedSession.setId(sessionId);
        submittedSession.setExamPaperId(examPaperId);
        submittedSession.setStudentId(studentId);
        submittedSession.setStatus("SUBMITTED");
        submittedSession.setAnswers("{\"1\":\"A\",\"2\":\"B,C\",\"3\":\"True\"}");
        submittedSession.setTimeSpent(1800);
        submittedSession.setSubmitTime(LocalDateTime.now());

        when(examSessionMapper.selectById(sessionId)).thenReturn(sampleSession, submittedSession);
        when(examSessionMapper.update(isNull(), any())).thenReturn(1);

        ExamSession result = examSessionService.submitExam(sessionId, answers);

        assertNotNull(result);
        assertEquals(sessionId, result.getId());
        assertEquals("SUBMITTED", result.getStatus());
        assertNotNull(result.getSubmitTime());
        assertNotNull(result.getTimeSpent());
        assertNotNull(result.getAnswers());

        verify(examSessionMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("提交答案-空答案集合也能提交")
    void testSubmitAnswer_EmptyAnswers() {
        Map<String, Object> emptyAnswers = new HashMap<>();
        ExamSession submittedSession = new ExamSession();
        submittedSession.setId(sessionId);
        submittedSession.setStatus("SUBMITTED");
        submittedSession.setAnswers("{}");

        when(examSessionMapper.selectById(sessionId)).thenReturn(sampleSession, submittedSession);
        when(examSessionMapper.update(isNull(), any())).thenReturn(1);

        ExamSession result = examSessionService.submitExam(sessionId, emptyAnswers);

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        verify(examSessionMapper).update(isNull(), any());
    }

    // ===================== 自动评分测试 =====================

    private ExamQuestion examQuestion(Long questionId, int order) {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(questionId);
        eq.setExamPaperId(examPaperId);
        eq.setOrderIndex(order);
        eq.setPoints(new BigDecimal("10"));
        return eq;
    }

    private Question singleChoice(Long id, String correct) {
        Question q = new Question();
        q.setId(id);
        q.setType("SINGLE_CHOICE");
        q.setCorrectAnswers(correct);
        return q;
    }

    /**
     * 构建 10 道题：6 道单选全对 + 1 道多选超选（部分分 5 分）+ 3 道单选全错
     * -> totalScore 65, fullScore 100, percentage 65.00, correct 6, wrong 4
     */
    private void stubGrading(List<ExamQuestion> eqs, List<Question> qs) {
        startedSubmittedSession();
        when(examSessionMapper.selectById(sessionId)).thenReturn(sampleSession);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(eqs);
        for (int i = 0; i < qs.size(); i++) {
            when(questionService.getById(qs.get(i).getId())).thenReturn(qs.get(i));
        }
        // 全对场景不会触达错题记录，故使用 lenient 避免严格桩校验报错
        lenient().when(wrongQuestionService.count(any())).thenReturn(0L);
        lenient().when(wrongQuestionService.save(any())).thenReturn(true);
    }

    private void startedSubmittedSession() {
        sampleSession.setStatus("SUBMITTED");
    }

    @Test
    @DisplayName("自动评分-客观题完全正确得满分")
    void testAutoGrade_AllCorrect() {
        List<ExamQuestion> eqs = new ArrayList<>();
        List<Question> qs = new ArrayList<>();
        StringBuilder answersJson = new StringBuilder("{");
        String[] corrects = {"A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < 6; i++) {
            long qid = 101L + i;
            eqs.add(examQuestion(qid, i + 1));
            qs.add(singleChoice(qid, corrects[i]));
            if (i > 0) answersJson.append(",");
            answersJson.append("\"").append(qid).append("\":\"").append(corrects[i]).append("\"");
        }
        answersJson.append("}");
        sampleSession.setAnswers(answersJson.toString());
        stubGrading(eqs, qs);

        Map<String, Object> result = examSessionService.autoGrade(sessionId);

        assertNotNull(result);
        assertEquals(sessionId, result.get("sessionId"));
        assertEquals(new BigDecimal("60"), result.get("totalScore"));
        assertEquals(new BigDecimal("60"), result.get("fullScore"));
        assertEquals(new BigDecimal("100.00"), result.get("percentage"));
        assertEquals(6, result.get("correctCount"));
        assertEquals(0, result.get("wrongCount"));
        assertEquals(0, result.get("pendingReviewCount"));
        assertEquals("GRADED", result.get("status"));
    }

    @Test
    @DisplayName("自动评分-部分正确返回混合结果")
    void testAutoGrade_PartialCorrect() {
        List<ExamQuestion> eqs = new ArrayList<>();
        List<Question> qs = new ArrayList<>();
        String[] corrects = {"A", "B", "C", "D", "E", "F"};
        StringBuilder answersJson = new StringBuilder("{");
        for (int i = 0; i < 6; i++) {
            long qid = 101L + i;
            eqs.add(examQuestion(qid, i + 1));
            qs.add(singleChoice(qid, corrects[i]));
            if (i > 0) answersJson.append(",");
            answersJson.append("\"").append(qid).append("\":\"").append(corrects[i]).append("\"");
        }
        // 第 7 题：多选超选（答 [A,B,C,D]，正确答案 [A,B,C]）-> 部分分 10 * 1.0 * 0.5 = 5
        long partialQid = 107L;
        eqs.add(examQuestion(partialQid, 7));
        Question multi = new Question();
        multi.setId(partialQid);
        multi.setType("MULTIPLE_CHOICE");
        multi.setCorrectAnswers("[\"A\",\"B\",\"C\"]");
        qs.add(multi);
        answersJson.append(",\"").append(partialQid).append("\":\"[A, B, C, D]\"");
        // 第 8-10 题：全错
        for (int i = 0; i < 3; i++) {
            long qid = 108L + i;
            eqs.add(examQuestion(qid, 8 + i));
            qs.add(singleChoice(qid, "Z"));
            answersJson.append(",\"").append(qid).append("\":\"X\"");
        }
        answersJson.append("}");
        sampleSession.setAnswers(answersJson.toString());
        stubGrading(eqs, qs);

        Map<String, Object> result = examSessionService.autoGrade(sessionId);

        assertNotNull(result);
        assertEquals(new BigDecimal("65.00"), result.get("totalScore"));
        assertEquals(new BigDecimal("100"), result.get("fullScore"));
        assertEquals(new BigDecimal("65.00"), result.get("percentage"));
        assertEquals(6, result.get("correctCount"));
        assertEquals(4, result.get("wrongCount"));
        assertEquals(0, result.get("pendingReviewCount"));
    }

    @Test
    @DisplayName("自动评分-含主观题标记待人工评分")
    void testAutoGrade_WithPendingReview() {
        List<ExamQuestion> eqs = new ArrayList<>();
        List<Question> qs = new ArrayList<>();
        String[] corrects = {"A", "B", "C", "D", "E", "F", "G"};
        StringBuilder answersJson = new StringBuilder("{");
        for (int i = 0; i < 7; i++) {
            long qid = 101L + i;
            eqs.add(examQuestion(qid, i + 1));
            qs.add(singleChoice(qid, corrects[i]));
            if (i > 0) answersJson.append(",");
            answersJson.append("\"").append(qid).append("\":\"").append(corrects[i]).append("\"");
        }
        // 第 8 题：单选答错
        eqs.add(examQuestion(108L, 8));
        qs.add(singleChoice(108L, "Z"));
        answersJson.append(",\"108\":\"X\"");
        // 第 9-10 题：主观题，待人工评分
        for (int i = 0; i < 2; i++) {
            long qid = 109L + i;
            eqs.add(examQuestion(qid, 9 + i));
            Question essay = new Question();
            essay.setId(qid);
            essay.setType("SHORT_ANSWER");
            essay.setCorrectAnswers(null);
            qs.add(essay);
            answersJson.append(",\"").append(qid).append("\":\"essay\"");
        }
        answersJson.append("}");
        sampleSession.setAnswers(answersJson.toString());
        stubGrading(eqs, qs);

        Map<String, Object> result = examSessionService.autoGrade(sessionId);

        assertNotNull(result);
        assertEquals(new BigDecimal("70"), result.get("totalScore"));
        assertEquals(new BigDecimal("100"), result.get("fullScore"));
        assertEquals(new BigDecimal("70.00"), result.get("percentage"));
        assertEquals(7, result.get("correctCount"));
        assertEquals(1, result.get("wrongCount"));
        assertEquals(2, result.get("pendingReviewCount"));
    }
}
