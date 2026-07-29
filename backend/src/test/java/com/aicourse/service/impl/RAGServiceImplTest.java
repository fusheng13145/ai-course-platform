package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * RAG 服务单元测试
 * 验证「基于知识库 + 抑制幻觉」核心逻辑：TF-IDF 中文召回排序、检索态问答输出与来源标注、文档增删改查往返。
 * 离线可跑：mock KnowledgePointService 提供固定语料、mock LLMProvider 控制是否配置。
 */
@ExtendWith(MockitoExtension.class)
class RAGServiceImplTest {

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private LLMProvider llmProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RAGServiceImpl service;

    private List<KnowledgePoint> corpus;

    @BeforeEach
    void setUp() {
        service = new RAGServiceImpl(knowledgePointService, llmProvider, objectMapper, 5, 1.5, 0.75);

        KnowledgePoint kp1 = new KnowledgePoint();
        kp1.setId(1L); kp1.setName("反向传播");
        kp1.setDescription("反向传播是神经网络训练中的核心算法，用于计算梯度并更新权重。");
        kp1.setCourseId(1L); kp1.setPointType("CONCEPT");

        KnowledgePoint kp2 = new KnowledgePoint();
        kp2.setId(2L); kp2.setName("梯度下降");
        kp2.setDescription("梯度下降是一种优化算法，通过沿梯度反方向更新参数来最小化损失函数。");
        kp2.setCourseId(1L); kp2.setPointType("CONCEPT");

        KnowledgePoint kp3 = new KnowledgePoint();
        kp3.setId(3L); kp3.setName("卷积神经网络");
        kp3.setDescription("CNN 使用卷积层提取图像特征。");
        kp3.setCourseId(1L); kp3.setPointType("CONCEPT");

        corpus = new ArrayList<>(List.of(kp1, kp2, kp3));

        // loadCorpus(courseId) -> list(Wrapper)；用 (Wrapper<KnowledgePoint>) any() 消除 IService.list 重载歧义
        lenient().when(knowledgePointService.list((Wrapper<KnowledgePoint>) any())).thenReturn(corpus);
    }

    @Test
    void retrieve_shouldRankRelevantPointFirst() throws Exception {
        // 查询“梯度下降优化算法”应召回相关知识点，且“梯度下降”排在最前、相似度 > 0
        // （知识库中含标点的描述也能被正确分词：修正前此类描述词频恒为空、召回恒为 0）
        String res = service.retrieve("梯度下降优化算法", 1L, 3);
        Map<?, ?> map = objectMapper.readValue(res, Map.class);
        assertTrue((int) map.get("count") >= 1);
        List<?> results = (List<?>) map.get("results");
        assertFalse(results.isEmpty());
        Map<?, ?> top = (Map<?, ?>) results.get(0);
        assertEquals("梯度下降", top.get("name"));
        assertTrue(Double.parseDouble(String.valueOf(top.get("score"))) > 0);
    }

    @Test
    void ask_withoutLLM_shouldReturnRetrievalOnlyAndCiteSource() throws Exception {
        // 未配置大模型 -> 检索态，引用知识库来源，抑制幻觉
        when(llmProvider.isConfigured()).thenReturn(false);

        String res = service.ask("什么是反向传播", 1L, 3);
        Map<?, ?> map = objectMapper.readValue(res, Map.class);
        assertEquals("retrieval-only", map.get("model"));
        assertNotNull(map.get("answer"));
        assertTrue(((String) map.get("answer")).length() > 0);

        List<?> sources = (List<?>) map.get("sources");
        assertFalse(sources.isEmpty());
        Map<?, ?> src = (Map<?, ?>) sources.get(0);
        assertEquals("反向传播", src.get("name"));
    }

    @Test
    void ask_withLLM_shouldFuseAndCiteSource() throws Exception {
        // 已配置大模型 -> 融合生成，且仍标注来源
        when(llmProvider.isConfigured()).thenReturn(true);
        when(llmProvider.name()).thenReturn("mock");
        when(llmProvider.complete(any(), any()))
                .thenReturn("反向传播用于计算梯度并更新网络权重。");

        String res = service.ask("什么是反向传播", 1L, 3);
        Map<?, ?> map = objectMapper.readValue(res, Map.class);
        assertEquals("mock", map.get("model"));
        assertTrue(((String) map.get("answer")).contains("梯度"));
        List<?> sources = (List<?>) map.get("sources");
        assertFalse(sources.isEmpty());
    }

    @Test
    void listDocuments_shouldReturnCorpus() throws Exception {
        String res = service.listDocuments(1L);
        Map<?, ?> map = objectMapper.readValue(res, Map.class);
        assertEquals("ok", map.get("status"));
        assertEquals(3, map.get("count"));
        List<?> documents = (List<?>) map.get("documents");
        assertEquals(3, documents.size());
    }

    @Test
    void addDocument_shouldPersistAsDocumentPoint() throws Exception {
        String res = service.addDocument(1L, "测试文档", "这是文档内容");
        Map<?, ?> map = objectMapper.readValue(res, Map.class);
        assertEquals("ok", map.get("status"));

        ArgumentCaptor<KnowledgePoint> captor = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(knowledgePointService).save(captor.capture());
        KnowledgePoint saved = captor.getValue();
        assertEquals(1L, saved.getCourseId());
        assertEquals("测试文档", saved.getName());
        assertEquals("这是文档内容", saved.getDescription());
        assertEquals("DOCUMENT", saved.getPointType());
    }

    @Test
    void deleteDocument_shouldRemoveById() throws Exception {
        String res = service.deleteDocument(42L);
        Map<?, ?> map = objectMapper.readValue(res, Map.class);
        assertEquals("ok", map.get("status"));
        assertEquals(42, map.get("deleted"));
        verify(knowledgePointService).removeById(eq(42L));
    }
}
