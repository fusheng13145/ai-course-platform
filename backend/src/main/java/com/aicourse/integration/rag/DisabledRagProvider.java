package com.aicourse.integration.rag;

import com.aicourse.integration.ContractPaths;

import java.util.List;

/**
 * RAG <b>本地占位实现</b>（当前阶段默认）。
 * <p>
 * 不依赖任何 Python 服务，所有方法返回 {@code 503} 降级占位（仅填充 {@code error} / {@code code}，
 * 其余字段为 null，经 {@code @JsonInclude(NON_NULL)} 序列化后即为 {@code {"error":...,"code":503}}）。
 * 这与 {@code AIServiceImpl.ragAsk}「检测到 code:503 即回退纯 LLM」的降级约定完全兼容，问答链路不中断。
 * </p>
 * <p>
 * 接入 Python 8002 后经 {@code rag.enabled=true} 切换为 {@link HttpRagProvider}。
 * </p>
 */
public class DisabledRagProvider implements RagContract {

    private static final String MSG = ContractPaths.MSG_RAG_UNAVAILABLE;
    private static final int CODE = ContractPaths.CODE_UNAVAILABLE;

    @Override
    public HealthStatus healthCheck() {
        return new HealthStatus(false);
    }

    @Override
    public RagQueryResult ask(RagAskRequest request) {
        return new RagQueryResult(null, null, null, MSG, CODE);
    }

    @Override
    public RagDocument addDocument(Long courseId, String documentName, String content) {
        return new RagDocument(null, null, null, null, null, MSG, CODE);
    }

    @Override
    public RagDocument deleteDocument(Long documentId) {
        return new RagDocument(null, null, null, null, null, MSG, CODE);
    }

    @Override
    public RagDocumentList listDocuments(Long courseId) {
        return new RagDocumentList(null, MSG, CODE);
    }

    @Override
    public RagDocument updateDocument(Long documentId, String content) {
        return new RagDocument(null, null, null, null, null, MSG, CODE);
    }

    @Override
    public IndexResult buildIndex(Long courseId) {
        return new IndexResult(null, null, MSG, CODE);
    }

    @Override
    public IndexResult clearIndex(Long courseId) {
        return new IndexResult(null, null, MSG, CODE);
    }

    @Override
    public RagRetrievalResult retrieve(RagRetrieveRequest request) {
        return new RagRetrievalResult(null, MSG, CODE);
    }
}
