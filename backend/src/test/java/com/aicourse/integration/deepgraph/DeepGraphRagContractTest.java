package com.aicourse.integration.deepgraph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DeepGraph 契约 record 的引擎就绪判定：mode 必须 ENGINE，且 code 不得为 503。 */
class DeepGraphRagContractTest {

    @Test
    @DisplayName("检索结果：ENGINE + code null 为就绪，503/UNAVAILABLE 不就绪")
    void retrievalEngineReadyCoversAllBranches() {
        assertTrue(new DeepGraphRagContract.DeepGraphRetrievalResult(
                List.of(), "", "ENGINE", null, null).engineReady());
        assertFalse(new DeepGraphRagContract.DeepGraphRetrievalResult(
                List.of(), "", "ENGINE", "degraded", 503).engineReady());
        assertFalse(new DeepGraphRagContract.DeepGraphRetrievalResult(
                List.of(), "", "UNAVAILABLE", "degraded", null).engineReady());
    }

    @Test
    @DisplayName("课程图谱：ENGINE + code null 为就绪，503/UNAVAILABLE 不就绪")
    void courseGraphEngineReadyCoversAllBranches() {
        assertTrue(new DeepGraphRagContract.DeepGraphCourseGraphData(
                List.of(), List.of(), List.of(), "ENGINE", null, null).engineReady());
        assertFalse(new DeepGraphRagContract.DeepGraphCourseGraphData(
                List.of(), List.of(), List.of(), "ENGINE", "degraded", 503).engineReady());
        assertFalse(new DeepGraphRagContract.DeepGraphCourseGraphData(
                List.of(), List.of(), List.of(), "UNAVAILABLE", "degraded", null).engineReady());
    }

    @Test
    @DisplayName("问答结果 record 可承载空来源与错误码")
    void qaResultCarriesEmptySources() {
        DeepGraphRagContract.DeepGraphQaResult result =
                new DeepGraphRagContract.DeepGraphQaResult("答案", null, List.of(), "ENGINE", null, 200);
        assertTrue(result.sources().isEmpty());
    }
}
