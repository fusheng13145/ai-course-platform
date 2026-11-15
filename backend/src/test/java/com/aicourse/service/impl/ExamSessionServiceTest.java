package com.aicourse.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.UserService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.component.exam.ExamSessionCheatingComponent;
import com.aicourse.service.component.exam.ExamSessionLifecycleComponent;
import com.aicourse.service.component.exam.ExamSessionQueryComponent;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aicourse.vo.AutoGradeResultVO;
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
 * <p>阅卷逻辑（autoGrade 等）已统一收口到 {@link ExamSessionGradingService}，
 * 逻辑细节由 ExamSessionGradingServiceTest 覆盖，本类验证门面委托行为与异常传播。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceTest {

    @Mock private ExamPaperService examPaperService;
    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ExamSessionGradingService gradingService;
    @Mock private ExamSessionMapper examSessionMapper;
    @Mock private ExamPaperMapper examPaperMapper;

    private ExamSessionServiceImpl examSessionService;

    private ExamSession sampleSession;
    private ExamPaper publishedPaper;
    private Long examPaperId;
    private Long studentId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        // 原兼容构造器已移除：内联其组件组装逻辑，再走主构造器（3 组件 + gradingService）
        ObjectMapper objectMapper = new ObjectMapper();
        ExamSessionLifecycleComponent lifecycleComponent =
                new ExamSessionLifecycleComponent(examPaperService, null,
                        org.mockito.Mockito.mock(com.aicourse.service.component.org.TeachingClassSnapshotResolver.class),
                        objectMapper, examPaperMapper);
        ExamSessionCheatingComponent cheatingComponent =
                new ExamSessionCheatingComponent(examPaperService, null, objectMapper);
        ExamSessionQueryComponent queryComponent =
                new ExamSessionQueryComponent(examPaperService, examQuestionService, null, objectMapper,
                        mock(CourseService.class), mock(UserService.class));
        examSessionService = new ExamSessionServiceImpl(lifecycleComponent, cheatingComponent,
                queryComponent, gradingService);
        ReflectionTestUtils.setField(examSessionService, "baseMapper", examSessionMapper);
        for (String field : new String[]{"lifecycleComponent", "cheatingComponent", "queryComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(examSessionService, field), "examSessionMapper", examSessionMapper);
        }
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

    // ===================== autoGrade（委托 GradingService） =====================

    @Test
    @DisplayName("自动评分-委托 GradingService 并原样返回结果")
    void testAutoGrade_Delegates() {
        AutoGradeResultVO expected = new AutoGradeResultVO(sessionId, new BigDecimal("65.00"), new BigDecimal("100"), new BigDecimal("65.00"), 6, 4, 0, Map.of(), "GRADED");
        when(gradingService.autoGrade(sessionId)).thenReturn(expected);

        AutoGradeResultVO result = examSessionService.autoGrade(sessionId);

        assertSame(expected, result);
        verify(gradingService).autoGrade(sessionId);
    }

    @Test
    @DisplayName("自动评分-GradingService 校验失败异常原样传播")
    void testAutoGrade_PropagatesBusinessException() {
        when(gradingService.autoGrade(sessionId))
                .thenThrow(new BusinessException("试卷尚未提交，无法进行自动阅卷"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.autoGrade(sessionId));
        assertEquals("试卷尚未提交，无法进行自动阅卷", ex.getMessage());
        verify(gradingService).autoGrade(sessionId);
    }

}
