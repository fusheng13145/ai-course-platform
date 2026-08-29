package com.aicourse.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三个具体供应商（DeepSeek / OpenAI / Qwen）构造与基础方法的覆盖。
 * 仅验证构造（含 log.debug）与 name() / isConfigured()，避免真实 HTTP 调用。
 */
class LlmProviderSubclassesTest {

    @Test
    void deepSeekProvider() {
        DeepSeekProvider p = new DeepSeekProvider("sk", "http://localhost/v1", "deepseek-chat", 5, 60, 2);
        assertEquals("deepseek", p.name());
        assertTrue(p.isConfigured());

        DeepSeekProvider blank = new DeepSeekProvider("", "http://localhost/v1", "deepseek-chat", 5, 60, 2);
        assertFalse(blank.isConfigured());
    }

    @Test
    void openAiProvider() {
        OpenAiProvider p = new OpenAiProvider("sk", "http://api.openai.com/v1", "gpt-4o-mini", 5, 60, 2);
        assertEquals("openai", p.name());
        assertTrue(p.isConfigured());
    }

    @Test
    void qwenProvider() {
        QwenProvider p = new QwenProvider("sk", "http://dashscope/v1", "qwen-plus", 5, 60, 2);
        assertEquals("qwen", p.name());
        assertTrue(p.isConfigured());
    }
}
