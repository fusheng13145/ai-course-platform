package com.aicourse.service.impl;

import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.rag.DisabledRagProvider;
import com.aicourse.integration.rag.HttpRagProvider;
import com.aicourse.integration.rag.RagContract;
import com.aicourse.integration.rag.RagContract.RagAskRequest;
import com.aicourse.integration.rag.RagContract.RagRetrieveRequest;
import com.aicourse.service.RAGService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RAG 服务实现（门面）。
 * <p>
 * 本类实现业务接口 {@link RAGService}，内部按 {@code rag.enabled} 委派到标准化契约
 * {@link RagContract} 的具体实现：
 * <ul>
 *   <li>{@code false}（默认）→ {@link DisabledRagProvider}：本地占位，返回 {@code 503}，不依赖 Python；</li>
 *   <li>{@code true} → {@link HttpRagProvider}：HTTP 对接 Python RAG 微服务 8002。</li>
 * </ul>
 * 契约接口、数据结构与调用约定集中在 {@code com.aicourse.integration.rag} 包，便于另一成员独立实现 Python 端。
 * 禁用/不可达返回的 {@code 503} 与 {@code AIServiceImpl.ragAsk}「检测到 code:503 即回退纯 LLM」约定一致。
 * </p>
 */
@Service
@Slf4j
public class RAGServiceImpl implements RAGService {

    private static final ObjectMapper OM = new ObjectMapper();

    private final RagContract provider;

    public RAGServiceImpl(
            @Value("${rag.enabled:false}") boolean enabled,
            @Value("${rag.base-url:http://localhost:8002}") String baseUrl,
            @Value("${rag.connect-timeout:5}") int connectTimeout,
            @Value("${rag.read-timeout:60}") int readTimeout,
            @Value("${rag.max-retries:2}") int maxRetries,
            @Value("${rag.health-path:/api/v1/rag/health}") String healthPath,
            @Value("${rag.default-top-k:5}") int defaultTopK) {
        this.provider = enabled
                ? new HttpRagProvider(baseUrl, connectTimeout, readTimeout, maxRetries, healthPath, defaultTopK)
                : new DisabledRagProvider();
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

    private String serialize(Object obj) {
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("RAG 结果序列化失败：{}", e.getMessage());
            return "{\"error\":\"" + ContractPaths.MSG_RAG_UNAVAILABLE + "\",\"code\":" + ContractPaths.CODE_UNAVAILABLE + "}";
        }
    }
}
