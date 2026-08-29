package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.PlagiarismCheckMapper;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.PlagiarismMatchService;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 针对 PlagiarismCheckServiceImpl 的指令覆盖率强化测试。
 * 覆盖 createCheck / executeCheck / getCheckResult / getHighSimilarityMatches /
 * getSubmissionChecks 的全部分支，以及私有辅助方法（相似度/分词/片段/类型/摘要/JSON）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlagiarismCheckServiceImplBoostTest {

    @Mock
    private PlagiarismCheckMapper plagiarismCheckMapper;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private PlagiarismMatchService plagiarismMatchService;
    @Mock
    private ObjectMapper mockObjectMapper; // 仅用于序列化失败分支

    private final ObjectMapper realObjectMapper = new ObjectMapper();
    private PlagiarismCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlagiarismCheckServiceImpl(plagiarismCheckMapper, submissionService,
                submissionMapper, plagiarismMatchService, realObjectMapper);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), PlagiarismCheck.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), PlagiarismMatch.class);
    }

    // ==================== createCheck ====================

    @Test
    @DisplayName("createCheck: 提交记录不存在抛异常")
    void createCheck_submissionNull() {
        when(submissionService.getById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createCheck(1L, "TEXT"));
        assertTrue(ex.getMessage().contains("提交记录不存在"));
    }

    @Test
    @DisplayName("createCheck: 提交内容为空抛异常")
    void createCheck_emptyContent() {
        Submission s = new Submission();
        s.setId(1L);
        s.setContent("");
        when(submissionService.getById(1L)).thenReturn(s);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createCheck(1L, "TEXT"));
        assertTrue(ex.getMessage().contains("提交内容为空"));
    }

    @Test
    @DisplayName("createCheck: 成功创建 PENDING 任务")
    void createCheck_success() {
        Submission s = new Submission();
        s.setId(1L);
        s.setTaskId(99L);
        s.setStudentId(7L);
        s.setContent("这是一段提交内容");
        when(submissionService.getById(1L)).thenReturn(s);

        PlagiarismCheck result = service.createCheck(1L, null);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals("TEXT", result.getCheckType());
        assertEquals(BigDecimal.ZERO, result.getSimilarityScore());
        assertEquals(0, result.getHighSimilarityCount());
        verify(plagiarismCheckMapper).insert(any(PlagiarismCheck.class));
    }

    // ==================== executeCheck ====================

    @Test
    @DisplayName("executeCheck: 查重任务不存在抛异常")
    void executeCheck_checkNotFound() {
        when(plagiarismCheckMapper.selectById(5L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.executeCheck(5L));
        assertTrue(ex.getMessage().contains("查重任务不存在"));
    }

    @Test
    @DisplayName("executeCheck: 当前提交内容为空 -> BusinessException 被捕获并置 FAILED")
    void executeCheck_submissionContentNull() {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(5L);
        check.setSubmissionId(10L);
        check.setTaskId(99L);
        when(plagiarismCheckMapper.selectById(5L)).thenReturn(check);

        Submission cur = new Submission();
        cur.setId(10L);
        cur.setContent(null);
        when(submissionService.getById(10L)).thenReturn(cur);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.executeCheck(5L));
        assertTrue(ex.getMessage().contains("无法获取提交内容"));

        ArgumentCaptor<PlagiarismCheck> cap = ArgumentCaptor.forClass(PlagiarismCheck.class);
        verify(plagiarismCheckMapper, times(2)).updateById(cap.capture());
        assertEquals("FAILED", cap.getAllValues().get(1).getStatus());
    }

    @Test
    @DisplayName("executeCheck: 其他异常 -> 置 FAILED 并抛出 INTERNAL_SERVER_ERROR")
    void executeCheck_genericException() {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(5L);
        check.setSubmissionId(10L);
        check.setTaskId(99L);
        when(plagiarismCheckMapper.selectById(5L)).thenReturn(check);

        Submission cur = new Submission();
        cur.setId(10L);
        cur.setContent("正常内容");
        when(submissionService.getById(10L)).thenReturn(cur);

        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenThrow(new RuntimeException("boom"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.executeCheck(5L));
        assertEquals(ApiCode.INTERNAL_SERVER_ERROR, ex.getCode());

        ArgumentCaptor<PlagiarismCheck> cap = ArgumentCaptor.forClass(PlagiarismCheck.class);
        verify(plagiarismCheckMapper, atLeast(2)).updateById(cap.capture());
        PlagiarismCheck failed = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertEquals("FAILED", failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("boom"));
    }

    @Test
    @DisplayName("executeCheck: 正常执行，包含高/低/空内容三类其它提交")
    void executeCheck_success_mixed() {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(5L);
        check.setSubmissionId(10L);
        check.setTaskId(99L);
        when(plagiarismCheckMapper.selectById(5L)).thenReturn(check);

        Submission cur = new Submission();
        cur.setId(10L);
        cur.setContent("人工智能 机器学习 深度学习");
        when(submissionService.getById(10L)).thenReturn(cur);

        Submission high = new Submission();
        high.setId(11L);
        high.setStudentId(111L);
        high.setContent("人工智能 机器学习 深度学习"); // 完全相同 -> 相似度 1.0

        Submission low = new Submission();
        low.setId(12L);
        low.setStudentId(222L);
        low.setContent("quantum physics relativity entropy"); // 完全不相交 -> 0

        Submission empty = new Submission();
        empty.setId(13L);
        empty.setStudentId(333L);
        empty.setContent(""); // 空内容 -> continue

        when(submissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(high, low, empty));

        service.executeCheck(5L);

        ArgumentCaptor<PlagiarismCheck> cap = ArgumentCaptor.forClass(PlagiarismCheck.class);
        verify(plagiarismCheckMapper, atLeast(2)).updateById(cap.capture());
        PlagiarismCheck completed = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(0, new BigDecimal("1.0").compareTo(completed.getSimilarityScore())); // 1.0000
        assertEquals(1, completed.getHighSimilarityCount());
        assertNotNull(completed.getCheckDetail());
        assertTrue(completed.getCheckDetail().contains("algorithmVersion"));

        verify(plagiarismMatchService).batchSaveMatches(eq(5L), anyList());
    }

    @Test
    @DisplayName("executeCheck: 无高相似度匹配则不调用 batchSaveMatches")
    void executeCheck_noHighSim() {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(5L);
        check.setSubmissionId(10L);
        check.setTaskId(99L);
        when(plagiarismCheckMapper.selectById(5L)).thenReturn(check);

        Submission cur = new Submission();
        cur.setId(10L);
        cur.setContent("人工智能 机器学习 深度学习");
        when(submissionService.getById(10L)).thenReturn(cur);

        Submission low = new Submission();
        low.setId(12L);
        low.setContent("quantum physics relativity entropy");
        when(submissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(low));

        service.executeCheck(5L);

        verify(plagiarismMatchService, never()).batchSaveMatches(any(), any());
        ArgumentCaptor<PlagiarismCheck> cap = ArgumentCaptor.forClass(PlagiarismCheck.class);
        verify(plagiarismCheckMapper, atLeast(1)).updateById(cap.capture());
        assertEquals("COMPLETED", cap.getAllValues().get(cap.getAllValues().size() - 1).getStatus());
    }

    // ==================== getCheckResult ====================

    @Test
    @DisplayName("getCheckResult: 任务不存在抛异常")
    void getCheckResult_notFound() {
        when(plagiarismCheckMapper.selectById(9L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getCheckResult(9L));
        assertTrue(ex.getMessage().contains("查重任务不存在"));
    }

    @Test
    @DisplayName("getCheckResult: 无 checkDetail 时返回基本信息")
    void getCheckResult_noDetail() {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(9L);
        check.setSimilarityScore(new BigDecimal("0.5"));
        check.setHighSimilarityCount(2);
        check.setResultSummary("summary");
        check.setStatus("COMPLETED");
        when(plagiarismCheckMapper.selectById(9L)).thenReturn(check);
        when(plagiarismMatchService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.getCheckResult(9L);
        assertEquals(check, result.get("checkInfo"));
        assertEquals(new BigDecimal("0.5"), result.get("similarityScore"));
        assertFalse(result.containsKey("algorithmVersion"));
    }

    @Test
    @DisplayName("getCheckResult: 含 checkDetail 时解析出 algorithmVersion")
    void getCheckResult_withDetail() {
        PlagiarismCheck check = new PlagiarismCheck();
        check.setId(9L);
        check.setCheckDetail("{\"algorithmVersion\":\"Jaccard-CJKBigram-v2.0\"}");
        when(plagiarismCheckMapper.selectById(9L)).thenReturn(check);

        PlagiarismMatch m = new PlagiarismMatch();
        m.setId(1L);
        m.setSimilarity(new BigDecimal("0.9"));
        when(plagiarismMatchService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(m));

        Map<String, Object> result = service.getCheckResult(9L);
        assertEquals("Jaccard-CJKBigram-v2.0", result.get("algorithmVersion"));
        assertEquals("{\"algorithmVersion\":\"Jaccard-CJKBigram-v2.0\"}", result.get("checkDetailRaw"));
        assertEquals(1, ((List<?>) result.get("matches")).size());
    }

    // ==================== getHighSimilarityMatches / getSubmissionChecks ====================

    @Test
    @DisplayName("getHighSimilarityMatches: 按阈值查询匹配明细")
    void getHighSimilarityMatches() {
        PlagiarismMatch m = new PlagiarismMatch();
        m.setId(1L);
        when(plagiarismMatchService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(m));
        List<PlagiarismMatch> r = service.getHighSimilarityMatches(5L, new BigDecimal("0.3"));
        assertEquals(1, r.size());
        verify(plagiarismMatchService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getSubmissionChecks: 查询某提交的全部查重历史")
    void getSubmissionChecks() {
        PlagiarismCheck c = new PlagiarismCheck();
        c.setId(1L);
        when(plagiarismCheckMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(c));
        List<PlagiarismCheck> r = service.getSubmissionChecks(10L);
        assertEquals(1, r.size());
        verify(plagiarismCheckMapper).selectList(any(LambdaQueryWrapper.class));
    }

    // ==================== 私有辅助方法（反射） ====================

    @Test
    @DisplayName("calculateSimilarity: 相同文本=1.0，不相交文本=0.0")
    void calculateSimilarity() throws Exception {
        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("calculateSimilarity", String.class, String.class);
        m.setAccessible(true);

        Map<String, Object> same = (Map<String, Object>) m.invoke(service, "人工智能 机器学习", "人工智能 机器学习");
        assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) same.get("similarity")));
        assertNotNull(same.get("originalFragment"));
        assertNotNull(same.get("matchedFragment"));

        Map<String, Object> diff = (Map<String, Object>) m.invoke(service, "人工智能 机器学习", "quantum physics");
        assertEquals(0, ((BigDecimal) diff.get("similarity")).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("tokenize: 覆盖 null/空/CJK/英文/混合")
    void tokenize() throws Exception {
        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("tokenize", String.class);
        m.setAccessible(true);

        assertTrue(((Set<?>) m.invoke(service, (Object) null)).isEmpty());
        assertTrue(((Set<?>) m.invoke(service, "")).isEmpty());

        Set<?> cjk = (Set<?>) m.invoke(service, "人工智能");
        assertTrue(cjk.contains("人工"));
        assertTrue(cjk.contains("工智"));
        assertTrue(cjk.contains("智能"));

        Set<?> eng = (Set<?>) m.invoke(service, "Hello World");
        assertTrue(eng.contains("hello"));
        assertTrue(eng.contains("world"));

        Set<?> mix = (Set<?>) m.invoke(service, "人工智能 Machine Learning 模型");
        assertTrue(mix.contains("人工"));
        assertTrue(mix.contains("machine"));
        assertTrue(mix.contains("learning"));
        assertTrue(mix.contains("模型"));
    }

    @Test
    @DisplayName("extractCommonFragment: 提取公共片段")
    void extractCommonFragment() throws Exception {
        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("extractCommonFragment", String.class, Set.class, Set.class);
        m.setAccessible(true);

        String full = "这是一段关于人工智能和机器学习的示例文本用于查重片段提取测试覆盖";
        Set<String> all = new HashSet<>(Arrays.asList("人工", "智能", "机器"));
        Set<String> common = new HashSet<>(Arrays.asList("人工", "机器"));
        String frag = (String) m.invoke(service, full, all, common);
        assertNotNull(frag);
        assertFalse(frag.isEmpty());

        // 无公共词 -> 空串
        String empty = (String) m.invoke(service, full, all, new HashSet<>());
        assertEquals("", empty);
    }

    @Test
    @DisplayName("determineMatchType: EXACT / PARAPHRASE / STRUCTURE")
    void determineMatchType() throws Exception {
        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("determineMatchType", BigDecimal.class);
        m.setAccessible(true);
        assertEquals("EXACT", m.invoke(service, new BigDecimal("0.8")));
        assertEquals("PARAPHRASE", m.invoke(service, new BigDecimal("0.5")));
        assertEquals("STRUCTURE", m.invoke(service, new BigDecimal("0.2")));
    }

    @Test
    @DisplayName("generateResultSummary: 有/无高相似度匹配两分支")
    void generateResultSummary() throws Exception {
        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("generateResultSummary", BigDecimal.class, int.class, int.class);
        m.setAccessible(true);
        String with = (String) m.invoke(service, new BigDecimal("0.5"), 3, 10);
        assertTrue(with.contains("3处高相似度匹配"));
        String without = (String) m.invoke(service, BigDecimal.ZERO, 0, 10);
        assertTrue(without.contains("未发现明显的高相似度匹配"));
    }

    @Test
    @DisplayName("buildCheckDetailJson: 正常序列化含 algorithmVersion")
    void buildCheckDetailJson_success() throws Exception {
        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("buildCheckDetailJson", List.class, BigDecimal.class, int.class, int.class);
        m.setAccessible(true);

        Map<String, Object> match = new HashMap<>();
        match.put("matchedSubmissionId", 11L);
        match.put("matchedSource", "学生ID:111的提交");
        match.put("similarity", new BigDecimal("0.95"));
        match.put("matchType", "EXACT");
        List<Map<String, Object>> matches = Collections.singletonList(match);

        String json = (String) m.invoke(service, matches, new BigDecimal("0.95"), 1, 5);
        assertTrue(json.contains("algorithmVersion"));
        assertTrue(json.contains("Jaccard-CJKBigram-v2.0"));
        assertTrue(json.contains("\"matchedSubmissionId\":11"));
    }

    @Test
    @DisplayName("buildCheckDetailJson: 序列化失败回退到安全默认 JSON")
    void buildCheckDetailJson_serializationFails() throws Exception {
        PlagiarismCheckServiceImpl failService = new PlagiarismCheckServiceImpl(
                plagiarismCheckMapper, submissionService, submissionMapper, plagiarismMatchService, mockObjectMapper);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), PlagiarismCheck.class);

        when(mockObjectMapper.createObjectNode()).thenThrow(new RuntimeException("serial fail"));

        Method m = PlagiarismCheckServiceImpl.class.getDeclaredMethod("buildCheckDetailJson", List.class, BigDecimal.class, int.class, int.class);
        m.setAccessible(true);
        String json = (String) m.invoke(failService, Collections.emptyList(), BigDecimal.ONE, 0, 0);
        assertTrue(json.contains("algorithmVersion"));
        assertTrue(json.contains("序列化失败"));
    }
}
