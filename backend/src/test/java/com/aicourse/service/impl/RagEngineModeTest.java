package com.aicourse.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 引擎模式解析：Python 优先（默认），Java 仅备选，老配置（仅 rag.enabled）不回退。
 */
class RagEngineModeTest {

    @Test
    @DisplayName("默认/显式 python → 使用 Python 引擎")
    void pythonMode() {
        assertTrue(RAGServiceImpl.resolveUsePython("python", false));
        assertTrue(RAGServiceImpl.resolveUsePython(" Python ", false));
        assertTrue(RAGServiceImpl.resolveUsePython("PYTHON", true));
    }

    @Test
    @DisplayName("provider=java → 强制纯 LLM，即使 enabled=true")
    void javaModeForcesDisabled() {
        assertFalse(RAGServiceImpl.resolveUsePython("java", true));
        assertFalse(RAGServiceImpl.resolveUsePython(" JAVA ", true));
    }

    @Test
    @DisplayName("provider 为空 → 沿用 rag.enabled（老配置兼容）")
    void blankFallsBackToEnabled() {
        assertTrue(RAGServiceImpl.resolveUsePython("", true));
        assertTrue(RAGServiceImpl.resolveUsePython(null, true));
        assertFalse(RAGServiceImpl.resolveUsePython("", false));
    }

    @Test
    @DisplayName("无法识别的模式 → 同样回落到 rag.enabled")
    void unknownFallsBackToEnabled() {
        assertTrue(RAGServiceImpl.resolveUsePython("ollama", true));
        assertFalse(RAGServiceImpl.resolveUsePython("ollama", false));
    }
}
