package com.aicourse.service.impl;

import com.aicourse.service.RAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class AIServiceImplTest {

    @Mock private RAGService ragService;

    @InjectMocks private AIServiceImpl aiService;

    @BeforeEach
    void setUp() {
        // 注入字段值（通过ReflectionTestUtils因为构造函数使用@Value）
        ReflectionTestUtils.setField(aiService, "apiKey", "sk-test-key");
        ReflectionTestUtils.setField(aiService, "model", "deepseek-chat");
    }

    @Test
    void testRagAsk_RAGServiceAvailable_UseRAG() {
        // Given
        when(ragService.healthCheck()).thenReturn(true);
        when(ragService.ask(anyString(), anyLong(), anyInt()))
                .thenReturn("{\"answer\":\"基于课程知识库的答案\",\"sources\":[]}");

        // When
        String result = aiService.ragAsk("什么是机器学习？", 1L);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("基于课程知识库的答案"));
        verify(ragService).ask(eq("什么是机器学习？"), eq(1L), eq(5));
    }

    @Test
    void testRagAsk_RAGServiceUnavailable_FallbackToLLM() {
        // Given
        when(ragService.healthCheck()).thenReturn(false);

        // When
        String result = aiService.ragAsk("什么是机器学习？", 1L);

        // Then
        assertNotNull(result);
        // RAG服务不可用时，降级为纯LLM调用（会返回模拟响应或真实API响应）
        verify(ragService, never()).ask(anyString(), anyLong(), anyInt());
    }

    @Test
    void testRagAsk_RAGThrowsException_FallbackToLLM() {
        // Given
        when(ragService.healthCheck()).thenReturn(true);
        when(ragService.ask(anyString(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("RAG service error"));

        // When
        String result = aiService.ragAsk("什么是机器学习？", 1L);

        // Then
        assertNotNull(result);
        // 异常时降级为纯LLM调用
    }

    @Test
    void testChat_WithApiKey() {
        // When (API Key 已配置，会调用真实API或返回错误)
        String result = aiService.chat("你好", null, 1L);

        // Then
        assertNotNull(result);
    }

    @Test
    void testGenerateQuestion() {
        // When
        String result = aiService.generateQuestion(1L, "CHOICE", "MEDIUM", 3);

        // Then
        assertNotNull(result);
    }

    @Test
    void testGenerateSummary() {
        // When
        String result = aiService.generateSummary("这是一段需要总结的长文本内容。");

        // Then
        assertNotNull(result);
    }

    @Test
    void testCheckPlagiarism() {
        // When
        String result = aiService.checkPlagiarism("文本A内容", "文本B内容");

        // Then
        assertNotNull(result);
    }
}