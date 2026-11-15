package com.aicourse.integration.rag;

import com.aicourse.integration.ContractPaths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 本地占位实现 {@link DisabledRagProvider} 的单元测试。
 * <p>
 * 锁定「当前阶段不依赖 Python」的占位契约：所有方法返回 {@code 503} 降级占位（仅填充
 * {@code error} / {@code code}），健康状态恒为 false。该 {@code 503} 与
 * {@code AIServiceImpl.ragAsk}「检测到 code:503 即回退纯 LLM」约定兼容，问答链路不中断。
 */
class DisabledRagProviderTest {

    private final DisabledRagProvider provider = new DisabledRagProvider();

    @Test
    void healthCheck_returnsFalse_noPythonEngine() {
        assertFalse(provider.healthCheck().healthy());
    }

    @Test
    void ask_returns503Unavailable() {
        RagContract.RagQueryResult r = provider.ask(new RagContract.RagAskRequest("q", 1L, 5));
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
        assertNull(r.answer());
        assertNull(r.sources());
    }

    @Test
    void addDocument_returns503Unavailable() {
        RagContract.RagDocument r = provider.addDocument(1L, "doc", "content");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
    }

    @Test
    void deleteDocument_returns503Unavailable() {
        RagContract.RagDocument r = provider.deleteDocument(1L);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
    }

    @Test
    void listDocuments_returns503Unavailable() {
        RagContract.RagDocumentList r = provider.listDocuments(1L);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
        assertNull(r.documents());
    }

    @Test
    void updateDocument_returns503Unavailable() {
        RagContract.RagDocument r = provider.updateDocument(1L, "content");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
    }

    @Test
    void buildIndex_returns503Unavailable() {
        RagContract.IndexResult r = provider.buildIndex(1L);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
        assertNull(r.success());
    }

    @Test
    void clearIndex_returns503Unavailable() {
        RagContract.IndexResult r = provider.clearIndex(1L);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
    }

    @Test
    void retrieve_returns503Unavailable() {
        RagContract.RagRetrievalResult r = provider.retrieve(new RagContract.RagRetrieveRequest("q", 1L, 5));
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_RAG_UNAVAILABLE, r.error());
        assertEquals(ContractPaths.MODE_UNAVAILABLE, r.mode());
        assertNull(r.results());
    }
}
