package com.aicourse.integration;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 集成契约常量（调用约定的唯一事实来源）。
 * <p>
 * 知识图谱（KG，端口 8001）、RAG（端口 8002）与微调（FT，端口 8003）三个 Python 微服务由团队另一成员负责，
 * 本端（Java 后端）实现<b>消费侧</b>与<b>标准化对接契约</b>。本类集中定义：
 * <ul>
 *   <li>配置键（{@code knowledge-graph.*} / {@code rag.*} / {@code fine-tune.*}），用于按 {@code enabled} 切换本地占位 / 远端 Python；</li>
 *   <li>HTTP 方法 + 路径（端点契约）；</li>
 *   <li>请求/响应 JSON 字段名（字段级调用约定）；</li>
 *   <li>状态码与模式语义（{@code 503} = 引擎未接入/不可用；{@code ENGINE}/{@code LOCAL}/{@code UNAVAILABLE}）。</li>
 * </ul>
 * 任何 Java 侧 HTTP 调用或 Python 侧实现都必须严格对齐此处常量，避免两端字段名漂移。
 * 对应文档：docs/系统架构设计.md 的集成契约章节（Python 微服务契约，唯一标准基准）。
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
    /** RAG：基址默认值。8002 已于批次 3B-2 退役，知识库端点并入 8501（deepgraph 进程）。 */
    public static final String RAG_DEFAULT_BASE_URL = "http://localhost:8501";

    /** FT：是否启用 Python 微调微服务（默认 false → 使用本地占位 / 503 降级）。 */
    public static final String FT_ENABLED = "fine-tune.enabled";
    /** FT：Python 微调微服务基址。 */
    public static final String FT_BASE_URL = "fine-tune.base-url";
    /** FT：基址默认值。 */
    public static final String FT_DEFAULT_BASE_URL = "http://localhost:8003";

    /**
     * DeepGraph RAG：是否启用文档图谱 RAG 微服务（deepgraph_rag，端口 8501，默认 false → 不参与问答链）。
     * <p>该服务由已验收的 DeepSeek-RAG-Chatbot（原样复制于 {@code python-services/deepgraph_rag/}）
     * 经 {@code api_server.py} 契约适配器接入，作为 8002 知识点语料检索之外的<b>第二检索源</b>
     * （整篇课程文档的 FAISS+BM25 混合召回 + Neo4j GraphRAG 关系上下文 + HyDE/重排）。</p>
     */
    public static final String DEEPGRAPH_ENABLED = "deepgraph-rag.enabled";
    /** DeepGraph RAG：微服务基址。 */
    public static final String DEEPGRAPH_BASE_URL = "deepgraph-rag.base-url";
    /** DeepGraph RAG：基址默认值。 */
    public static final String DEEPGRAPH_DEFAULT_BASE_URL = "http://localhost:8501";

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

    // ===================== FT（8003）端点：HTTP 方法 + 路径 =====================

    public static final String FT_HEALTH = "/api/v1/fine-tune/health";
    public static final String FT_START = "/api/v1/fine-tune/start";
    public static final String FT_TASK_STATUS = "/api/v1/fine-tune/tasks/{taskId}/status";
    public static final String FT_TASKS = "/api/v1/fine-tune/tasks";
    public static final String FT_TASK_CANCEL = "/api/v1/fine-tune/tasks/{taskId}/cancel";
    public static final String FT_INFER = "/api/v1/fine-tune/infer/{taskId}";
    public static final String FT_TASK_EXPORT = "/api/v1/fine-tune/tasks/{taskId}/export";
    public static final String FT_TASK = "/api/v1/fine-tune/tasks/{taskId}";

    // ===================== DeepGraph RAG（8501）端点：HTTP 方法 + 路径 =====================

    public static final String DEEPGRAPH_HEALTH = "/api/v1/deepgraph/health";
    public static final String DEEPGRAPH_RETRIEVE = "/api/v1/deepgraph/retrieve";
    public static final String DEEPGRAPH_QA = "/api/v1/deepgraph/qa";
    public static final String DEEPGRAPH_INGEST = "/api/v1/deepgraph/ingest";
    public static final String DEEPGRAPH_INGEST_FILE = "/api/v1/deepgraph/ingest-file";
    public static final String DEEPGRAPH_DOCUMENTS = "/api/v1/deepgraph/documents";
    public static final String DEEPGRAPH_DOCUMENT = "/api/v1/deepgraph/documents/{documentName}";
    public static final String DEEPGRAPH_CHAPTER_GRAPH = "/api/v1/deepgraph/chapter-graph";
    public static final String DEEPGRAPH_LEARNING_PATH = "/api/v1/deepgraph/learning-path";
    /** 课程图谱聚合（全部章节图谱 + 层次树，供同步进 MySQL knowledge_points/relations）。 */
    public static final String DEEPGRAPH_COURSE_GRAPH = "/api/v1/deepgraph/course-graph";

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

    // DeepGraph RAG 响应扩展字段（8501 契约）
    /** 检索响应：Neo4j GraphRAG 关系上下文（“- 实体 (关系) 实体”行集合）。 */
    public static final String R_GRAPH_CONTEXT = "graph_context";
    /** 问答响应：发给大模型的完整 Prompt（可解释/排查用）。 */
    public static final String R_LLM_PROMPT = "llm_prompt";
    /** 文档入库响应：服务端落盘名称（course{id}_ 前缀命名空间）。 */
    public static final String R_STORED_AS = "stored_as";
    /** 文档入库响应：向量库分块数。 */
    public static final String R_CHUNK_COUNT = "chunk_count";
    /** 文档入库响应：是否构建了知识图谱。 */
    public static final String R_HAS_GRAPH = "has_graph";
    /** 文档入库响应：图谱关系边数。 */
    public static final String R_GRAPH_EDGES = "graph_edges";
    /** 学习路径响应：结构化路径文本（Markdown）。 */
    public static final String R_PATH_TEXT = "path_text";
    /** 检索来源：召回通道（VECTOR / GRAPH 等，透传展示用）。 */
    public static final String R_RETRIEVAL = "retrieval";

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
    public static final String MSG_DEEPGRAPH_UNAVAILABLE =
            "文档图谱 RAG 引擎（deepgraph_rag）未接入或不可用";

    // ===================== 契约判定（唯一来源，provider 不得各写一份） =====================

    /**
     * 判定 Python 侧响应体是否声明「引擎不可用」。
     *
     * <p><b>为什么放在这里</b>（I-1，2026-11-15）：原先该判定是 {@code HttpRagProvider} 的私有方法
     * {@code isEngineUnavailable}，判定规则与 {@code code}/{@code mode} 字段语义同处一地却不在契约类里。
     * 任何新增 provider 或解析路径都会各写一份，规则必然分叉（AGENTS §4.3）。现上提为契约唯一来源。</p>
     *
     * <p><b>判定口径</b>：{@code body.code == 503} 或 {@code body.mode == "UNAVAILABLE"} 任一成立即视为不可用。
     * 二者是 Python 侧同一语义的两种表达，历史上都出现过，因此两条并列而非二选一。</p>
     *
     * @param root 已解析的响应体节点，可为 {@code null}
     * @return true=引擎不可用，调用方应走降级而非「空结果」
     */
    public static boolean isUnavailable(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        if (root.has(R_CODE) && root.get(R_CODE).asInt() == CODE_UNAVAILABLE) {
            return true;
        }
        if (root.has(R_MODE)) {
            JsonNode mode = root.get(R_MODE);
            return !mode.isNull() && MODE_UNAVAILABLE.equals(mode.asText());
        }
        return false;
    }
}
