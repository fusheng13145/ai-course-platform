package com.aicourse.service.impl;

import com.aicourse.entity.ExamSession;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.aicourse.service.impl.exam.ExamSessionLifecycleService;
import com.aicourse.service.impl.exam.ExamSessionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话服务单元测试
 * 测试 ExamSessionServiceImpl 的考试开始、答案提交和自动评分核心流程
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceTest {

    @Mock private ExamSessionLifecycleService lifecycleService;
    @Mock private ExamSessionGradingService gradingService;
    @Mock private ExamSessionQueryService queryService;

    @InjectMocks private ExamSessionServiceImpl examSessionService;

    private ExamSession sampleSession;
    private Long examPaperId;
    private Long studentId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        examPaperId = 100L;
        studentId = 200L;
        sessionId = 1L;

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

        when(lifecycleService.startExam(examPaperId, studentId, ipAddress, userAgent))
                .thenReturn(sampleSession);

        ExamSession result = examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);

        assertNotNull(result);
        assertEquals(sessionId, result.getId());
        assertEquals(examPaperId, result.getExamPaperId());
        assertEquals(studentId, result.getStudentId());
        assertEquals("STARTED", result.getStatus());
        assertEquals(1, result.getAttemptNumber());
        assertEquals(ipAddress, result.getIpAddress());
        assertEquals(userAgent, result.getUserAgent());
        assertNotNull(result.getStartTime());

        verify(lifecycleService).startExam(examPaperId, studentId, ipAddress, userAgent);
    }

    @Test
    @DisplayName("开始考试-委托验证参数正确传递")
    void testStartExam_ParameterPropagation() {
        String ipAddress = "10.0.0.1";
        String userAgent = "Chrome/120.0";

        when(lifecycleService.startExam(eq(examPaperId), eq(studentId), eq(ipAddress), eq(userAgent)))
                .thenReturn(sampleSession);

        examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);

        verify(lifecycleService, times(1)).startExam(
                eq(examPaperId), eq(studentId), eq(ipAddress), eq(userAgent));
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

        when(lifecycleService.submitExam(sessionId, answers)).thenReturn(submittedSession);

        ExamSession result = examSessionService.submitExam(sessionId, answers);

        assertNotNull(result);
        assertEquals(sessionId, result.getId());
        assertEquals("SUBMITTED", result.getStatus());
        assertNotNull(result.getSubmitTime());
        assertNotNull(result.getTimeSpent());
        assertNotNull(result.getAnswers());

        verify(lifecycleService).submitExam(sessionId, answers);
    }

    @Test
    @DisplayName("提交答案-空答案集合也能提交")
    void testSubmitAnswer_EmptyAnswers() {
        Map<String, Object> emptyAnswers = new HashMap<>();
        ExamSession submittedSession = new ExamSession();
        submittedSession.setId(sessionId);
        submittedSession.setStatus("SUBMITTED");
        submittedSession.setAnswers("{}");

        when(lifecycleService.submitExam(sessionId, emptyAnswers)).thenReturn(submittedSession);

        ExamSession result = examSessionService.submitExam(sessionId, emptyAnswers);

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        verify(lifecycleService).submitExam(sessionId, emptyAnswers);
    }

    // ===================== 自动评分测试 =====================

    @Test
    @DisplayName("自动评分-客观题完全正确得满分")
    void testAutoGrade_AllCorrect() {
        Map<String, Object> gradingResult = new LinkedHashMap<>();
        gradingResult.put("sessionId", sessionId);
        gradingResult.put("totalScore", new BigDecimal("100"));
        gradingResult.put("fullScore", new BigDecimal("100"));
        gradingResult.put("percentage", new BigDecimal("100.00"));
        gradingResult.put("correctCount", 10);
        gradingResult.put("wrongCount", 0);
        gradingResult.put("pendingReviewCount", 0);
        gradingResult.put("status", "GRADED");

        when(gradingService.autoGrade(sessionId)).thenReturn(gradingResult);

        Map<String, Object> result = examSessionService.autoGrade(sessionId);

        assertNotNull(result);
        assertEquals(sessionId, result.get("sessionId"));
        assertEquals(new BigDecimal("100"), result.get("totalScore"));
        assertEquals(new BigDecimal("100"), result.get("fullScore"));
        assertEquals(new BigDecimal("100.00"), result.get("percentage"));
        assertEquals(10, result.get("correctCount"));
        assertEquals(0, result.get("wrongCount"));
        assertEquals("GRADED", result.get("status"));

        verify(gradingService).autoGrade(sessionId);
    }

    @Test
    @DisplayName("自动评分-部分正确返回混合结果")
    void testAutoGrade_PartialCorrect() {
        Map<String, Object> gradingResult = new LinkedHashMap<>();
        gradingResult.put("sessionId", sessionId);
        gradingResult.put("totalScore", new BigDecimal("65"));
        gradingResult.put("fullScore", new BigDecimal("100"));
        gradingResult.put("percentage", new BigDecimal("65.00"));
        gradingResult.put("correctCount", 6);
        gradingResult.put("wrongCount", 4);
        gradingResult.put("pendingReviewCount", 0);
        gradingResult.put("status", "GRADED");

        when(gradingService.autoGrade(sessionId)).thenReturn(gradingResult);

        Map<String, Object> result = examSessionService.autoGrade(sessionId);

        assertNotNull(result);
        assertEquals(new BigDecimal("65"), result.get("totalScore"));
        assertEquals(6, result.get("correctCount"));
        assertEquals(4, result.get("wrongCount"));
        assertEquals(new BigDecimal("65.00"), result.get("percentage"));

        verify(gradingService).autoGrade(sessionId);
    }

    @Test
    @DisplayName("自动评分-含主观题标记待人工评分")
    void testAutoGrade_WithPendingReview() {
        Map<String, Object> gradingResult = new LinkedHashMap<>();
        gradingResult.put("sessionId", sessionId);
        gradingResult.put("totalScore", new BigDecimal("70"));
        gradingResult.put("fullScore", new BigDecimal("100"));
        gradingResult.put("percentage", new BigDecimal("70.00"));
        gradingResult.put("correctCount", 7);
        gradingResult.put("wrongCount", 1);
        gradingResult.put("pendingReviewCount", 2);
        gradingResult.put("status", "GRADED");

        when(gradingService.autoGrade(sessionId)).thenReturn(gradingResult);

        Map<String, Object> result = examSessionService.autoGrade(sessionId);

        assertNotNull(result);
        assertEquals(2, result.get("pendingReviewCount"));
        assertEquals(7, result.get("correctCount"));
        assertEquals(1, result.get("wrongCount"));
        assertEquals(new BigDecimal("70"), result.get("totalScore"));

        verify(gradingService).autoGrade(sessionId);
    }
}
