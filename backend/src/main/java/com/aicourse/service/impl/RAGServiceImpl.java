package com.aicourse.service.impl;

import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.rag.DisabledRagProvider;
import com.aicourse.integration.rag.HttpRagProvider;
import com.aicourse.integration.rag.RagContract;
import com.aicourse.integration.rag.RagContract.RagAskRequest;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagRetrieveRequest;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RAG 服务实现（门面）。
 * <p>
 * 本类实现业务接口 {@link RAGService}，内部按 {@code rag.provider} 委派到标准化契约
 * {@link RagContract} 的具体实现（2026-11-15 基线：Python 为核心、Java 仅备选）：
 * <ul>
 *   <li>{@code provider=python}（默认）→ {@link HttpRagProvider}：HTTP 对接知识库 RAG
 *       （默认 Engine Hub 8600；分离布局 8501，原 8002 已退役并入）；</li>
 *   <li>{@code provider=java} → {@link DisabledRagProvider}：不调 Python，返回 {@code 503}，问答走纯 LLM 兜底；</li>
 *   <li>{@code provider} 未配置 → 沿用历史开关 {@code rag.enabled}，保证老配置不回退。</li>
 * </ul>
 * 契约接口、数据结构与调用约定集中在 {@code com.aicourse.integration.rag} 包，便于另一成员独立实现 Python 端。
 * 禁用/不可达返回的 {@code 503} 与 {@code AIServiceImpl.ragAsk}「检测到 code:503 即回退纯 LLM」约定一致。
 * </p>
 */
@Service
@Slf4j
public class RAGServiceImpl implements RAGService {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 引擎模式：Python 契约（默认） */
    private static final String MODE_PYTHON = "python";
    /** 引擎模式：纯 LLM 兜底（不调 Python） */
    private static final String MODE_JAVA = "java";

    private final RagContract provider;

    /**
     * 解析是否使用 Python 引擎（package-private：便于单测直测）。
     * <ol>
     *   <li>{@code rag.provider=java} → 强制纯 LLM，不调 Python；</li>
     *   <li>{@code rag.provider=python} → 使用知识库 RAG 契约（默认）；</li>
     *   <li>空 / 未识别 → 沿用历史开关 {@code rag.enabled}，保证老配置文件不回退。</li>
     * </ol>
     */
    static boolean resolveUsePython(String providerMode, boolean enabled) {
        String m = providerMode == null ? "" : providerMode.trim().toLowerCase(java.util.Locale.ROOT);
        if (MODE_JAVA.equals(m)) {
            return false;
        }
        if (MODE_PYTHON.equals(m)) {
            return true;
        }
        return enabled;
    }

    /** 兼容旧签名（provider 走 rag.enabled），供既有单测直构使用。 */
    public RAGServiceImpl(
            boolean enabled,
            String baseUrl,
            int connectTimeout,
            int readTimeout,
            int maxRetries,
            String healthPath,
            int defaultTopK) {
        this(enabled, "", baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultTopK, "");
    }

    /** 兼容旧签名（含 internalApiKey），provider 走 rag.enabled。 */
    public RAGServiceImpl(
            boolean enabled,
            String baseUrl,
            int connectTimeout,
            int readTimeout,
            int maxRetries,
            String healthPath,
            int defaultTopK,
            String internalApiKey) {
        this(enabled, "", baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultTopK, internalApiKey);
    }

    @Autowired
    public RAGServiceImpl(
            @Value("${rag.enabled:true}") boolean enabled,
            @Value("${rag.provider:}") String providerMode,
            @Value("${rag.base-url:" + ContractPaths.RAG_DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${rag.connect-timeout:5}") int connectTimeout,
            @Value("${rag.read-timeout:60}") int readTimeout,
            @Value("${rag.max-retries:2}") int maxRetries,
            @Value("${rag.health-path:/api/v1/rag/health}") String healthPath,
            @Value("${rag.default-top-k:5}") int defaultTopK,
            @Value("${app.internal.service-api-key:}") String internalApiKey) {
        boolean usePython = resolveUsePython(providerMode, enabled);
        this.provider = usePython
                ? new HttpRagProvider(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultTopK, internalApiKey)
                : new DisabledRagProvider();
        log.info("[RAG] engine={} baseUrl={}", usePython ? MODE_PYTHON : "disabled(pure-llm)",
                usePython ? baseUrl : "-");
    }

    @Override
    public boolean healthCheck() {
        return provider.healthCheck().healthy();
    }

    @Override
    public String ask(String question, Long courseId, int topK) {
        return serialize(provider.ask(new RagAskRequest(question, courseId, topK)));
    }

    @Override
    public String addDocument(Long courseId, String documentName, String content) {
        return serialize(provider.addDocument(courseId, documentName, content));
    }

    @Override
    public String deleteDocument(Long documentId) {
        return serialize(provider.deleteDocument(documentId));
    }

    @Override
    public String listDocuments(Long courseId) {
        return serialize(provider.listDocuments(courseId));
    }

    @Override
    public String updateDocument(Long documentId, String content) {
        return serialize(provider.updateDocument(documentId, content));
    }

    @Override
    public String buildIndex(Long courseId) {
        return serialize(provider.buildIndex(courseId));
    }

    @Override
    public String clearIndex(Long courseId) {
        return serialize(provider.clearIndex(courseId));
    }

    @Override
    public String retrieve(String query, Long courseId, int topK) {
        return serialize(provider.retrieve(new RagRetrieveRequest(query, courseId, topK)));
    }

    @Override
    public RagRetrievalResult retrieveTyped(String query, Long courseId, int topK) {
        // 直接透传契约结果：mode 是「引擎是否真实应答」的判据，经 JSON 序列化再反解只会丢语义。
        return provider.retrieve(new RagRetrieveRequest(query, courseId, topK));
    }

    private String serialize(Object obj) {
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("RAG 结果序列化失败：{}", e.getMessage());
            return "{\"error\":\"" + ContractPaths.MSG_RAG_UNAVAILABLE + "\",\"code\":" + ContractPaths.CODE_UNAVAILABLE + "}";
        }
    }
}
