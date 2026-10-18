package com.aicourse.service.component.ai;

import com.aicourse.common.JsonUtils;
import com.aicourse.exception.LLMUnavailableException;
import com.aicourse.llm.LLMProvider;
import com.aicourse.service.AiCallLogService;
import com.aicourse.service.impl.PromptGuard;
import com.aicourse.vo.QaSourceVO;
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
 *
 * <p><b>跨课程 AI 统计的落库点就在这里</b>（2026-10-13 增）：本组件是全部业务入口
 * 唯一真实发起大模型调用的位置，只有它同时拿得到「输入 token 估算、供应商名、真实耗时、成败」，
 * 而调用者身份（userId）与归属课程（courseId）由控制器经 {@link AiCallContext} 作用域带入。
 * 未处于作用域内时静默跳过落库——宁可少记一条，也不记出错误的归属。</p>
 *
 * <p>⚠️ <b>覆盖边界（如实记录）</b>：{@link #stream} 的实际调用发生在 SSE 订阅线程上，
 * {@code ThreadLocal} 作用域不会跟随过去，故流式路径<b>不落库</b>；管理端统计页与手册
 * 均已注明"仅统计非流式调用"。</p>
 */
@Slf4j
@Component
public class AiCoreComponent {

    /** 未配置任何 LLM 供应商 Key 时返回的清晰业务提示（不再返回伪造 JSON） */
    public static final String NO_PROVIDER_MSG = "AI 服务暂未配置（请联系管理员配置大模型 API Key 后重试）。";
    // DR-74-B：删除 "AI 服务暂时不可用，请稍后再试。" 这个 SERVICE_ERROR_MSG 常量。
    // 它曾被当作正常回答返回（HTTP 200 + code 200），是"失败伪装成成功"的载体；
    // 失败现在一律是 LLMUnavailableException(503)，不允许再有任何"看起来像内容"的兜底文案。

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LLMProvider llmProvider;
    private final PromptGuard promptGuard;
    private final MeterRegistry meterRegistry;
    /** 调用落库服务（records ai_call_logs）；其 record() 自身失败安全，这里不做 try/catch。 */
    private final AiCallLogService aiCallLogService;
    /** 按业务类型分桶的 AI 调用计数器缓存：key=类型，value=对应 Counter（首次访问时懒注册）。 */
    private final Map<String, Counter> aiCallCounters = new ConcurrentHashMap<>();
    /** 按业务类型分桶的 AI 输入 token 估算计数器缓存（与调用计数器对称，口径见 {@link #recordAiToken}）。 */
    private final Map<String, Counter> aiTokenCounters = new ConcurrentHashMap<>();
    /** 按业务类型分桶的 AI 调用失败计数器缓存（与调用计数器对称，口径见 {@link #recordAiFailure}）。 */
    private final Map<String, Counter> aiFailedCounters = new ConcurrentHashMap<>();

    public AiCoreComponent(LLMProvider llmProvider, PromptGuard promptGuard, MeterRegistry meterRegistry,
                           AiCallLogService aiCallLogService) {
        this.llmProvider = llmProvider;
        this.promptGuard = promptGuard;
        this.meterRegistry = meterRegistry;
        this.aiCallLogService = aiCallLogService;
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
     * 直连出口：调用供应商后对输出统一做活动内容清洗。
     * <p>取不到内容一律是失败（不再把 null 交给上层去兜 {@code SERVICE_ERROR_MSG}——
     * 那个兜底正是「HTTP 200 + 一句中文」的诞生地，见手册 DR-74）。</p>
     */
    public String completeAndSanitize(List<Map<String, String>> messages, double temperature, String type) {
        recordAiCall(type);
        long inputTokens = estimateInputTokens(messages);
        recordAiToken(type, inputTokens);
        long startedNanos = System.nanoTime();
        String sanitized;
        try {
            sanitized = promptGuard.sanitizeOutput(llmProvider.complete(messages, temperature));
        } catch (Exception e) {
            log.error("AI相关操作失败: {}", e.getMessage(), e);
            recordAiFailure(type);
            logCall(inputTokens, null, startedNanos, false, e.getMessage());
            throw e;
        }
        // 空响应单独在 try 之外判定并计入一次失败：放进 try 里会被下面的兜底 catch 再记一遍，
        // 失败率与 ai_call_logs 都会翻倍。
        if (sanitized == null || sanitized.isBlank()) {
            recordAiFailure(type);
            logCall(inputTokens, null, startedNanos, false, "上游未返回可用正文");
            throw new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE,
                    "大模型未返回可用正文（供应商 [" + providerName() + "] 返回空内容）");
        }
        logCall(inputTokens, sanitized, startedNanos, true, null);
        return sanitized;
    }

    /**
     * 统一调用入口：成功返回清洗后的正文；<b>失败抛 {@link LLMUnavailableException}（HTTP 503）</b>。
     * <p>旧契约在此返回 {@code NO_PROVIDER_MSG} / {@code SERVICE_ERROR_MSG} 两句文案，控制器把它们当
     * 正常回答用 {@code ApiResponse.success} 发出，于是"AI 挂了"在协议层长成"AI 答了一句客套话"，
     * 出题链路甚至拿这句中文去 parse JSON（DR-74 实测）。</p>
     */
    public String callLlm(List<Map<String, String>> messages, Double temperature, String jsonSchema, String type) {
        if (!llmProvider.isConfigured()) {
            // 与既有口径一致：未配置不计入调用量/失败率（它是部署形态，不是上游故障），
            // 但对调用方必须是失败——503 + 「请联系管理员配置 Key」的文案原样保留。
            throw new LLMUnavailableException(LLMUnavailableException.Kind.NOT_CONFIGURED, NO_PROVIDER_MSG);
        }
        recordAiCall(type);
        long inputTokens = estimateInputTokens(messages);
        if (jsonSchema != null) {
            inputTokens += (long) (jsonSchema.length() / 1.5);
        }
        recordAiToken(type, inputTokens);
        long startedNanos = System.nanoTime();
        String result;
        try {
            result = jsonSchema != null
                    ? llmProvider.completeJson(messages, temperature, jsonSchema)
                    : llmProvider.complete(messages, temperature);
        } catch (Exception e) {
            log.error("AI相关操作失败: {}", e.getMessage(), e);
            recordAiFailure(type);
            logCall(inputTokens, null, startedNanos, false, e.getMessage());
            throw e;
        }
        if (result == null) {
            // 供应商侧 null 只可能是「未返回可用正文」（未配置已在方法开头抛出），同样只记一次失败
            recordAiFailure(type);
            logCall(inputTokens, null, startedNanos, false, "上游返回空响应");
            throw new LLMUnavailableException(LLMUnavailableException.Kind.EMPTY_RESPONSE,
                    "大模型返回空响应（供应商 [" + providerName() + "]）");
        }
        String sanitized = promptGuard.sanitizeOutput(result);
        logCall(inputTokens, sanitized, startedNanos, true, null);
        return sanitized;
    }

    /**
     * 把本次调用落到 {@code ai_call_logs} —— <b>仅当处于 {@code AiCallLogService#inContext} 作用域内</b>时写。
     *
     * <p>不在作用域内时静默跳过（返回）：宁可少记一条，也不把归属不明的调用记成"某个课程用了 AI"。
     * 落库本身的失败已由 {@code AiCallLogService#record} 吞掉，故此处不再包一层 try/catch。</p>
     *
     * @param promptTokens 本次输入 token 估算
     * @param outputText   模型输出原文（失败/空响应传 null）
     * @param startedNanos 调用起始纳秒（用于算耗时）
     * @param success      是否拿到可用输出
     * @param errorMsg     失败原因（可空）
     */
    private void logCall(long promptTokens, String outputText, long startedNanos, boolean success, String errorMsg) {
        AiCallContext ctx = AiCallContext.get();
        if (ctx == null || aiCallLogService == null) {
            return;
        }
        long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        aiCallLogService.record(ctx.getUserId(), ctx.getCourseId(), ctx.getScene(),
                providerName(), promptTokens, outputText, durationMs, success, errorMsg);
    }

    /** 当前实际生效的供应商名；未配置（或读取异常）时返回 null，不阻断落库。 */
    private String providerName() {
        try {
            return llmProvider != null && llmProvider.isConfigured() ? llmProvider.name() : null;
        } catch (Exception e) {
            log.debug("读取 LLM 供应商名失败，按未知处理: {}", e.getMessage());
            return null;
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
     * 从 RAG 返回的 JSON（{@code {"answer":...,"sources":[...]}}）中提取召回来源；
     * 非 JSON、无 {@code sources} 或解析失败时返回空列表——溯源是增强信息，不得因此让问答本身失败。
     */
    public List<QaSourceVO> extractRagSources(String ragJson) {
        String json = JsonUtils.extractJsonObject(ragJson);
        if (json == null) {
            return List.of();
        }
        try {
            JsonNode sources = OBJECT_MAPPER.readTree(json).get("sources");
            if (sources == null || !sources.isArray()) {
                return List.of();
            }
            List<QaSourceVO> list = new ArrayList<>(sources.size());
            for (JsonNode s : sources) {
                String documentName = s.path("documentName").asText(null);
                String snippet = s.path("snippet").asText(null);
                if (documentName == null && snippet == null) {
                    continue;
                }
                list.add(new QaSourceVO(
                        s.hasNonNull("documentId") ? s.get("documentId").asLong() : null,
                        documentName,
                        s.hasNonNull("relevanceScore") ? s.get("relevanceScore").asDouble() : null,
                        snippet));
            }
            return list;
        } catch (Exception e) {
            log.debug("RAG 来源 JSON 解析失败，按无溯源处理: {}", e.getMessage());
            return List.of();
        }
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
