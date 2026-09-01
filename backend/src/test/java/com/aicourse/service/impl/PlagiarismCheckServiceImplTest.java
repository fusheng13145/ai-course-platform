package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.PlagiarismMatchService;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 查重检测服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class PlagiarismCheckServiceImplTest {

    @Mock private SubmissionService submissionService;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private PlagiarismMatchService plagiarismMatchService;
    @Mock private com.aicourse.mapper.PlagiarismCheckMapper plagiarismCheckMapper;

    @InjectMocks private PlagiarismCheckServiceImpl plagiarismCheckService;

    private Submission testSubmission;
    private PlagiarismCheck testCheck;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(plagiarismCheckService, "baseMapper", plagiarismCheckMapper);

        testSubmission = new Submission();
        testSubmission.setId(1L);
        testSubmission.setTaskId(10L);
        testSubmission.setStudentId(100L);
        testSubmission.setContent("machine learning deep learning neural network data science");

        testCheck = new PlagiarismCheck();
        testCheck.setId(1L);
        testCheck.setSubmissionId(1L);
        testCheck.setTaskId(10L);
        testCheck.setStudentId(100L);
        testCheck.setCheckType("TEXT");
        testCheck.setStatus("PENDING");
        testCheck.setSimilarityScore(BigDecimal.ZERO);
        testCheck.setHighSimilarityCount(0);
    }

    // ==================== createCheck ====================

    @Test
    @DisplayName("创建查重任务 - 成功创建")
    void testCreateCheck_Success() {
        when(submissionService.getById(1L)).thenReturn(testSubmission);
        when(plagiarismCheckMapper.insert(any(PlagiarismCheck.class))).thenReturn(1);

        PlagiarismCheck result = plagiarismCheckService.createCheck(1L, "TEXT");

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals("TEXT", result.getCheckType());
        assertEquals(1L, result.getSubmissionId());
        verify(plagiarismCheckMapper).insert(any(PlagiarismCheck.class));
    }

    @Test
    @DisplayName("创建查重任务 - 提交内容为空抛出异常")
    void testCreateCheck_EmptyContent() {
        testSubmission.setContent("");
        when(submissionService.getById(1L)).thenReturn(testSubmission);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> plagiarismCheckService.createCheck(1L, "TEXT"));
        assertEquals("提交内容为空，无法进行查重", exception.getMessage());
    }

    @Test
    @DisplayName("创建查重任务 - 提交记录不存在抛出异常")
    void testCreateCheck_SubmissionNotFound() {
        when(submissionService.getById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> plagiarismCheckService.createCheck(999L, "TEXT"));
        assertEquals("提交记录不存在", exception.getMessage());
    }

    // ==================== executeCheck ====================

    @Test
    @DisplayName("执行查重 - Jaccard相似度计算与高相似度检测")
    void testExecuteCheck_HighSimilarity() {
        when(plagiarismCheckMapper.selectById(1L)).thenReturn(testCheck);
        when(plagiarismCheckMapper.updateById(any(PlagiarismCheck.class))).thenReturn(1);
        when(submissionService.getById(1L)).thenReturn(testSubmission);

        Submission otherSubmission = new Submission();
        otherSubmission.setId(2L);
        otherSubmission.setStudentId(200L);
        otherSubmission.setContent("machine learning deep learning neural network artificial intelligence");

        when(submissionMapper.selectList(any())).thenReturn(List.of(otherSubmission));
        doNothing().when(plagiarismMatchService).batchSaveMatches(anyLong(), anyList());

        assertDoesNotThrow(() -> plagiarismCheckService.executeCheck(1L));

        verify(plagiarismCheckMapper, atLeast(2)).updateById(any(PlagiarismCheck.class));
        verify(plagiarismMatchService).batchSaveMatches(anyLong(), anyList());
    }

    @Test
    @DisplayName("执行查重 - 无相似度")
    void testExecuteCheck_NoSimilarity() {
        when(plagiarismCheckMapper.selectById(1L)).thenReturn(testCheck);
        when(plagiarismCheckMapper.updateById(any(PlagiarismCheck.class))).thenReturn(1);
        when(submissionService.getById(1L)).thenReturn(testSubmission);

        Submission otherSubmission = new Submission();
        otherSubmission.setId(2L);
        otherSubmission.setStudentId(200L);
        otherSubmission.setContent("quantum physics relativity theory black hole cosmology");

        when(submissionMapper.selectList(any())).thenReturn(List.of(otherSubmission));

        assertDoesNotThrow(() -> plagiarismCheckService.executeCheck(1L));

        verify(plagiarismMatchService, never()).batchSaveMatches(anyLong(), anyList());
    }

    @Test
    @DisplayName("执行查重 - 查重任务不存在抛出异常")
    void testExecuteCheck_NotFound() {
        when(plagiarismCheckMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> plagiarismCheckService.executeCheck(999L));
    }

    // ==================== getCheckResult ====================

    @Test
    @DisplayName("获取查重结果 - 包含匹配明细")
    void testGetCheckResult_WithMatches() {
        testCheck.setStatus("COMPLETED");
        testCheck.setSimilarityScore(new BigDecimal("0.4500"));
        testCheck.setHighSimilarityCount(2);
        testCheck.setResultSummary("查重结果摘要");
        testCheck.setCheckDetail("{\"algorithmVersion\":\"Jaccard-CJKBigram-v2.0\"}");

        when(plagiarismCheckMapper.selectById(1L)).thenReturn(testCheck);

        PlagiarismMatch match = new PlagiarismMatch();
        match.setId(1L);
        match.setSimilarity(new BigDecimal("0.4500"));

        when(plagiarismMatchService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(match));

        com.aicourse.vo.PlagiarismCheckResultVO result = plagiarismCheckService.getCheckResult(1L);

        assertNotNull(result);
        assertEquals(testCheck, result.getCheckInfo());
        assertEquals(testCheck.getSimilarityScore(), result.getSimilarityScore());
        assertEquals(2, result.getHighSimilarityCount());
        assertNotNull(result.getMatches());
        assertEquals("Jaccard-CJKBigram-v2.0", result.getAlgorithmVersion());
    }

    @Test
    @DisplayName("获取查重结果 - 任务不存在抛出异常")
    void testGetCheckResult_NotFound() {
        when(plagiarismCheckMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> plagiarismCheckService.getCheckResult(999L));
    }

    // ==================== getPlagiarismStats ====================

    @Test
    @DisplayName("查重统计 - 统计所有提交的查重结果")
    void testGetSubmissionChecks() {
        PlagiarismCheck check1 = new PlagiarismCheck();
        check1.setId(1L);
        check1.setStatus("COMPLETED");

        PlagiarismCheck check2 = new PlagiarismCheck();
        check2.setId(2L);
        check2.setStatus("FAILED");

        when(plagiarismCheckMapper.selectList(any())).thenReturn(List.of(check1, check2));

        List<PlagiarismCheck> result = plagiarismCheckService.getSubmissionChecks(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
    }
}