package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.RAGService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.ai.CourseEvidenceComponent;
import com.aicourse.service.component.submission.SubmissionGradeComponent;
import com.aicourse.service.component.submission.SubmissionJsonComponent;
import com.aicourse.service.component.submission.SubmissionReadComponent;
import com.aicourse.service.component.submission.SubmissionWriteComponent;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 提交服务聚合门面单元测试（B 主战场 P1，缺口最大且覆盖率最低）。
 * 自身 extends ServiceImpl<SubmissionMapper, Submission>，故手动构造并注入 baseMapper(mock)；
 * MeterRegistry 用真实 SimpleMeterRegistry 避免 Counter NPE；内部 lambda 涉及 Submission 需注册 TableInfo；
 * *.list(any()) 锚定 any(Wrapper.class)；submissionMapper.insert/updateById 锚定 any(Submission.class)；
 * 私有 JSON 助手走注入的 objectMapper，需 stub readValue/writeValueAsString。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionServiceImplTest {

    @Mock private TaskService taskService;
    @Mock private PlagiarismCheckService plagiarismCheckService;
    @Mock private AIService aiService;
    @Mock private ObjectMapper objectMapper;
    @Mock private NotificationService notificationService;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private RAGService ragService;
    @Mock private KnowledgePointService knowledgePointService;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private SubmissionServiceImpl service;

    private Task task;
    private Submission submission;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);

        // 原兼容构造器已移除：内联其组件组装逻辑，再走主构造器（3 个组件）
        SubmissionJsonComponent jsonSupport = new SubmissionJsonComponent(objectMapper);
        SubmissionWriteComponent writeComponent =
                new SubmissionWriteComponent(null, taskService, jsonSupport,
                        org.mockito.Mockito.mock(com.aicourse.service.component.org.TeachingClassSnapshotResolver.class),
                        meterRegistry);
        CourseEvidenceComponent evidenceComponent =
                new CourseEvidenceComponent(ragService, knowledgePointService);
        SubmissionGradeComponent gradeComponent = new SubmissionGradeComponent(null, taskService,
                notificationService, aiService, jsonSupport, writeComponent, evidenceComponent);
        SubmissionReadComponent readComponent =
                new SubmissionReadComponent(null, plagiarismCheckService, aiService, jsonSupport);
        service = new SubmissionServiceImpl(writeComponent, gradeComponent, readComponent);
        ReflectionTestUtils.setField(service, "baseMapper", submissionMapper);
        // 同步 mapper 到内部组件（兼容构造器组装的真实组件初始持有 null mapper）
        for (String field : new String[]{"writeComponent", "gradeComponent", "readComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(service, field), "submissionMapper", submissionMapper);
        }

        task = new Task();
        task.setId(1L); task.setCourseId(1L); task.setTitle("作业1");
        task.setIsPublished(Boolean.TRUE); task.setMaxScore(new BigDecimal("100"));
        task.setDueDate(null); task.setMaxSubmissions(null); task.setLatePenaltyPerDay(null);

        submission = new Submission();
        submission.setId(1L); submission.setTaskId(1L); submission.setStudentId(11L);
        submission.setStatus("SUBMITTED"); submission.setContent("解答"); submission.setViewCount(0);
        submission.setMaxScore(new BigDecimal("100")); submission.setAttemptNumber(1);

        when(taskService.getById(anyLong())).thenReturn(task);
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        when(submissionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        when(submissionMapper.insert(any(Submission.class))).thenReturn(1);
        when(submissionMapper.updateById(any(Submission.class))).thenReturn(1);
        when(submissionMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        when(aiService.generateContent(anyString(), anyString()))
                .thenReturn("{\"score\":85,\"feedback\":\"good\",\"highlights\":\"h\",\"suggestions\":\"s\"}");
        when(aiService.checkPlagiarism(anyString(), anyString())).thenReturn("0");

        when(plagiarismCheckService.save(any(PlagiarismCheck.class))).thenReturn(true);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(plagiarismCheckService.updateById(any(PlagiarismCheck.class))).thenReturn(true);
        // objectMapper 用 mock 默认行为即可：readValue 返回 null -> parseData 容错返回空 Map，
        // writeValueAsString 返回 null -> submissionData 置空，均不抛受检异常。
    }

    // ===================== submitAssignment =====================

    @Test
    @DisplayName("submitAssignment 任务不存在抛异常")
    void submit_taskNull() {
        when(taskService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitAssignment(1L, 11L, "c", null));
    }

    @Test
    @DisplayName("submitAssignment 未发布抛异常")
    void submit_unpublished() {
        task.setIsPublished(Boolean.FALSE);
        assertThrows(BusinessException.class, () -> service.submitAssignment(1L, 11L, "c", null));
    }

    @Test
    @DisplayName("submitAssignment 正常新建提交")
    void submit_ok_new() {
        Submission r = service.submitAssignment(1L, 11L, "解答", List.of("a.png"));
        assertEquals("SUBMITTED", r.getStatus());
        assertEquals(1, r.getAttemptNumber());
        verify(submissionMapper).insert(any(Submission.class));
    }

    @Test
    @DisplayName("submitAssignment 草稿转正走更新")
    void submit_ok_draft() {
        Submission draft = new Submission();
        draft.setId(9L); draft.setTaskId(1L); draft.setStudentId(11L);
        draft.setStatus("DRAFT"); draft.setFileUrls("old.png"); draft.setViewCount(2);
        when(submissionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(draft);
        Submission r = service.submitAssignment(1L, 11L, "解答", null);
        assertEquals("SUBMITTED", r.getStatus());
        assertEquals("old.png", r.getFileUrls()); // 未带新附件保留草稿附件
        verify(submissionMapper).updateById(any(Submission.class));
    }

    @Test
    @DisplayName("submitAssignment 逾期计算扣分")
    void submit_late() {
        task.setDueDate(LocalDateTime.now().minusDays(2));
        Submission r = service.submitAssignment(1L, 11L, "解答", null);
        assertTrue(r.getIsLate());
        assertNotNull(r.getLatePenalty());
        assertTrue(r.getLatePenalty().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("submitAssignment 超过最大提交次数抛异常")
    void submit_maxLimit() {
        task.setMaxSubmissions(1);
        when(submissionMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.submitAssignment(1L, 11L, "c", null));
    }

    // ===================== saveDraft =====================

    @Test
    @DisplayName("saveDraft 任务不存在抛异常")
    void draft_taskNull() {
        when(taskService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.saveDraft(1L, 11L, "c", null));
    }

    @Test
    @DisplayName("saveDraft 新建草稿")
    void draft_ok_new() {
        Submission r = service.saveDraft(1L, 11L, "草稿", null);
        assertEquals("DRAFT", r.getStatus());
        verify(submissionMapper).insert(any(Submission.class));
    }

    @Test
    @DisplayName("saveDraft 已有草稿则更新")
    void draft_ok_update() {
        Submission draft = new Submission();
        draft.setId(9L); draft.setTaskId(1L); draft.setStudentId(11L); draft.setStatus("DRAFT");
        when(submissionMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(draft);
        Submission r = service.saveDraft(1L, 11L, "草稿2", null);
        assertEquals("DRAFT", r.getStatus());
        verify(submissionMapper).updateById(any(Submission.class));
    }

    // ===================== resubmit =====================

    @Test
    @DisplayName("resubmit 提交记录不存在抛异常")
    void resubmit_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resubmit(1L, "c", null));
    }

    @Test
    @DisplayName("resubmit 状态不允许抛异常")
    void resubmit_wrongStatus() {
        submission.setStatus("GRADED");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.resubmit(1L, "c", null));
    }

    @Test
    @DisplayName("resubmit 退回态可重新提交")
    void resubmit_ok() {
        submission.setStatus("RETURNED");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission r = service.resubmit(1L, "c", null);
        assertEquals("RESUBMITTED", r.getStatus());
        verify(submissionMapper).insert(any(Submission.class));
    }

    // ===================== returnSubmission =====================

    @Test
    @DisplayName("returnSubmission 记录不存在抛异常")
    void return_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.returnSubmission(1L, "原因"));
    }

    @Test
    @DisplayName("returnSubmission 已评分不可退回")
    void return_graded() {
        submission.setStatus("GRADED");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.returnSubmission(1L, "原因"));
    }

    @Test
    @DisplayName("returnSubmission 正常退回写入原因")
    void return_ok() {
        submission.setStatus("SUBMITTED");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission r = service.returnSubmission(1L, "不合格");
        assertEquals("RETURNED", r.getStatus());
        assertEquals("不合格", r.getFeedback());
        verify(submissionMapper).updateById(any(Submission.class));
    }

    // ===================== gradeSubmission =====================

    @Test
    @DisplayName("gradeSubmission 记录不存在抛异常")
    void grade_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.gradeSubmission(1L, BigDecimal.TEN, "f", 2L));
    }

    @Test
    @DisplayName("gradeSubmission 逾期扣除惩罚分")
    void grade_late() {
        submission.setIsLate(Boolean.TRUE);
        submission.setLatePenalty(new BigDecimal("20"));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission r = service.gradeSubmission(1L, new BigDecimal("80"), "f", 2L);
        assertEquals(new BigDecimal("60"), r.getScore());
        assertNotNull(r.getPercentage());
        verify(submissionMapper).updateById(any(Submission.class));
        verify(notificationService).onEvent(eq("SUBMISSION_GRADED"), any());
    }

    @Test
    @DisplayName("gradeSubmission 正常不逾期")
    void grade_normal() {
        submission.setIsLate(Boolean.FALSE);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission r = service.gradeSubmission(1L, new BigDecimal("90"), "f", 2L);
        assertEquals(new BigDecimal("90"), r.getScore());
        assertEquals("GRADED", r.getStatus());
    }

    // ===================== gradeSubmissionByTeacher =====================

    @Test
    @DisplayName("gradeSubmissionByTeacher 记录不存在抛异常")
    void gradeT_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.gradeSubmissionByTeacher(1L, 1L, new Submission(), 2L));
    }

    @Test
    @DisplayName("gradeSubmissionByTeacher 作业不匹配抛 403")
    void gradeT_mismatch() {
        submission.setTaskId(1L);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.gradeSubmissionByTeacher(99L, 1L, new Submission(), 2L));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("gradeSubmissionByTeacher 正常批改")
    void gradeT_ok() {
        submission.setTaskId(1L);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission grade = new Submission();
        grade.setScore(new BigDecimal("88")); grade.setFeedback("好");
        Submission r = service.gradeSubmissionByTeacher(1L, 1L, grade, 2L);
        assertEquals("GRADED", r.getStatus());
        verify(submissionMapper).updateById(any(Submission.class));
    }

    // ===================== aiGradeSubmission =====================

    @Test
    @DisplayName("aiGradeSubmission 记录不存在抛异常")
    void ai_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.aiGradeSubmission(1L));
    }

    @Test
    @DisplayName("aiGradeSubmission 内容为空抛异常")
    void ai_emptyContent() {
        submission.setContent("");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.aiGradeSubmission(1L));
    }

    @Test
    @DisplayName("aiGradeSubmission 解析 JSON 成功：只写 AI 派生列，不整行写回覆盖教师状态/分数")
    void ai_ok() throws Exception {
        submission.setContent("解答内容");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        // mock ObjectMapper 默认 writeValueAsString 返回 null，会让 submission_data 为空而漏写该列
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        Submission r = service.aiGradeSubmission(1L);
        assertNotNull(r.getAiFeedback());

        ArgumentCaptor<Wrapper<Submission>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionMapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertNotNull(sqlSet);
        assertTrue(sqlSet.contains("ai_feedback"), "必须写入 ai_feedback");
        assertTrue(sqlSet.contains("submission_data"), "必须写入 aiSuggestedScore 等派生数据");
        assertFalse(sqlSet.contains("status"), "不得写 status——陈旧快照会把教师退回/批阅冲回 SUBMITTED");
        assertFalse(sqlSet.contains("score"), "不得写 score——避免覆盖教师分数");
        verify(submissionMapper, never()).updateById(any(Submission.class));
    }

    @Test
    @DisplayName("aiGradeSubmission AI 失败：也只写 ai_feedback，不碰 status/score")
    void ai_failure_onlyWritesAiFeedback() {
        submission.setContent("解答内容");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(aiService.generateContent(anyString(), anyString())).thenThrow(new RuntimeException("LLM boom"));

        service.aiGradeSubmission(1L);

        ArgumentCaptor<Wrapper<Submission>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionMapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertNotNull(sqlSet);
        assertTrue(sqlSet.contains("ai_feedback"));
        assertFalse(sqlSet.contains("status"));
        assertFalse(sqlSet.contains("score"));
        verify(submissionMapper, never()).updateById(any(Submission.class));
    }

    @Test
    @DisplayName("aiGradeSubmission JSON 解析失败回退原文")
    void ai_parseFail() {
        submission.setContent("解答内容");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(aiService.generateContent(anyString(), anyString())).thenReturn("不是JSON");
        Submission r = service.aiGradeSubmission(1L);
        assertEquals("不是JSON", r.getAiFeedback());
    }

    // ===================== calculateLatePenalty =====================

    @Test
    @DisplayName("calculateLatePenalty 记录不存在抛异常")
    void penalty_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.calculateLatePenalty(1L));
    }

    @Test
    @DisplayName("calculateLatePenalty 已有惩罚值直接返回")
    void penalty_existing() {
        submission.setLatePenalty(new BigDecimal("15"));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertEquals(new BigDecimal("15"), service.calculateLatePenalty(1L));
    }

    @Test
    @DisplayName("calculateLatePenalty 重算逾期为正")
    void penalty_recompute_late() {
        submission.setLatePenalty(null);
        submission.setSubmittedAt(LocalDateTime.now().minusDays(3));
        task.setDueDate(LocalDateTime.now().minusDays(5));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        BigDecimal p = service.calculateLatePenalty(1L);
        assertTrue(p.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("calculateLatePenalty 未逾期返回零")
    void penalty_recompute_notLate() {
        submission.setLatePenalty(null);
        submission.setSubmittedAt(LocalDateTime.now());
        task.setDueDate(LocalDateTime.now().plusDays(5));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertEquals(BigDecimal.ZERO, service.calculateLatePenalty(1L));
    }

    // ===================== incrementViewCount =====================

    @Test
    @DisplayName("incrementViewCount 记录不存在抛异常")
    void view_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.incrementViewCount(1L));
    }

    @Test
    @DisplayName("incrementViewCount 正常 +1")
    void view_ok() {
        submission.setViewCount(5);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        service.incrementViewCount(1L);
        assertEquals(6, submission.getViewCount());
        verify(submissionMapper).updateById(any(Submission.class));
    }

    // ===================== triggerPlagiarismCheck =====================

    @Test
    @DisplayName("triggerPlagiarismCheck 记录不存在抛异常")
    void plag_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.triggerPlagiarismCheck(1L));
    }

    @Test
    @DisplayName("triggerPlagiarismCheck 内容为空抛异常")
    void plag_emptyContent() {
        submission.setContent("");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.triggerPlagiarismCheck(1L));
    }

    @Test
    @DisplayName("triggerPlagiarismCheck 无他者提交为低风险")
    void plag_noOthers() {
        submission.setContent("解答内容");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(submissionMapper.selectList(any())).thenReturn(new ArrayList<>());
        PlagiarismCheck r = service.triggerPlagiarismCheck(1L);
        assertEquals("LOW_RISK", r.getStatus());
        verify(plagiarismCheckService).updateById(any(PlagiarismCheck.class));
    }

    @Test
    @DisplayName("triggerPlagiarismCheck 高相似度为高风险")
    void plag_highRisk() {
        submission.setContent("解答内容");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission other = new Submission();
        other.setId(2L); other.setContent("他人解答");
        when(submissionMapper.selectList(any())).thenReturn(List.of(other));
        when(aiService.checkPlagiarism(anyString(), anyString())).thenReturn("85.5");
        PlagiarismCheck r = service.triggerPlagiarismCheck(1L);
        assertEquals("HIGH_RISK", r.getStatus());
    }

    // ===================== getByTaskAndStudent =====================

    @Test
    @DisplayName("getByTaskAndStudent 返回最新提交：attemptNumber DESC + createdAt DESC + LIMIT 1")
    void getByTaskAndStudent_ok() {
        when(submissionMapper.selectOne(any(Wrapper.class), eq(false))).thenReturn(submission);
        Submission r = service.getByTaskAndStudent(1L, 11L);
        assertNotNull(r);
        assertEquals(1L, r.getId());

        ArgumentCaptor<Wrapper<Submission>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionMapper).selectOne(captor.capture(), eq(false));
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("attempt_number DESC"), "应按 attempt_number 倒序取最新尝试，实际=" + sql);
        assertTrue(sql.contains("created_at DESC"), "同 attemptNumber 时按 createdAt 倒序");
        assertTrue(sql.contains("LIMIT 1"), "必须 LIMIT 1，避免多尝试触发 TooManyResultsException");
    }

    // ===================== getPlagiarismSummary =====================

    @Test
    @DisplayName("getPlagiarismSummary 记录不存在抛异常")
    void summary_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getPlagiarismSummary(1L));
    }

    @Test
    @DisplayName("getPlagiarismSummary 无查重记录")
    void summary_noChecks() {
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getPlagiarismSummary(1L);
        assertEquals(false, r.get("hasCheck"));
    }

    @Test
    @DisplayName("getPlagiarismSummary 有查重记录判风险等级")
    void summary_withChecks() {
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        PlagiarismCheck c = new PlagiarismCheck();
        c.setId(5L); c.setSimilarityScore(new BigDecimal("0.85"));
        c.setStatus("HIGH_RISK"); c.setResultSummary("相似");
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(List.of(c));
        Map<String, Object> r = service.getPlagiarismSummary(1L);
        assertEquals(true, r.get("hasCheck"));
        assertEquals("HIGH_RISK", r.get("riskLevel"));
    }

    // ===================== getSubmissionDetail =====================

    @Test
    @DisplayName("getSubmissionDetail 记录不存在抛异常")
    void detail_null() {
        when(submissionMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getSubmissionDetail(1L));
    }

    @Test
    @DisplayName("getSubmissionDetail 组装扩展信息与查重摘要")
    void detail_ok() {
        submission.setSubmissionData("{}");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getSubmissionDetail(1L);
        assertEquals(1L, r.get("id"));
        assertNotNull(r.get("plagiarismInfo"));
    }
}
