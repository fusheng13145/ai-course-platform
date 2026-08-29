package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.SubmissionJsonSupport;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖率补齐：SubmissionServiceImpl 现有测试未覆盖的分支
 * （逾期扣分各分支、最大提交次数限制、草稿复用、resubmit 状态校验、AI 评分失败/JSON 解析失败、
 * 查重风险等级、查重摘要风险判定、私有 JSON 辅助容错等）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionServiceImplBoostTest {

    @Mock private TaskService taskService;
    @Mock private PlagiarismCheckService plagiarismCheckService;
    @Mock private AIService aiService;
    @Mock private NotificationService notificationService;
    @Mock private SubmissionMapper submissionMapper;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ObjectMapper realObjectMapper = new ObjectMapper();

    private SubmissionServiceImpl service;
    private Task task;
    private Submission submission;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PlagiarismCheck.class);

        service = new SubmissionServiceImpl(taskService, plagiarismCheckService, aiService,
                realObjectMapper, notificationService, meterRegistry);
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

        lenient().when(taskService.getById(anyLong())).thenReturn(task);
        lenient().when(submissionMapper.selectById(anyLong())).thenReturn(null);
        lenient().when(submissionMapper.selectOne(any())).thenReturn(null);
        lenient().when(submissionMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(submissionMapper.selectCount(any())).thenReturn(0L);
        lenient().when(submissionMapper.insert(any(Submission.class))).thenReturn(1);
        lenient().when(submissionMapper.updateById(any(Submission.class))).thenReturn(1);
        lenient().when(aiService.generateContent(anyString(), anyString()))
                .thenReturn("{\"score\":85,\"feedback\":\"good\",\"highlights\":\"h\",\"suggestions\":\"s\"}");
        lenient().when(aiService.checkPlagiarism(anyString(), anyString())).thenReturn("0");
        lenient().when(plagiarismCheckService.save(any(PlagiarismCheck.class))).thenReturn(true);
        lenient().when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(plagiarismCheckService.updateById(any(PlagiarismCheck.class))).thenReturn(true);
    }

    // ===== submitAssignment 逾期 / 上限 / 草稿 =====

    @Test
    @DisplayName("submitAssignment 达到最大提交次数限制抛异常")
    void submit_maxSubmissions() {
        task.setMaxSubmissions(2);
        when(submissionMapper.selectCount(any())).thenReturn(2L);
        assertThrows(BusinessException.class, () -> service.submitAssignment(1L, 11L, "c", null));
    }

    @Test
    @DisplayName("submitAssignment 逾期扣分计算")
    void submit_latePenalty() {
        task.setDueDate(LocalDateTime.now().minusDays(3));
        task.setLatePenaltyPerDay(new BigDecimal("10"));
        Submission r = service.submitAssignment(1L, 11L, "c", null);
        assertTrue(r.getIsLate());
        assertEquals(0, r.getLatePenalty().compareTo(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("submitAssignment 逾期扣分封顶为满分")
    void submit_latePenaltyCapped() {
        task.setDueDate(LocalDateTime.now().minusDays(100));
        task.setLatePenaltyPerDay(new BigDecimal("50"));
        task.setMaxScore(new BigDecimal("100"));
        Submission r = service.submitAssignment(1L, 11L, "c", null);
        assertEquals(0, r.getLatePenalty().compareTo(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("submitAssignment 复用草稿走更新且保留附件")
    void submit_reuseDraft() {
        Submission draft = new Submission();
        draft.setId(9L); draft.setFileUrls("old.png"); draft.setViewCount(3);
        when(submissionMapper.selectOne(any(), anyBoolean())).thenReturn(draft);
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        Submission r = service.submitAssignment(1L, 11L, "c", null);
        assertEquals(9L, r.getId());
        assertEquals("old.png", r.getFileUrls());
        assertEquals(3, r.getViewCount());
        verify(submissionMapper).updateById(any(Submission.class));
    }

    @Test
    @DisplayName("submitAssignment fileUrls 为空保留草稿附件")
    void submit_noNewFilesKeepsDraft() {
        Submission draft = new Submission();
        draft.setId(9L); draft.setFileUrls("kept.png");
        when(submissionMapper.selectOne(any(), anyBoolean())).thenReturn(draft);
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        Submission r = service.submitAssignment(1L, 11L, "c", null);
        assertEquals("kept.png", r.getFileUrls());
    }

    // ===== saveDraft =====

    @Test
    @DisplayName("saveDraft 新建草稿走插入")
    void saveDraft_new() {
        Submission r = service.saveDraft(1L, 11L, "草稿", List.of("a.png"));
        assertEquals("DRAFT", r.getStatus());
        verify(submissionMapper).insert(any(Submission.class));
    }

    @Test
    @DisplayName("saveDraft 已存在草稿走更新")
    void saveDraft_existing() {
        Submission draft = new Submission();
        draft.setId(9L);
        when(submissionMapper.selectOne(any(), anyBoolean())).thenReturn(draft);
        Submission r = service.saveDraft(1L, 11L, "草稿", null);
        assertEquals(9L, r.getId());
        verify(submissionMapper).updateById(any(Submission.class));
    }

    // ===== resubmit =====

    @Test
    @DisplayName("resubmit 记录不存在抛异常")
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
    @DisplayName("resubmit 任务为 null 不计算逾期")
    void resubmit_taskNull() {
        submission.setStatus("RETURNED");
        submission.setAttemptNumber(1);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(taskService.getById(anyLong())).thenReturn(null);
        Submission r = service.resubmit(1L, "c", List.of("x.png"));
        assertEquals("RESUBMITTED", r.getStatus());
        assertFalse(r.getIsLate());
    }

    @Test
    @DisplayName("resubmit 正常带逾期扣分")
    void resubmit_late() {
        submission.setStatus("SUBMITTED");
        submission.setAttemptNumber(1);
        Task t = new Task();
        t.setDueDate(LocalDateTime.now().minusDays(2)); t.setMaxScore(new BigDecimal("100"));
        t.setLatePenaltyPerDay(new BigDecimal("10"));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(taskService.getById(anyLong())).thenReturn(t);
        Submission r = service.resubmit(1L, "c", null);
        assertTrue(r.getIsLate());
    }

    // ===== returnSubmission =====

    @Test
    @DisplayName("returnSubmission 已评分不可退回")
    void return_graded() {
        submission.setStatus("GRADED");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.returnSubmission(1L, "原因"));
    }

    @Test
    @DisplayName("returnSubmission 原因缺省兜底")
    void return_noReason() {
        submission.setStatus("SUBMITTED");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission r = service.returnSubmission(1L, null);
        assertEquals("RETURNED", r.getStatus());
        assertEquals("未填写退回原因", r.getFeedback());
    }

    // ===== gradeSubmission =====

    @Test
    @DisplayName("gradeSubmission 逾期扣分后不为负")
    void grade_lateFloorZero() {
        submission.setIsLate(true); submission.setLatePenalty(new BigDecimal("50"));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission r = service.gradeSubmission(1L, new BigDecimal("30"), "fb", 2L);
        assertEquals(0, r.getScore().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("gradeSubmission 计算百分比")
    void grade_percentage() {
        submission.setIsLate(false);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(taskService.getById(anyLong())).thenReturn(task);
        Submission r = service.gradeSubmission(1L, new BigDecimal("80"), "fb", 2L);
        assertEquals(0, r.getPercentage().compareTo(new BigDecimal("80.00")));
    }

    @Test
    @DisplayName("gradeSubmission 通知学生失败被捕获")
    void grade_notifyFails() {
        submission.setIsLate(false);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(taskService.getById(anyLong())).thenReturn(task);
        doThrow(new RuntimeException("notify err")).when(notificationService).onEvent(anyString(), any());
        Submission r = service.gradeSubmission(1L, new BigDecimal("80"), "fb", 2L);
        assertNotNull(r);
    }

    // ===== gradeSubmissionByTeacher =====

    @Test
    @DisplayName("gradeSubmissionByTeacher 不属于该作业返回 403")
    void gradeTeacher_mismatch() {
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () ->
                service.gradeSubmissionByTeacher(999L, 1L, submission, 2L));
    }

    // ===== aiGradeSubmission =====

    @Test
    @DisplayName("aiGradeSubmission 内容为空抛异常")
    void aiGrade_empty() {
        submission.setContent("");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.aiGradeSubmission(1L));
    }

    @Test
    @DisplayName("aiGradeSubmission AI 调用失败兜底")
    void aiGrade_aiFails() {
        submission.setContent("解答");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(aiService.generateContent(anyString(), anyString())).thenThrow(new RuntimeException("ai down"));
        Submission r = service.aiGradeSubmission(1L);
        assertTrue(r.getAiFeedback().startsWith("AI评分失败"));
    }

    @Test
    @DisplayName("aiGradeSubmission JSON 解析失败用原始文本")
    void aiGrade_parseFails() {
        submission.setContent("解答");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(aiService.generateContent(anyString(), anyString())).thenReturn("not a json");
        Submission r = service.aiGradeSubmission(1L);
        assertEquals("not a json", r.getAiFeedback());
    }

    // ===== calculateLatePenalty =====

    @Test
    @DisplayName("calculateLatePenalty 已有 penalty 直接返回")
    void latePenalty_existing() {
        submission.setLatePenalty(new BigDecimal("12.34"));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertEquals(0, submission.getLatePenalty().compareTo(service.calculateLatePenalty(1L)));
    }

    @Test
    @DisplayName("calculateLatePenalty 缺失字段返回零")
    void latePenalty_missingFields() {
        submission.setLatePenalty(null); submission.setTaskId(1L);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(taskService.getById(anyLong())).thenReturn(null);
        assertEquals(0, BigDecimal.ZERO.compareTo(service.calculateLatePenalty(1L)));
    }

    @Test
    @DisplayName("calculateLatePenalty 重新计算逾期扣分")
    void latePenalty_recompute() {
        submission.setLatePenalty(null); submission.setTaskId(1L);
        submission.setSubmittedAt(LocalDateTime.now().minusDays(2));
        Task t = new Task(); t.setDueDate(LocalDateTime.now().minusDays(5));
        t.setMaxScore(new BigDecimal("100")); t.setLatePenaltyPerDay(new BigDecimal("10"));
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(taskService.getById(anyLong())).thenReturn(t);
        BigDecimal p = service.calculateLatePenalty(1L);
        assertTrue(p.compareTo(BigDecimal.ZERO) > 0);
    }

    // ===== incrementViewCount =====

    @Test
    @DisplayName("incrementViewCount 自增")
    void viewCount_ok() {
        submission.setViewCount(5);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        service.incrementViewCount(1L);
        assertEquals(6, submission.getViewCount());
    }

    // ===== triggerPlagiarismCheck =====

    @Test
    @DisplayName("triggerPlagiarismCheck 内容为空抛异常")
    void plag_contentEmpty() {
        submission.setContent("");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        assertThrows(BusinessException.class, () -> service.triggerPlagiarismCheck(1L));
    }

    @Test
    @DisplayName("triggerPlagiarismCheck 高相似度 HIGH_RISK")
    void plag_highRisk() {
        submission.setContent("解答");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission other = new Submission(); other.setContent("其它");
        when(submissionMapper.selectList(any())).thenReturn(List.of(other));
        when(aiService.checkPlagiarism(anyString(), anyString())).thenReturn("85");
        PlagiarismCheck c = service.triggerPlagiarismCheck(1L);
        assertEquals("HIGH_RISK", c.getStatus());
    }

    @Test
    @DisplayName("triggerPlagiarismCheck 相似度解析失败降级")
    void plag_parseFail() {
        submission.setContent("解答");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission other = new Submission(); other.setContent("其它");
        when(submissionMapper.selectList(any())).thenReturn(List.of(other));
        when(aiService.checkPlagiarism(anyString(), anyString())).thenReturn("abc");
        PlagiarismCheck c = service.triggerPlagiarismCheck(1L);
        assertEquals("LOW_RISK", c.getStatus());
    }

    @Test
    @DisplayName("triggerPlagiarismCheck AI 调用异常标记 ERROR")
    void plag_aiError() {
        submission.setContent("解答");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        Submission other = new Submission(); other.setContent("其它");
        when(submissionMapper.selectList(any())).thenReturn(List.of(other));
        when(aiService.checkPlagiarism(anyString(), anyString())).thenThrow(new RuntimeException("ai err"));
        PlagiarismCheck c = service.triggerPlagiarismCheck(1L);
        assertEquals("ERROR", c.getStatus());
    }

    // ===== getPlagiarismSummary 风险等级 =====

    @Test
    @DisplayName("getPlagiarismSummary 无查重记录")
    void summary_noCheck() {
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getPlagiarismSummary(1L);
        assertEquals(false, r.get("hasCheck"));
    }

    @Test
    @DisplayName("getPlagiarismSummary 各风险等级")
    void summary_riskLevels() {
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        for (double s : new double[]{0.8, 0.5, 0.3, 0.1}) {
            PlagiarismCheck c = new PlagiarismCheck();
            c.setId(1L); c.setSimilarityScore(BigDecimal.valueOf(s));
            when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(List.of(c));
            Map<String, Object> r = service.getPlagiarismSummary(1L);
            assertNotNull(r.get("riskLevel"));
        }
        PlagiarismCheck c = new PlagiarismCheck(); c.setId(1L); c.setSimilarityScore(null);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(List.of(c));
        Map<String, Object> r = service.getPlagiarismSummary(1L);
        assertEquals("UNKNOWN", r.get("riskLevel"));
    }

    // ===== getSubmissionDetail =====

    @Test
    @DisplayName("getSubmissionDetail 含 submissionData 扩展字段")
    void detail_withData() {
        submission.setSubmissionData("{\"reviewCount\":3,\"returnReason\":\"重复\"}");
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getSubmissionDetail(1L);
        assertEquals(3, r.get("reviewCount"));
        assertEquals("重复", r.get("returnReason"));
    }

    @Test
    @DisplayName("getSubmissionDetail submissionData 为空 reviewCount 为 0")
    void detail_noData() {
        submission.setSubmissionData(null);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getSubmissionDetail(1L);
        assertEquals(0, r.get("reviewCount"));
    }

    @Test
    @DisplayName("getSubmissionDetail 查重摘要异常被捕获")
    void detail_plagiarismFails() {
        submission.setSubmissionData(null);
        when(submissionMapper.selectById(anyLong())).thenReturn(submission);
        when(plagiarismCheckService.list(any(Wrapper.class))).thenThrow(new RuntimeException("db err"));
        Map<String, Object> r = service.getSubmissionDetail(1L);
        assertNotNull(r.get("id"));
    }

    // ===== 私有 JSON 辅助（反射）=====

    @Test
    @DisplayName("JSON 辅助：容错与类型解析（迁移至 SubmissionJsonSupport 后）")
    void privateJsonHelpers() throws Exception {
        SubmissionJsonSupport support = new SubmissionJsonSupport(realObjectMapper);
        Method parse = SubmissionJsonSupport.class.getDeclaredMethod("parseData", String.class);
        parse.setAccessible(true);
        assertEquals(true, ((Map<?, ?>) parse.invoke(support, (Object) null)).isEmpty());
        assertEquals(true, ((Map<?, ?>) parse.invoke(support, "")).isEmpty());
        // 非法 JSON -> 容错返回空 Map（触发 IOException 分支）
        assertEquals(true, ((Map<?, ?>) parse.invoke(support, "{bad json")).isEmpty());

        Method serialize = SubmissionJsonSupport.class.getDeclaredMethod("serializeData", Map.class);
        serialize.setAccessible(true);
        assertEquals("{}", serialize.invoke(support, new LinkedHashMap<>()));
        Map<String, Object> m = new LinkedHashMap<>(); m.put("a", 1);
        assertTrue(((String) serialize.invoke(support, m)).contains("\"a\""));

        Method upd = SubmissionJsonSupport.class.getDeclaredMethod("updateJsonField", String.class, String.class, String.class);
        upd.setAccessible(true);
        String json = (String) upd.invoke(support, "{}", "k", "v");
        assertTrue(json.contains("\"k\""));

        Method extI = SubmissionJsonSupport.class.getDeclaredMethod("extractIntFromJson", String.class, String.class);
        extI.setAccessible(true);
        assertEquals(0, extI.invoke(support, "{}", "n"));
        assertEquals(7, extI.invoke(support, "{\"n\":7}", "n"));
        assertEquals(5, extI.invoke(support, "{\"n\":\"5\"}", "n"));
        assertEquals(0, extI.invoke(support, "{\"n\":\"xx\"}", "n")); // NumberFormatException 分支

        Method extS = SubmissionJsonSupport.class.getDeclaredMethod("extractStringFromJson", String.class, String.class);
        extS.setAccessible(true);
        assertNull(extS.invoke(support, "{}", "s"));
        assertEquals("v", extS.invoke(support, "{\"s\":\"v\"}", "s"));
    }
}
