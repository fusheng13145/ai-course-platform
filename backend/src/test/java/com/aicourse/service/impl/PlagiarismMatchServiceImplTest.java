package com.aicourse.service.impl;

import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.PlagiarismMatchMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlagiarismMatchServiceImplTest {

    @Mock
    private PlagiarismMatchMapper matchMapper;

    private PlagiarismMatchServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PlagiarismMatch.class);
        // Spy 拦截 saveBatch（其依赖真实 MyBatis sqlSession，纯 Mock 无法执行）
        service = spy(new PlagiarismMatchServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", matchMapper);
        lenient().doReturn(true).when(service).saveBatch(any());
    }

    private Map<String, Object> matchData(Long subId, String src, BigDecimal sim,
                                          String orig, String matched, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("matchedSubmissionId", subId);
        m.put("matchedSource", src);
        m.put("similarity", sim);
        m.put("originalText", orig);
        m.put("matchedText", matched);
        m.put("matchType", type);
        return m;
    }

    // ===== batchSaveMatches =====
    @Test
    @DisplayName("batchSaveMatches null 安全")
    void batch_null() {
        service.batchSaveMatches(1L, null);
        verify(service, never()).saveBatch(any());
    }

    @Test
    @DisplayName("batchSaveMatches 空列表安全")
    void batch_empty() {
        service.batchSaveMatches(1L, Collections.emptyList());
        verify(service, never()).saveBatch(any());
    }

    @Test
    @DisplayName("batchSaveMatches 非空批量保存")
    void batch_some() {
        List<Map<String, Object>> list = List.of(
                matchData(10L, "srcA", new BigDecimal("0.85"), "orig1", "m1", "TEXT"),
                matchData(11L, "srcB", new BigDecimal("0.30"), "orig2", "m2", "CODE"));
        service.batchSaveMatches(1L, list);
        verify(service).saveBatch(any());
    }

    // ===== getComparisonView =====
    @Test
    @DisplayName("getComparisonView 不存在抛异常")
    void view_notFound() {
        when(matchMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getComparisonView(5L));
    }

    @Test
    @DisplayName("getComparisonView 高相似度")
    void view_highSimilarity() {
        PlagiarismMatch m = new PlagiarismMatch();
        m.setId(5L);
        m.setPlagiarismCheckId(1L);
        m.setMatchedSubmissionId(10L);
        m.setMatchedSource("src");
        m.setSimilarity(new BigDecimal("0.85"));
        m.setMatchType("TEXT");
        m.setOriginalText("o");
        m.setMatchedText("m");
        when(matchMapper.selectById(5L)).thenReturn(m);
        Map<String, Object> v = service.getComparisonView(5L);
        assertEquals(5L, v.get("matchId"));
        assertEquals(new BigDecimal("0.85"), v.get("similarity"));
        @SuppressWarnings("unchecked")
        Map<String, String> comp = (Map<String, String>) v.get("comparison");
        assertEquals("o", comp.get("originalText"));
        assertEquals("m", comp.get("matchedText"));
        assertEquals("高度疑似抄袭（相似度≥70%），建议重点审查", v.get("levelDescription"));
    }

    @Test
    @DisplayName("getComparisonView 相似度为 null")
    void view_nullSimilarity() {
        PlagiarismMatch m = new PlagiarismMatch();
        m.setId(5L);
        m.setSimilarity(null);
        when(matchMapper.selectById(5L)).thenReturn(m);
        Map<String, Object> v = service.getComparisonView(5L);
        assertEquals("未知", v.get("levelDescription"));
        @SuppressWarnings("unchecked")
        Map<String, String> comp = (Map<String, String>) v.get("comparison");
        assertEquals("", comp.get("originalText"));
    }

    @Test
    @DisplayName("getComparisonView 中/低相似度分支")
    void view_midLowSimilarity() {
        PlagiarismMatch m1 = new PlagiarismMatch();
        m1.setId(1L);
        m1.setSimilarity(new BigDecimal("0.5"));
        when(matchMapper.selectById(1L)).thenReturn(m1);
        assertEquals("中度疑似改写（相似度40%-70%），建议人工复核",
                service.getComparisonView(1L).get("levelDescription"));

        PlagiarismMatch m2 = new PlagiarismMatch();
        m2.setId(2L);
        m2.setSimilarity(new BigDecimal("0.3"));
        when(matchMapper.selectById(2L)).thenReturn(m2);
        assertEquals("轻度相似（相似度20%-40%），存在一定参考嫌疑",
                service.getComparisonView(2L).get("levelDescription"));

        PlagiarismMatch m3 = new PlagiarismMatch();
        m3.setId(3L);
        m3.setSimilarity(new BigDecimal("0.1"));
        when(matchMapper.selectById(3L)).thenReturn(m3);
        assertEquals("低度相似（相似度<20%），基本正常",
                service.getComparisonView(3L).get("levelDescription"));
    }
}
