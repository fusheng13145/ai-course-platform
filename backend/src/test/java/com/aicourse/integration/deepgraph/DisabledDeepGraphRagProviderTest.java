package com.aicourse.integration.deepgraph;

import com.aicourse.integration.ContractPaths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepGraph RAG 本地占位实现测试：enabled=false 时全部方法返回 503 降级，
 * 上层（CourseEvidenceComponent）按 engineReady() 判定后静默忽略本引擎。
 */
class DisabledDeepGraphRagProviderTest {

    private final DisabledDeepGraphRagProvider provider = new DisabledDeepGraphRagProvider();

    @Test
    void healthCheck_returnsFalse() {
        assertFalse(provider.healthCheck().healthy());
    }

    @Test
    void retrieve_returns503Unavailable() {
        DeepGraphRagContract.DeepGraphRetrievalResult r = provider.retrieve(
                new DeepGraphRagContract.DeepGraphRetrieveRequest("q", 1L, 3));
        assertFalse(r.engineReady());
        assertEquals(ContractPaths.MODE_UNAVAILABLE, r.mode());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, r.error());
    }

    @Test
    void ingest_returns503Unavailable() {
        DeepGraphRagContract.DeepGraphIngestResult r = provider.ingest(1L, "doc", "content");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertEquals(ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, r.error());
    }

    @Test
    void listDocuments_returns503Unavailable() {
        DeepGraphRagContract.DeepGraphDocumentList r = provider.listDocuments(1L);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertNull(r.documents());
    }

    @Test
    void deleteDocument_returns503Unavailable() {
        DeepGraphRagContract.DeepGraphDeleteResult r = provider.deleteDocument(1L, "doc");
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    void chapterGraph_returns503Unavailable() {
        DeepGraphRagContract.DeepGraphChapterGraphResult r =
                provider.chapterGraph(1L, "doc", "ch", null);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
    }

    @Test
    void learningPath_returns503Unavailable() {
        DeepGraphRagContract.DeepGraphLearningPathResult r =
                provider.learningPath("TCP", 1L, 3, true);
        assertEquals(ContractPaths.CODE_UNAVAILABLE, r.code());
        assertNull(r.pathText());
    }
}
