package com.aicourse.integration.rag;

import com.aicourse.integration.ContractPaths;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * RAG 集成契约（标准化接口）。
 * <p>
 * 职责边界（2026-08-13 用户明确）：RAG 检索引擎（BM25 / 向量检索 / embedding / 融合）由团队另一成员
 * 以 Python 微服务（端口 8002）实现。本接口是本端与 Python 端的<b>唯一对接契约</b>——定义了
 * 清晰的函数签名、数据结构（请求/响应记录）与调用约定，便于另一成员独立实现、也便于本端
 * 在不依赖具体 Python 实现的前提下完成消费侧逻辑。
 * </p>
 * <p>
 * <b>两种实现</b>：
 * <ul>
 *   <li>{@link DisabledRagProvider} —— <b>当前阶段默认占位实现</b>，不依赖任何 Python 服务，
 *       所有方法返回 {@code 503} 降级占位（{@code AIServiceImpl.ragAsk} 据此自动回退纯 LLM 调用）；</li>
 *   <li>{@link HttpRagProvider} —— 可选远端实现，经 {@code rag.enabled=true} 启用，按 {@link ContractPaths}
 *       调用 Python 8002 的 {@code /api/v1/rag/*} 契约。</li>
 * </ul>
 * </p>
 *
 * <h3>调用约定（HTTP 方法 + 路径 + JSON 字段，详见 {@link ContractPaths}）</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>路径</th><th>请求体关键字段</th><th>响应关键字段</th></tr>
 *   <tr><td>GET</td><td>{@code /api/v1/rag/health}</td><td>-</td><td>任意 2xx 即健康</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/rag/ask}</td><td>question, course_id?, top_k</td><td>answer, sources[]</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/rag/documents}</td><td>course_id, document_name, content</td><td>document 元信息</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/rag/retrieve}</td><td>query, course_id?, top_k</td><td>results[]</td></tr>
 *   <tr><td>POST</td><td>{@code /api/v1/rag/index/build/{courseId}}</td><td>-</td><td>success, indexed_chunks</td></tr>
 * </table>
 * 状态码语义：{@code 200} 成功；{@code 503} 引擎未接入/不可用（降级占位）。
 */
public interface RagContract {

    /** 引擎健康检查。占位实现返回 false；启用远端时返回 Python 8002 可达性。 */
    HealthStatus healthCheck();

    /** 基于知识库的问答。 */
    RagQueryResult ask(RagAskRequest request);

    /** 向知识库添加文档。 */
    RagDocument addDocument(Long courseId, String documentName, String content);

    /** 从知识库删除文档。 */
    RagDocument deleteDocument(Long documentId);

    /** 获取知识库文档列表。 */
    RagDocumentList listDocuments(Long courseId);

    /** 更新文档内容。 */
    RagDocument updateDocument(Long documentId, String content);

    /** 构建/更新课程向量索引。 */
    IndexResult buildIndex(Long courseId);

    /** 清理课程索引。 */
    IndexResult clearIndex(Long courseId);

    /** 检索相似文档（仅召回，不生成答案）。 */
    RagRetrievalResult retrieve(RagRetrieveRequest request);

    /** 健康状态。 */
    record HealthStatus(boolean healthy) {
    }

    /** 问答请求。 */
    record RagAskRequest(String question, Long courseId, int topK) {
    }

    /** 检索请求。 */
    record RagRetrieveRequest(String query, Long courseId, int topK) {
    }

    /** 召回来源（引用片段）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RagSource(Long documentId, String documentName, Double relevanceScore, String snippet) {
    }

    /** 问答结果（数据结构定义）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RagQueryResult(String answer, List<RagSource> sources, String mode, String error, Integer code) {
    }

    /** 文档结果（数据结构定义）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RagDocument(Long id, String documentName, Long courseId, String content, String mode, String error, Integer code) {
    }

    /** 文档列表结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RagDocumentList(List<RagDocument> documents, String error, Integer code) {
    }

    /** 索引操作结果。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IndexResult(Boolean success, Integer indexedChunks, String error, Integer code) {
    }

    /**
     * 检索召回结果。
     * <p>{@code mode} 取引擎应答原文（{@code ENGINE}/{@code PLACEHOLDER}/{@code UNAVAILABLE}）：
     * 调用方只有看到 {@code ENGINE} 才能把召回内容当作真实引擎证据，否则必须走本地降级路径。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RagRetrievalResult(List<RagSource> results, String mode, String error, Integer code) {

        /**
         * 是否可作为真实引擎证据：只有 {@code mode=ENGINE} 且未被降级（{@code code} 非 503）。
         * <p>{@code PLACEHOLDER}（CI 占位桩）与 {@code UNAVAILABLE} 一律不算——它们不是引擎产物，
         * 上层必须改走本地降级路径，不得把占位/降级内容当作课程答案。</p>
         */
        public boolean engineReady() {
            return ContractPaths.MODE_ENGINE.equals(mode)
                    && (code == null || code != ContractPaths.CODE_UNAVAILABLE);
        }
    }
}
