package com.aicourse.service.component.ai;

import com.aicourse.integration.ContractPaths;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphRetrievalResult;
import com.aicourse.integration.deepgraph.DeepGraphRagContract.DeepGraphSource;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.service.DeepGraphRagService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.RAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 双引擎证据合并（{@link CourseEvidenceComponent#retrieveWithDocuments} /
 * {@link CourseEvidenceComponent#retrieveMergedTyped}）单元测试。
 * <p>口径：只有 {@code mode=ENGINE} 才算真实证据；8002 在前、8501 补充；按片段归一化去重；
 * 第二引擎未启用（null）/降级/异常一律静默跳过，行为与单引擎时一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeepGraphEvidenceMergeTest {

    @Mock
    private RAGService ragService;
    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private DeepGraphRagService deepGraphRagService;

    private CourseEvidenceComponent component;

    @BeforeEach
    void setUp() {
        component = new CourseEvidenceComponent(ragService, knowledgePointService, deepGraphRagService);
    }

    private static RagRetrievalResult engineRetrieval(RagSource... sources) {
        return new RagRetrievalResult(List.of(sources), ContractPaths.MODE_ENGINE, null, null);
    }

    private static DeepGraphRetrievalResult deepgraphRetrieval(DeepGraphSource... sources) {
        return new DeepGraphRetrievalResult(List.of(sources), "- TCP (定义) 三次握手",
                ContractPaths.MODE_ENGINE, null, null);
    }

    @Test
    @DisplayName("双引擎都 ENGINE -> 合并（8002 在前，8501 补充，携带图谱上下文不丢失）")
    void bothEnginesReady_mergesResults() {
        when(ragService.retrieveTyped("三次握手", 4178L, 5)).thenReturn(engineRetrieval(
                new RagSource(1L, "知识点语料", 0.9, "TCP 是面向连接的传输层协议")));
        when(deepGraphRagService.retrieveTyped("三次握手", 4178L, 5)).thenReturn(deepgraphRetrieval(
                new DeepGraphSource(null, "course4178_教材.txt", 0.8, "三次握手建立可靠连接", "VECTOR")));

        List<RagSource> merged = component.retrieveWithDocuments("三次握手", 4178L, 5);
        assertEquals(2, merged.size());
        assertEquals("知识点语料", merged.get(0).documentName());
        assertEquals("course4178_教材.txt", merged.get(1).documentName());

        RagRetrievalResult typed = component.retrieveMergedTyped("三次握手", 4178L, 5);
        assertTrue(typed.engineReady());
        assertEquals(2, typed.results().size());
    }

    @Test
    @DisplayName("重复片段去重：8501 与 8002 命中同一段时不重复注入")
    void duplicateSnippets_deduplicated() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(engineRetrieval(
                new RagSource(1L, "d1", 0.9, "Same Snippet Content")));
        when(deepGraphRagService.retrieveTyped("q", 1L, 5)).thenReturn(deepgraphRetrieval(
                new DeepGraphSource(null, "course1_x.txt", 0.8, "same snippet  content", "VECTOR")));

        List<RagSource> merged = component.retrieveWithDocuments("q", 1L, 5);
        assertEquals(1, merged.size());
    }

    @Test
    @DisplayName("8501 降级(UNAVAILABLE) -> 静默跳过，仅返回 8002 证据")
    void deepgraphDegraded_skippedSilently() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(engineRetrieval(
                new RagSource(1L, "d1", 0.9, "primary")));
        when(deepGraphRagService.retrieveTyped("q", 1L, 5)).thenReturn(
                new DeepGraphRetrievalResult(null, null, ContractPaths.MODE_UNAVAILABLE,
                        ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE));

        List<RagSource> merged = component.retrieveWithDocuments("q", 1L, 5);
        assertEquals(1, merged.size());
        assertEquals("d1", merged.get(0).documentName());
    }

    @Test
    @DisplayName("未启用第二引擎（null provider，旧构造器）-> 行为与单引擎一致")
    void deepgraphAbsent_behavesLikeSingleEngine() {
        CourseEvidenceComponent legacy = new CourseEvidenceComponent(ragService, knowledgePointService);
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(engineRetrieval(
                new RagSource(1L, "d1", 0.9, "primary")));

        assertEquals(1, legacy.retrieveWithDocuments("q", 1L, 5).size());
        assertTrue(legacy.retrieveMergedTyped("q", 1L, 5).engineReady());
    }

    @Test
    @DisplayName("8002 降级 + 8501 ENGINE -> 仍给出真实引擎证据（mode=ENGINE）")
    void primaryDegraded_deepgraphReady_stillEngineEvidence() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(
                new RagRetrievalResult(null, ContractPaths.MODE_UNAVAILABLE,
                        ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE));
        when(deepGraphRagService.retrieveTyped("q", 1L, 5)).thenReturn(deepgraphRetrieval(
                new DeepGraphSource(null, "course1_x.txt", 0.8, "deepgraph snippet", "VECTOR")));

        RagRetrievalResult typed = component.retrieveMergedTyped("q", 1L, 5);
        assertTrue(typed.engineReady());
        assertEquals(1, typed.results().size());
        assertEquals("course1_x.txt", typed.results().get(0).documentName());
    }

    @Test
    @DisplayName("两路都降级 -> 原样透传主引擎应答，绝不伪造 ENGINE")
    void bothDegraded_passesThroughPrimary() {
        RagRetrievalResult unavailable = new RagRetrievalResult(null, ContractPaths.MODE_UNAVAILABLE,
                ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE);
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(unavailable);
        when(deepGraphRagService.retrieveTyped("q", 1L, 5)).thenReturn(
                new DeepGraphRetrievalResult(null, null, ContractPaths.MODE_UNAVAILABLE,
                        ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE));

        RagRetrievalResult typed = component.retrieveMergedTyped("q", 1L, 5);
        assertEquals(ContractPaths.MODE_UNAVAILABLE, typed.mode());
        assertEquals(ContractPaths.CODE_UNAVAILABLE, typed.code());
    }

    @Test
    @DisplayName("8501 抛异常 -> 静默跳过，不阻断主链")
    void deepgraphThrows_skippedSilently() {
        when(ragService.retrieveTyped("q", 1L, 5)).thenReturn(engineRetrieval(
                new RagSource(1L, "d1", 0.9, "primary")));
        when(deepGraphRagService.retrieveTyped("q", 1L, 5)).thenThrow(new RuntimeException("8501 down"));

        List<RagSource> merged = component.retrieveWithDocuments("q", 1L, 5);
        assertEquals(1, merged.size());
    }

    @Test
    @DisplayName("courseId 缺失 -> 不调用第二引擎")
    void nullCourseId_skipsDeepgraph() {
        List<RagSource> merged = component.retrieveWithDocuments("q", null, 5);
        assertTrue(merged.isEmpty());
        verify(deepGraphRagService, never()).retrieveTyped(any(), any(), anyInt());
    }

    @Test
    @DisplayName("8501 补充条数受上限约束（不喧宾夺主）")
    void deepgraphSources_capped() {
        when(ragService.retrieveTyped(eq("q"), anyLong(), anyInt())).thenReturn(
                new RagRetrievalResult(null, ContractPaths.MODE_UNAVAILABLE,
                        ContractPaths.MSG_RAG_UNAVAILABLE, ContractPaths.CODE_UNAVAILABLE));
        DeepGraphSource[] many = new DeepGraphSource[8];
        for (int i = 0; i < many.length; i++) {
            many[i] = new DeepGraphSource(null, "course1_x.txt", 0.9 - i * 0.01, "snippet-" + i, "VECTOR");
        }
        when(deepGraphRagService.retrieveTyped(eq("q"), anyLong(), anyInt()))
                .thenReturn(deepgraphRetrieval(many));

        List<RagSource> merged = component.retrieveWithDocuments("q", 1L, 5);
        assertEquals(5, merged.size(), "第二引擎合并条数不得超过 MAX_DEEPGRAPH_SOURCES(5)");
    }
}
