package com.aicourse.integration.deepgraph;

import com.aicourse.integration.ContractPaths;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * DeepGraph RAG 集成契约（文档图谱检索增强，端口 8501）。
 * <p>
 * 对接 {@code python-services/deepgraph_rag/}（已验收的 DeepSeek-RAG-Chatbot 原样复制 +
 * {@code api_server.py} 契约适配器）。与 8002 的 {@code RagContract} 互补而非替代：
 * 8002 面向知识点语料（structured corpus）的 BM25+向量+RRF+HyDE+重排混合检索；
 * 8501 面向<b>整篇课程文档</b>（PDF/DOCX/TXT 原文入库）的 FAISS+BM25 混合召回、
 * Neo4j GraphRAG 关系上下文、HyDE 查询扩展与 Cross-Encoder 重排（复制内容保持原实现不变）。
 * </p>
 * <p>
 * <b>两种实现</b>：
 * <ul>
 *   <li>{@link DisabledDeepGraphRagProvider} —— 默认占位（{@code deepgraph-rag.enabled=false}），
 *       所有方法返回 503 降级，问答链自动忽略本引擎；</li>
 *   <li>{@link HttpDeepGraphRagProvider} —— 远端实现，按 {@link ContractPaths} 调用 8501 的
 *       {@code /api/v1/deepgraph/*} 契约。</li>
 * </ul>
 * </p>
 *
 * <h3>调用约定（详见 {@link ContractPaths}）</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>路径</th><th>请求体关键字段</th><th>响应关键字段</th></tr>
 *   <tr><td>GET</td><td>{@code /api/v1/deepgraph/health}</td><td>-</td><td>任意 2xx 即健康</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/deepgraph/retrieve}</td><td>query, course_id?, top_k</td><td>results[], graph_context, mode</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/deepgraph/qa}</td><td>question, course_id?, chat_history?</td><td>answer, llm_prompt, sources[], mode</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/deepgraph/ingest}</td><td>course_id, document_name, content</td><td>success, stored_as, chunk_count, graph_edges</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/deepgraph/ingest-file?course_id=&document_name=}</td><td>raw pdf/docx/txt body</td><td>同上</td></tr>
 *   <tr><td>GET</td><td>{@code /api/v1/deepgraph/documents?course_id=}</td><td>-</td><td>documents[]</td></tr>
 *   <tr><td>DELETE</td><td>{@code /api/v1/deepgraph/documents/{name}?course_id=}</td><td>-</td><td>success</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/deepgraph/chapter-graph}</td><td>course_id, document_name, chapter_name?</td><td>success, nodes, edges, relations[]</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/deepgraph/learning-path}</td><td>target, course_id?, max_depth</td><td>path_text, mode</td></tr>
 * </table>
 * 降级语义与 8002 完全一致：HTTP 503（或 body {@code code:503}/{@code mode:"UNAVAILABLE"}）。
 */
public interface DeepGraphRagContract {

    /** 引擎健康检查。占位实现返回 false；启用远端时返回 8501 可达性。 */
    HealthStatus healthCheck();

    /**
     * 文档语料混合检索（仅召回，不生成答案）。
     * <p>返回结果附带 {@code graphContext}（Neo4j GraphRAG 关系行），调用方可将其一并注入接地提示词。</p>
     */
    DeepGraphRetrievalResult retrieve(DeepGraphRetrieveRequest request);

    /** 文档入库：整篇文本经原实现 process_documents 全链路处理（FAISS+BM25+图谱+章节）。 */
    DeepGraphIngestResult ingest(Long courseId, String documentName, String content);

    /**
     * 二进制文档入库（PDF/DOCX/TXT 原文，不做 Java 侧文本化）：
     * 以 raw body POST 到 8501 的 {@code ingest-file}，解析仍由复制内容 load_single_file 完成。
     */
    DeepGraphIngestResult ingestFile(Long courseId, String documentName,
                                     byte[] content, String contentType);

    /** 课程文档列表（course{id}_ 前缀命名空间）。 */
    DeepGraphDocumentList listDocuments(Long courseId);

    /** 删除课程文档（连同其向量索引/图谱/章节数据）。 */
    DeepGraphDeleteResult deleteDocument(Long courseId, String documentName);

    /** 章节知识图谱构建（LLM 三元组抽取 + Neo4j 同步）。 */
    DeepGraphChapterGraphResult chapterGraph(Long courseId, String documentName,
                                             String chapterName, String chapterText);

    /** 学习路径规划（Neo4j 反向追溯前置知识）。 */
    DeepGraphLearningPathResult learningPath(String target, Long courseId, int maxDepth,
                                             boolean searchAllSources);

    /**
     * 课程图谱聚合：某文档全部章节图谱（LLM 真实抽取）与层次大纲树的 nodes/edges 汇总，
     * 供本端幂等落库 MySQL {@code knowledge_points}/{@code knowledge_point_relations}
     * （平台「知识图谱」页渲染的数据源）。
     */
    DeepGraphCourseGraphData getCourseGraph(Long courseId, String documentName);

    /** 健康状态。 */
    record HealthStatus(boolean healthy) {
    }

    /** 检索请求。 */
    record DeepGraphRetrieveRequest(String query, Long courseId, int topK) {
    }

    /** 召回来源（引用片段；retrieval 为召回通道标记）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphSource(Long documentId, String documentName, Double relevanceScore,
                           String snippet, String retrieval) {
    }

    /**
     * 检索召回结果（含图谱关系上下文）。
     * <p>{@code mode} 取引擎应答原文；调用方只有看到 {@code ENGINE} 才能把召回内容当作真实引擎证据。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphRetrievalResult(List<DeepGraphSource> results, String graphContext,
                                    String mode, String error, Integer code) {

        /** 是否可作为真实引擎证据（与 {@code RagRetrievalResult.engineReady()} 同口径）。 */
        public boolean engineReady() {
            return ContractPaths.MODE_ENGINE.equals(mode)
                    && (code == null || code != ContractPaths.CODE_UNAVAILABLE);
        }
    }

    /** 问答结果（引擎内生成，用于独立 QA 通道；平台主问答链仅消费检索）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphQaResult(String answer, String llmPrompt, List<DeepGraphSource> sources,
                             String mode, String error, Integer code) {
    }

    /** 文档入库结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphIngestResult(Boolean success, Long courseId, String storedAs, Integer chapters,
                                 Integer chunkCount, Boolean hasGraph, Integer graphEdges,
                                 String mode, String error, Integer code) {
    }

    /** 文档列表结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphDocumentList(List<DeepGraphDocument> documents, String error, Integer code) {
    }

    /** 文档条目。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphDocument(String documentName, Long courseId, Integer chaptersCount,
                             Boolean hasGlobalGraph, Boolean hasFaissIndex) {
    }

    /** 文档删除结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphDeleteResult(Boolean success, String documentName, String error, Integer code) {
    }

    /** 章节图谱构建结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphChapterGraphResult(Boolean success, Integer nodes, Integer edges,
                                       List<RelationRow> relations, Boolean neo4jSynced,
                                       String mode, String error, Integer code) {
    }

    /** 图谱关系行（源实体/关系/目标实体）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RelationRow(String source, String relation, String target) {
    }

    /** 学习路径结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphLearningPathResult(String target, String pathText, String sourceDoc,
                                       String mode, String error, Integer code) {
    }

    /** 课程图谱聚合节点（type=CONCEPT/CHAPTER；evidence 为抽取溯源文本）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CourseGraphNode(String name, String type, String evidence) {
    }

    /** 课程图谱聚合边（chapter 为首见章节名）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CourseGraphEdge(String source, String target, String relation,
                           String evidence, String chapter) {
    }

    /** 课程图谱聚合结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeepGraphCourseGraphData(List<String> chapters, List<CourseGraphNode> nodes,
                                    List<CourseGraphEdge> edges, String mode,
                                    String error, Integer code) {

        /** 是否可作为真实引擎产物（与兄弟记录同口径）。 */
        public boolean engineReady() {
            return ContractPaths.MODE_ENGINE.equals(mode)
                    && (code == null || code != ContractPaths.CODE_UNAVAILABLE);
        }
    }
}
