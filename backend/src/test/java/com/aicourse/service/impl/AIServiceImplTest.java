package com.aicourse.service.impl;

import com.aicourse.service.PromptGuard;
import com.aicourse.service.RAGService;
import com.aicourse.llm.LLMProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class AIServiceImplTest {

    @Mock private LLMProvider llmProvider;
    @Mock private RAGService ragService;
    // 使用真实 PromptGuard（无外部依赖），既满足构造器注入又验证真实护栏行为
    @Spy private PromptGuard promptGuard = new PromptGuard();
    // 真实 MeterRegistry（@Spy 包真实实例），保证 Counter.builder().register() 返回有效 Counter
    @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks private AIServiceImpl aiService;

    @BeforeEach
    void setUp() {
        // AIServiceImpl 通过构造函数注入 LLMProvider 与 RAGService；
        // 桩化 complete 返回非空文本，避免 chat 等方法在 LLM 未配置时返回 null。
        // 仅部分测试方法会真正调用 LLMProvider，故使用 lenient 避免严格桩检查报 UnnecessaryStubbing。
        lenient().when(llmProvider.complete(any(), any())).thenReturn("测试AI回复");
    }

    @Test
    void testRagAsk_RAGServiceAvailable_UseRAG() {
        // Given
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

        // When
        String result = aiService.ragAsk("什么是机器学习？", 1L);

        // Then
        assertNotNull(result);
        // RAG不可用时，ragAsk 仍会先尝试 ragService.ask（返回 null），随后降级为纯LLM调用
        verify(ragService).ask(anyString(), anyLong(), anyInt());
    }

    @Test
    void testRagAsk_RAGThrowsException_FallbackToLLM() {
        // Given
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