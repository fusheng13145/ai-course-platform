package com.aicourse.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Ollama 联调测试（默认跳过，不污染 CI）。
 *
 * <p>本地验证方式：先 `ollama serve` 且已 `ollama pull deepseek-r1:8b`，再执行
 * <pre>OLLAMA_LIVE_TEST=true mvnw.cmd -o -f backend\pom.xml test -Dtest=OllamaProviderLiveTest</pre>
 * （PowerShell 用 {@code $env:OLLAMA_LIVE_TEST='true'}）。</p>
 *
 * <p>断言只校验「正式答案非空且不含思考内容」，不断言具体措辞，避免模型版本差异导致误报。</p>
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_TEST", matches = "true")
class OllamaProviderLiveTest {

    @Test
    void complete_againstLocalOllama_returnsAnswerWithoutThinking() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "deepseek-r1:8b");
        OllamaProvider provider = new OllamaProvider(baseUrl, model, "", 5, 180, 0);

        String answer = provider.complete(
                List.of(Map.of("role", "user", "content", "Answer in one short English sentence: what is 2+2?")),
                0.2);

        assertNotNull(answer, "Ollama 未返回正式答案");
        assertTrue(!answer.isBlank(), "Ollama 返回空答案");
        assertFalse(answer.contains("<think"), "思考标签泄漏进正式答案");
        assertFalse(answer.contains("</think"), "思考标签泄漏进正式答案");
        assertFalse(answer.contains("Thinking Process"), "思考过程泄漏进正式答案");
    }
}
