package com.aicourse.service.impl.exam;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话生命周期协作者单元测试（迁移自原 ExamSessionServiceImplTest 的对应用例，并补充未覆盖方法）。
 * 直接针对 {@link ExamSessionLifecycleService}，保持等价断言强度。
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionLifecycleServiceTest {

    @Mock private ExamPaperService examPaperService;
    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamSessionMapper examSessionMapper;
    @Mock private ExamSessionGradingService gradingService;

    @InjectMocks private ExamSessionLifecycleService lifecycleService;

    private ExamPaper testExamPaper;
    private ExamSession testSession;

    @BeforeEach
    void setUp() {
        testExamPaper = new ExamPaper();
        testExamPaper.setId(1L);
        testExamPaper.setTitle("期末测试");
        testExamPaper.setStatus("PUBLISHED");
        testExamPaper.setStartTime(LocalDateTime.now().minusDays(1));
        testExamPaper.setEndTime(LocalDateTime.now().plusDays(1));
        testExamPaper.setTotalScore(new java.math.BigDecimal("100"));
        testExamPaper.setMaxAttempts(3);
        testExamPaper.setDuration(120);

        testSession = new ExamSession();
        testSession.setId(1L);
        testSession.setExamPaperId(1L);
        testSession.setStudentId(100L);
        testSession.setStatus("STARTED");
        testSession.setStartTime(LocalDateTime.now().minusMinutes(30));
        testSession.setAttemptNumber(1);
        testSession.setIpAddress("192.168.1.1");
        testSession.setUserAgent("Mozilla/5.0");
    }

    // ==================== startExam ====================

    @Test
    @DisplayName("开始考试 - 成功开始")
    void testStartExam_Success() {
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(examSessionMapper.insert((ExamSession) any())).thenReturn(1);

        ExamSession result = lifecycleService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0");

        assertNotNull(result);
        assertEquals("STARTED", result.getStatus());
        assertEquals(1, result.getAttemptNumber());
        verify(examSessionMapper).insert((ExamSession) any());
    }

    @Test
    @DisplayName("开始考试 - 试卷不存在")
    void testStartExam_ExamNotFound() {
        when(examPaperService.getById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.startExam(999L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("试卷不存在", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 试卷未发布")
    void testStartExam_NotPublished() {
        testExamPaper.setStatus("DRAFT");
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("试卷尚未发布，无法开始考试", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 考试尚未开始")
    void testStartExam_BeforeStartTime() {
        testExamPaper.setStartTime(LocalDateTime.now().plusHours(1));
        testExamPaper.setEndTime(null);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("考试尚未开始", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 考试已结束")
    void testStartExam_AfterEndTime() {
        testExamPaper.setStartTime(LocalDateTime.now().minusDays(2));
        testExamPaper.setEndTime(LocalDateTime.now().minusDays(1));
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("考试已结束", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 超过最大尝试次数")
    void testStartExam_MaxAttemptsExceeded() {
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertTrue(exception.getMessage().contains("已达到最大尝试次数限制"));
    }

    @Test
    @DisplayName("开始考试 - 已有进行中的考试")
    void testStartExam_DuplicateActiveSession() {
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("您有正在进行的考试，请先完成或结束当前考试", exception.getMessage());
    }

    // ==================== submitExam ====================

    @Test
    @DisplayName("提交试卷 - 成功提交")
    void testSubmitExam_Success() {
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        ExamSession result = lifecycleService.submitExam(1L, new HashMap<>());

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        assertNotNull(result.getSubmitTime());
        assertNotNull(result.getTimeSpent());
        verify(examSessionMapper).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("提交试卷 - 重复提交防重")
    void testSubmitExam_DuplicateSubmit() {
        testSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.submitExam(1L, new HashMap<>()));
        assertTrue(exception.getMessage().contains("无法重复提交"));
    }

    @Test
    @DisplayName("提交试卷 - 会话不存在")
    void testSubmitExam_NotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.submitExam(999L, new HashMap<>()));
        assertEquals("考试会话不存在", exception.getMessage());
    }

    // ==================== autoSaveAnswers ====================

    @Test
    @DisplayName("自动保存 - 成功保存")
    void testAutoSaveAnswers_Success() {
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        assertDoesNotThrow(() -> lifecycleService.autoSaveAnswers(1L, new HashMap<>()));
        verify(examSessionMapper).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("自动保存 - 会话不存在")
    void testAutoSaveAnswers_NotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.autoSaveAnswers(999L, new HashMap<>()));
        assertEquals("考试会话不存在", exception.getMessage());
    }

    @Test
    @DisplayName("自动保存 - 非进行中状态拒绝")
    void testAutoSaveAnswers_WrongStatus() {
        testSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.autoSaveAnswers(1L, new HashMap<>()));
        assertEquals("当前考试状态不允许保存答案", exception.getMessage());
    }

    // ==================== forceSubmit ====================

    @Test
    @DisplayName("强制交卷 - 成功")
    void testForceSubmit_Success() {
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        ExamSession result = lifecycleService.forceSubmit(1L);

        assertEquals("FORCE_SUBMITTED", result.getStatus());
        assertNotNull(result.getSubmitTime());
        verify(examSessionMapper).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("强制交卷 - 已提交拒绝")
    void testForceSubmit_AlreadySubmitted() {
        testSession.setStatus("GRADED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.forceSubmit(1L));
        assertEquals("该试卷已经提交，无需强制交卷", exception.getMessage());
    }

    @Test
    @DisplayName("强制交卷 - 会话不存在")
    void testForceSubmit_NotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> lifecycleService.forceSubmit(999L));
        assertEquals("考试会话不存在", exception.getMessage());
    }

    // ==================== timeoutSubmit ====================

    @Test
    @DisplayName("超时收卷 - 非进行中状态直接返回")
    void testTimeoutSubmit_NotStarted() {
        testSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);

        ExamSession result = lifecycleService.timeoutSubmit(1L);

        assertSame(testSession, result);
        verify(gradingService, never()).autoGrade(any());
        verify(examSessionMapper, never()).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("超时收卷 - 进行中且已超时则收卷并触发阅卷")
    void testTimeoutSubmit_Expired() {
        // 开始时间远在过去，考试时长 120 分钟 → 截止时间已过去
        testSession.setStartTime(LocalDateTime.now().minusHours(3));
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);
        when(gradingService.autoGrade(1L)).thenReturn(new HashMap<>());

        ExamSession result = lifecycleService.timeoutSubmit(1L);

        assertEquals("TIMEOUT", result.getStatus());
        verify(examSessionMapper).updateById((ExamSession) any());
        verify(gradingService).autoGrade(1L);
    }

    @Test
    @DisplayName("超时收卷 - 进行中但未到截止时间则跳过")
    void testTimeoutSubmit_NotExpired() {
        testSession.setStartTime(LocalDateTime.now().minusMinutes(10));
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        ExamSession result = lifecycleService.timeoutSubmit(1L);

        assertEquals("STARTED", result.getStatus());
        verify(gradingService, never()).autoGrade(any());
    }
}
