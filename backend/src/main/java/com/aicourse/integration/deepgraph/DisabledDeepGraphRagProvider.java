package com.aicourse.integration.deepgraph;

import com.aicourse.integration.ContractPaths;

import java.util.List;

/**
 * DeepGraph RAG <b>本地占位实现</b>（{@code deepgraph-rag.enabled=false} 时的默认）。
 * <p>
 * 不依赖任何 Python 服务，所有方法返回 {@code 503} 降级占位。上层
 * {@code CourseEvidenceComponent} 按 {@code engineReady()} 判定后静默忽略本引擎，
 * 问答链自动回落到知识点语料检索 + 知识图谱接地的既有路径，行为与未集成前完全一致。
 * </p>
 */
public class DisabledDeepGraphRagProvider implements DeepGraphRagContract {

    private static final String MSG = ContractPaths.MSG_DEEPGRAPH_UNAVAILABLE;
    private static final int CODE = ContractPaths.CODE_UNAVAILABLE;

    @Override
    public HealthStatus healthCheck() {
        return new HealthStatus(false);
    }

    @Override
    public DeepGraphRetrievalResult retrieve(DeepGraphRetrieveRequest request) {
        return new DeepGraphRetrievalResult(null, null, ContractPaths.MODE_UNAVAILABLE, MSG, CODE);
    }

    @Override
    public DeepGraphIngestResult ingest(Long courseId, String documentName, String content) {
        return new DeepGraphIngestResult(null, null, null, null, null, null, null, ContractPaths.MODE_UNAVAILABLE, MSG, CODE);
    }

    @Override
    public DeepGraphIngestResult ingestFile(Long courseId, String documentName,
                                            byte[] content, String contentType) {
        return new DeepGraphIngestResult(null, null, null, null, null, null, null, ContractPaths.MODE_UNAVAILABLE, MSG, CODE);
    }

    @Override
    public DeepGraphDocumentList listDocuments(Long courseId) {
        return new DeepGraphDocumentList(null, MSG, CODE);
    }

    @Override
    public DeepGraphDeleteResult deleteDocument(Long courseId, String documentName) {
        return new DeepGraphDeleteResult(null, null, MSG, CODE);
    }

    @Override
    public DeepGraphChapterGraphResult chapterGraph(Long courseId, String documentName,
                                                    String chapterName, String chapterText) {
        return new DeepGraphChapterGraphResult(null, null, null, null, null, ContractPaths.MODE_UNAVAILABLE, MSG, CODE);
    }

    @Override
    public DeepGraphLearningPathResult learningPath(String target, Long courseId, int maxDepth,
                                                    boolean searchAllSources) {
        return new DeepGraphLearningPathResult(null, null, null, ContractPaths.MODE_UNAVAILABLE, MSG, CODE);
    }

    @Override
    public DeepGraphCourseGraphData getCourseGraph(Long courseId, String documentName) {
        return new DeepGraphCourseGraphData(null, null, null, ContractPaths.MODE_UNAVAILABLE, MSG, CODE);
    }
}
