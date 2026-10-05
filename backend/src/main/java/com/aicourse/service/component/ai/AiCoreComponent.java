package com.aicourse.service.component.ai;

import com.aicourse.common.JsonUtils;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.impl.PromptGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 执行核心组件：LLM 供应商调用出口、消息构建、PromptGuard 护栏与埋点。
 * <p>拆分自原 AIServiceImpl（2026-08-29），被对话/推荐/结构化/解析组件共享。</p>
 */
@Slf4j
@Component
public class AiCoreComponent {

    /** 未配置任何 LLM 供应商 Key 时返回的清晰业务提示（不再返回伪造 JSON） */
    public static final String NO_PROVIDER_MSG = "AI 服务暂未配置（请联系管理员配置大模型 API Key 后重试）。";
    public static final String SERVICE_ERROR_MSG = "AI 服务暂时不可用，请稍后再试。";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LLMProvider llmProvider;
    private final PromptGuard promptGuard;
    private final MeterRegistry meterRegistry;
    /** 按业务类型分桶的 AI 调用计数器缓存：key=类型，value=对应 Counter（首次访问时懒注册）。 */
    private final Map<String, Counter> aiCallCounters = new ConcurrentHashMap<>();
    /** 按业务类型分桶的 AI 输入 token 估算计数器缓存（与调用计数器对称，口径见 {@link #recordAiToken}）。 */
    private final Map<String, Counter> aiTokenCounters = new ConcurrentHashMap<>();
    /** 按业务类型分桶的 AI 调用失败计数器缓存（与调用计数器对称，口径见 {@link #recordAiFailure}）。 */
    private final Map<String, Counter> aiFailedCounters = new ConcurrentHashMap<>();

    public AiCoreComponent(LLMProvider llmProvider, PromptGuard promptGuard, MeterRegistry meterRegistry) {
        this.llmProvider = llmProvider;
        this.promptGuard = promptGuard;
        this.meterRegistry = meterRegistry;
    }

    /**
     * LLM 调用埋点：每次实际发起大模型请求时，按业务类型递增对应 Counter，
     * 使 Grafana 能按类型（chat/question/content/correct/plagiarism/rag/recommend/analyze/extract_graph）下钻统计。
     *
     * @param type 业务类型，取值见各公开方法的调用处
     */
    public void recordAiCall(String type) {
        aiCallCounters
                .computeIfAbsent(type, t -> Counter.builder("aicourse_ai_calls_total")
                        .tag("type", t)
                        .description("AI 大模型调用总次数（按业务类型分桶）")
                        .register(meterRegistry))
                .increment();
    }

    /**
     * AI 输入 token 估算埋点：按业务类型累加本次调用的输入 token 估算量。
     * <p>token 估算与目标供应商计费口径对齐项目既有约定（{@code AiQuotaService}）：
     * 中英文混合场景下保守按 {@code 1 token ≈ 1.5 字符} 折算，仅统计 prompt 输入规模，
     * 不含响应输出（响应长度在请求前不可知）；用于 Grafana 估算 AI 成本消耗趋势。</p>
     *
     * @param type   业务类型（与 {@link #recordAiCall} 一致）
     * @param tokens 输入 token 估算量（≥0）
     */
    public void recordAiToken(String type, long tokens) {
        if (tokens <= 0) {
            return;
        }
        aiTokenCounters
                .computeIfAbsent(type, t -> Counter.builder("aicourse_ai_tokens_estimate_total")
                        .tag("type", t)
                        .description("AI 大模型输入 token 估算总量（按业务类型分桶，1 token≈1.5 字符折算）")
                        .register(meterRegistry))
                .increment(tokens);
    }

    /**
     * AI 调用失败埋点：按业务类型累加失败次数。
     * <p>失败判定口径与 {@link #recordAiCall} 对齐（type 一致）：
     * LLM 供应商调用抛出异常，或返回 null（统一降级为 {@code SERVICE_ERROR_MSG}）均计一次失败。
     * 注意：未配置供应商（{@code NO_PROVIDER_MSG} 路径）不计入失败——那属于部署配置缺失，
     * 而非调用失败，避免污染失败率。与调用计数配合可计算各类型失败率，
     * 供 Prometheus 告警「AI 调用失败率突增」使用。</p>
     *
     * @param type 业务类型（与 {@link #recordAiCall} 一致）
     */
    public void recordAiFailure(String type) {
        aiFailedCounters
                .computeIfAbsent(type, t -> Counter.builder("aicourse_ai_calls_failed_total")
                        .tag("type", t)
                        .description("AI 大模型调用失败次数（按业务类型分桶，含异常与空响应降级）")
                        .register(meterRegistry))
                .increment();
    }

    /**
     * 拼接进 prompt 的文本片段兜底：null/空白一律换成给定缺省值，
     * 否则字面量 "null" 会被送进模型（语义污染），并按污染后的长度照常估算 token 计费。
     */
    public static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 依据消息列表的总字符数估算输入 token 数（1 token ≈ 1.5 字符）。
     */
    public long estimateInputTokens(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0L;
        }
        long chars = 0L;
        for (Map<String, String> m : messages) {
            if (m == null) {
                continue;
            }
            String content = m.get("content");
            if (content != null) {
                chars += content.length();
            }
        }
        return (long) (chars / 1.5);
    }

    /**
     * 直连出口：调用供应商后对输出统一做活动内容清洗（null 透传，交由上层判空）。
     */
    public String completeAndSanitize(List<Map<String, String>> messages, double temperature, String type) {
        recordAiCall(type);
        recordAiToken(type, estimateInputTokens(messages));
        try {
            return promptGuard.sanitizeOutput(llmProvider.complete(messages, temperature));
        } catch (Exception e) {
            log.error("AI相关操作失败: {}", e.getMessage(), e);
            recordAiFailure(type);
            throw e;
        }
    }

    /**
     * 统一调用入口：未配置供应商时返回清晰提示，调用失败返回通用错误文案；成功结果统一清洗后下发
     */
    public String callLlm(List<Map<String, String>> messages, Double temperature, String jsonSchema, String type) {
        if (!llmProvider.isConfigured()) {
            return NO_PROVIDER_MSG;
        }
        recordAiCall(type);
        long inputTokens = estimateInputTokens(messages);
        if (jsonSchema != null) {
            inputTokens += (long) (jsonSchema.length() / 1.5);
        }
        recordAiToken(type, inputTokens);
        try {
            String result = jsonSchema != null
                    ? llmProvider.completeJson(messages, temperature, jsonSchema)
                    : llmProvider.complete(messages, temperature);
            if (result == null) {
                recordAiFailure(type);
                return SERVICE_ERROR_MSG;
            }
            return promptGuard.sanitizeOutput(result);
        } catch (Exception e) {
            log.error("AI相关操作失败: {}", e.getMessage(), e);
            recordAiFailure(type);
            throw e;
        }
    }

    public List<Map<String, String>> buildMessages(String userMessage, String systemPrompt) {
        // 输入侧护栏：中和用户内容中的对话模板控制符与伪角色标记，防止从 user 角色「越狱」为系统指令
        String safeUser = promptGuard.neutralizeInjection(userMessage);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", safeUser)
            );
        }
        return List.of(Map.of("role", "user", "content", safeUser));
    }

    /**
     * 输入侧注入中和（供流式对话等场景在构造消息前使用）
     */
    public String neutralizeInjection(String text) {
        return promptGuard.neutralizeInjection(text);
    }

    /**
     * 流式 LLM 调用出口：委托供应商流式接口，逐 token 增量统一做输出清洗。
     */
    public Flux<String> stream(List<Map<String, String>> messages, double temperature) {
        return llmProvider.stream(messages, temperature)
                .map(promptGuard::sanitizeOutput);
    }

    /**
     * 对多轮消息历史逐条中和 user 角色内容（system/assistant 由服务端可信构造，不做改写）。
     */
    public List<Map<String, String>> neutralizeUserMessages(List<Map<String, String>> messages) {
        List<Map<String, String>> safe = new ArrayList<>(messages.size());
        for (Map<String, String> msg : messages) {
            if (msg == null) {
                continue;
            }
            String role = msg.get("role");
            String content = msg.get("content");
            if ("user".equalsIgnoreCase(role) && content != null) {
                safe.add(Map.of("role", role, "content", promptGuard.neutralizeInjection(content)));
            } else {
                safe.add(msg);
            }
        }
        return safe;
    }

    /**
     * 从 RAG 返回的 JSON（{@code {"answer":...,"sources":...}}）中提取纯文本答案；
     * 若非 JSON 或缺少 answer 字段，则原样返回（兼容历史格式）。
     */
    public String extractRagAnswer(String ragJson) {
        String json = JsonUtils.extractJsonObject(ragJson);
        if (json == null) {
            return ragJson;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node.has("answer") && node.get("answer").isTextual()
                    && !node.get("answer").asText().isBlank()) {
                return node.get("answer").asText();
            }
        } catch (Exception e) {
            log.debug("RAG 答案 JSON 解析失败，原样返回: {}", e.getMessage());
        }
        return ragJson;
    }

    /**
     * 判断当前是否有可用的 LLM 供应商（已配置 Key）
     */
    public boolean isProviderConfigured() {
        return llmProvider != null;
    }

    /**
     * 判断供应商是否已启用（非空且配置完成），供流式对话等需要实际调用供应商接口的路径使用
     */
    public boolean isProviderEnabled() {
        return llmProvider != null && llmProvider.isConfigured();
    }

    /**
     * 当供应商未配置 Key 时返回的统一提示
     */
    public String noProviderMessage() {
        return NO_PROVIDER_MSG;
    }
}
