package com.aicourse.integration;

/**
 * 集成契约常量（调用约定的唯一事实来源）。
 * <p>
 * 知识图谱（KG，端口 8001）与 RAG（端口 8002）两个 Python 微服务由团队另一成员负责，
 * 本端（Java 后端）实现<b>消费侧</b>与<b>标准化对接契约</b>。本类集中定义：
 * <ul>
 *   <li>配置键（{@code knowledge-graph.*} / {@code rag.*}），用于按 {@code enabled} 切换本地占位 / 远端 Python；</li>
 *   <li>HTTP 方法 + 路径（端点契约）；</li>
 *   <li>请求/响应 JSON 字段名（字段级调用约定）；</li>
 *   <li>状态码与模式语义（{@code 503} = 引擎未接入/不可用；{@code ENGINE}/{@code LOCAL}/{@code UNAVAILABLE}）。</li>
 * </ul>
 * 任何 Java 侧 HTTP 调用或 Python 侧实现都必须严格对齐此处常量，避免两端字段名漂移。
 * 对应文档：docs/项目手册.md 第八部分（Python 微服务集成契约，唯一标准基准）。
 */
public final class ContractPaths {

    private ContractPaths() {
    }

    // ===================== 配置键 =====================

    /** KG：是否启用 Python 知识图谱微服务（默认 false → 使用本地占位实现）。 */
    public static final String KG_ENABLED = "knowledge-graph.enabled";
    /** KG：Python 知识图谱微服务基址。 */
    public static final String KG_BASE_URL = "knowledge-graph.base-url";
    /** KG：基址默认值。 */
    public static final String KG_DEFAULT_BASE_URL = "http://localhost:8001";

    /** RAG：是否启用 Python RAG 微服务（默认 false → 使用本地占位实现，返回 503）。 */
    public static final String RAG_ENABLED = "rag.enabled";
    /** RAG：Python RAG 微服务基址。 */
    public static final String RAG_BASE_URL = "rag.base-url";
    /** RAG：基址默认值。 */
    public static final String RAG_DEFAULT_BASE_URL = "http://localhost:8002";

    // ===================== KG（8001）端点：HTTP 方法 + 路径 =====================

    public static final String KG_HEALTH = "/api/v1/graph/health";
    public static final String KG_PATH_PLAN = "/api/v1/graph/path/plan";
    public static final String KG_INGEST = "/api/v1/graph/ingest";
    public static final String KG_SUBGRAPH = "/api/v1/graph/subgraph";

    // ===================== RAG（8002）端点：HTTP 方法 + 路径 =====================

    public static final String RAG_HEALTH = "/api/v1/rag/health";
    public static final String RAG_ASK = "/api/v1/rag/ask";
    public static final String RAG_DOCUMENTS = "/api/v1/rag/documents";
    public static final String RAG_RETRIEVE = "/api/v1/rag/retrieve";
    public static final String RAG_INDEX_BUILD = "/api/v1/rag/index/build/{courseId}";
    public static final String RAG_INDEX = "/api/v1/rag/index/{courseId}";

    // ===================== 请求 JSON 字段名（Java ↔ Python 对齐） =====================

    public static final String F_SOURCE_ID = "source_id";
    public static final String F_TARGET_ID = "target_id";
    public static final String F_RELATION_TYPE = "relation_type";
    public static final String F_MAX_HOPS = "max_hops";
    public static final String F_COURSE_ID = "course_id";
    public static final String F_QUESTION = "question";
    public static final String F_QUERY = "query";
    public static final String F_TOP_K = "top_k";
    public static final String F_DOCUMENT_NAME = "document_name";
    public static final String F_CONTENT = "content";
    public static final String F_DOCUMENT_ID = "document_id";

    // ===================== 响应 JSON 字段名 =====================

    public static final String R_PATH = "path";
    public static final String R_EDGES = "edges";
    public static final String R_HOPS = "hops";
    public static final String R_STATUS = "status";
    public static final String R_ANSWER = "answer";
    public static final String R_SOURCES = "sources";
    public static final String R_RESULTS = "results";
    public static final String R_SUCCESS = "success";
    public static final String R_MODE = "mode";
    public static final String R_ERROR = "error";
    public static final String R_CODE = "code";

    // ===================== 状态与模式语义 =====================

    /** 引擎未接入/不可用时的 HTTP 状态码。 */
    public static final int CODE_UNAVAILABLE = 503;

    /** 路径/子图由 Python 知识图谱微服务计算返回。 */
    public static final String MODE_ENGINE = "ENGINE";
    /** 引擎未启用/不可达，由本端消费侧本地兜底（如本地 BFS）。 */
    public static final String MODE_LOCAL = "LOCAL";
    /** 引擎不可用且本地无兜底数据，仅返回降级占位。 */
    public static final String MODE_UNAVAILABLE = "UNAVAILABLE";

    public static final String MSG_KG_UNAVAILABLE =
            "知识图谱引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用";
    public static final String MSG_RAG_UNAVAILABLE =
            "RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用";
}
