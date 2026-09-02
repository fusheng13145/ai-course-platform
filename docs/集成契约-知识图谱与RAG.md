# 集成契约 · 知识图谱（8001）与 RAG（8002）

> 版本：v1.0 | 更新日期：2026-08-16 | 维护者：Java 后端（消费侧） + Python 服务（引擎侧，由团队另一成员负责）
>
> **本文档是 Java 后端与 Python 微服务之间「知识图谱 / RAG」对接契约的单一事实来源（Single Source of Truth）。**
> 所有 HTTP 路径、JSON 字段名、状态码与模式语义，均以 Java 端 `com.aicourse.integration.ContractPaths` 常量为准；Python 端实现须严格对齐，避免两端字段漂移。
> 旧版 `docs/运维手册.md` §11 的「Python 微服务接口契约」已归口至本文档，运维手册仅保留部署/配置要点并指向本文件。

---

## 1. 职责边界与设计意图

| 侧 | 职责 | 当前状态 |
|----|------|----------|
| Java 后端（本端） | **消费侧 + 标准化对接适配层**：定义契约接口、数据结构与调用约定；默认以「本地占位」运行，不依赖 Python；`enabled=true` 时经 HTTP 代理对接 Python。 | 已落地（`com.aicourse.integration` 包） |
| Python 知识图谱服务（8001） | 图谱引擎（图谱构建、多跳推理、GraphRAG）。 | **已有基线实现** `python-services/kg_service/`（FastAPI，内存存储，本地联调用） |
| Python RAG 检索服务（8002） | 检索引擎（BM25 / 向量检索 / embedding / 融合）。 | **仅接口预留**：Java 端未启用时返回 `503`，AI 问答自动回退纯 LLM |

**设计意图**：需求文档中的「分层 RAG」「知识图谱微服务」为规划能力，引擎实现由团队另一成员负责。本端**不实现引擎逻辑**，仅预留标准化接口与 HTTP 代理：
- 接入即用：Python 服务就绪后，将 `knowledge-graph.enabled` / `rag.enabled` 置 `true` 即生效，**无需改动业务代码**。
- 未接入不中断：默认本地占位 / `503` 降级，问答链路与路径规划端点始终可用。

### 1.1 剥离语义澄清（2026-09-02 审计锚定）

> **「知识图谱剥离」= 引擎侧剥离（职责转移），非功能移除。** 此澄清经 2026-09-02 全量审计锚定，与 `项目资料综合手册-最终版.md` 附录 D.3 互证，避免下游误读。

| 维度 | 内容 |
|------|------|
| 引擎侧（已剥离） | 图谱构建 / 多跳推理 / GraphRAG 检索 → 移交队友 Python 8001；RAG 向量 / embedding / 融合 → 移交队友 Python 8002；LoRA 微调 → 移交队友 Python 8003 |
| Java 端保留（消费侧） | `KnowledgeController` 27 个端点直读 MySQL `knowledge_points` / `knowledge_point_relations`；图谱可视化、关系维护、掌握度查询、AI 抽取等业务功能**完全可用** |
| 标准化契约（Java 端自含） | `com.aicourse.integration` 包（`ContractPaths` 常量、`KnowledgeGraphContract` / `RagContract` 接口、`LocalKnowledgeGraphProvider` / `DisabledRagProvider` 本地占位、`HttpKnowledgeGraphProvider` / `HttpRagProvider` HTTP 远端） |
| 本地兜底（永不中断） | `LocalKnowledgeGraphProvider.planLearningPath` 按起点知识点归属课程 `listByCourse` 隔离关系加载，引擎未启用 / 不可达 / 未找到路径时**始终返回真实路径**（`mode=LOCAL`） |
| 规划能力（P1，本仓库不实现） | 分层 RAG 中的 Neo4j / Milvus / Elasticsearch / FAISS / HyDE / Cross-Encoder 均为规划项，属职责边界内的合理预留而非缺陷 |

---

## 2. 集成层架构（Java 端 `com.aicourse.integration`）

```
                        业务接口（不变）
   KnowledgeGraphService          RAGService
            │                          │
            ▼ 门面（按 enabled 委派）    ▼
   KnowledgeGraphServiceImpl    RAGServiceImpl
            │                          │
            ▼                          ▼
   KnowledgeGraphContract       RagContract          ◀── 标准化接口 + 强类型 record
            ├── LocalKnowledgeGraphProvider  (默认, 不依赖 Python)
            └── HttpKnowledgeGraphProvider   (enabled, 调 8001)
                                         ├── DisabledRagProvider   (默认, 返回 503)
                                         └── HttpRagProvider        (enabled, 调 8002)
```

| 层级 | 类 / 接口 | 作用 |
|------|-----------|------|
| 常量 | `ContractPaths` | 调用约定唯一事实来源：配置键、端点路径、请求/响应字段名、状态码与模式语义 |
| 契约接口 | `KnowledgeGraphContract` / `RagContract` | 函数签名 + 数据结构（record）定义 |
| 本地占位 | `LocalKnowledgeGraphProvider` / `DisabledRagProvider` | 默认实现，不依赖 Python；KG 本地 BFS 兜底，RAG 返回 `503` |
| HTTP 远端 | `HttpKnowledgeGraphProvider` / `HttpRagProvider` | `enabled=true` 时按 `ContractPaths` 调 Python；复用 Sentinel 流控与带重试 HTTP 客户端 |
| 门面 | `KnowledgeGraphServiceImpl` / `RAGServiceImpl` | 实现业务接口，按 `enabled` 选择本地占位 / HTTP 远端；构造函数签名与业务接口完全不变 |

> **关键约定**：`ContractPaths` 是所有字段名与路径的唯一来源。任何 Java 侧 HTTP 调用或 Python 侧实现，都必须引用此处常量，禁止在业务代码中硬编码字符串。

---

## 3. 配置开关（如何切换本地 / Python）

经环境变量或 `application.yaml` 覆盖，无需改代码：

| 配置键（YAML） | 环境变量 | 默认值 | 说明 |
|----------------|----------|--------|------|
| `knowledge-graph.enabled` | `KG_ENABLED` | `false` | 启用 Python 知识图谱（8001）HTTP 代理 |
| `knowledge-graph.base-url` | `KG_BASE_URL` | `http://localhost:8001` | 8001 基址 |
| `knowledge-graph.connect-timeout` | `KG_CONNECT_TIMEOUT` | `5`（秒） | 连接超时 |
| `knowledge-graph.read-timeout` | `KG_READ_TIMEOUT` | `30`（秒） | 读取超时 |
| `knowledge-graph.max-retries` | `KG_MAX_RETRIES` | `2` | 重试次数（见 §7） |
| `knowledge-graph.health-path` | `KG_HEALTH_PATH` | `/api/v1/graph/health` | 健康检查路径 |
| `knowledge-graph.rag.max-hops` | `KG_RAG_MAX_HOPS` | `2` | 子图 / 路径 BFS 最大跳数 |
| `rag.enabled` | `RAG_ENABLED` | `false` | 启用 Python RAG（8002）HTTP 代理 |
| `rag.base-url` | `RAG_BASE_URL` | `http://localhost:8002` | 8002 基址 |
| `rag.connect-timeout` | `RAG_CONNECT_TIMEOUT` | `5`（秒） | 连接超时 |
| `rag.read-timeout` | `RAG_READ_TIMEOUT` | `60`（秒） | 读取超时 |
| `rag.max-retries` | `RAG_MAX_RETRIES` | `2` | 重试次数（见 §7） |
| `rag.default-top-k` | `RAG_DEFAULT_TOP_K` | `5` | 默认检索返回数量（请求未指定时） |
| `rag.health-path` | `RAG_HEALTH_PATH` | `/api/v1/rag/health` | 健康检查路径 |

示例：本地联调 Python 8001 基线
```bash
# 启动 Python 端（另一终端）
cd python-services/kg_service && pip install -r requirements.txt
uvicorn kg_service:app --host 0.0.0.0 --port 8001

# 启动 Java 端并开启知识图谱代理
KG_ENABLED=true KG_BASE_URL=http://localhost:8001 ./mvnw -o spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 4. 调用约定：知识图谱服务（8001）

### 4.1 端点总览

| HTTP 方法 | 路径 | 用途 | Java 端调用方 |
|-----------|------|------|----------------|
| `GET` | `/api/v1/graph/health` | 健康检查（任意 2xx 即健康） | `HttpKnowledgeGraphProvider.healthCheck()` |
| `POST` | `/api/v1/graph/path/plan` | 最短跳数路径规划 | `planLearningPath()` |
| `POST` | `/api/v1/graph/ingest` | 图谱摄取（确认已同步的知识点） | `ingest()` |
| `POST` | `/api/v1/graph/subgraph` | 按课程返回子图（节点 + 关系） | `getSubGraph()` |

> 节点/关系 CRUD（`/api/v1/graph/nodes`、`/api/v1/graph/relations` 等）为可选扩展基线，Java 端当前消费侧直读 MySQL，未调用；Python 端 `kg_service` 已实现供联调。

### 4.2 路径规划 `POST /api/v1/graph/path/plan`

请求体（字段名见 `ContractPaths`：`source_id` / `target_id` / `max_hops`）：
```json
{ "source_id": 1, "target_id": 12, "max_hops": 2 }
```

响应体（`path` / `edges` / `hops`）：
```json
{
  "path": [1, 5, 12],
  "edges": [
    {"source_id": 1, "target_id": 5, "relation_type": "PREREQUISITE"},
    {"source_id": 5, "target_id": 12, "relation_type": "PREREQUISITE"}
  ],
  "hops": 2
}
```
- **找不到路径**：返回 `{"path": [], "edges": [], "hops": 0}`，Java 端据此回退消费侧本地 BFS（直读 `knowledge_point_relations`），始终返回真实路径。
- 边关系类型枚举：`PREREQUISITE` / `INCLUDES` / `RELATED` / `EXTENDS`。

### 4.3 摄取 `POST /api/v1/graph/ingest`

请求：`{ "course_id": 1 }`
响应：`{ "status": "ok", "course_id": 1, "synced_nodes": 7 }`（Java 端解析 `synced_nodes` 或 `syncedPoints` 均可）

### 4.4 子图 `POST /api/v1/graph/subgraph`

请求：`{ "course_id": 1, "max_hops": 2 }`
响应：`{ "status": "ok", "course_id": 1, "max_hops": 2, "nodes": [...], "edges": [...] }`

---

## 5. 调用约定：RAG 检索服务（8002）

### 5.1 端点总览

| HTTP 方法 | 路径 | 用途 | Java 端调用方 |
|-----------|------|------|----------------|
| `GET` | `/api/v1/rag/health` | 健康检查 | `HttpRagProvider.healthCheck()` |
| `POST` | `/api/v1/rag/ask` | 基于知识库问答 | `ask()` |
| `POST` | `/api/v1/rag/documents` | 新增文档 | `addDocument()` |
| `GET` | `/api/v1/rag/documents?course_id={id}` | 文档列表 | `listDocuments()` |
| `DELETE` | `/api/v1/rag/documents/{documentId}` | 删除文档 | `deleteDocument()` |
| `PUT` | `/api/v1/rag/documents/{documentId}` | 更新文档 | `updateDocument()` |
| `POST` | `/api/v1/rag/index/build/{courseId}` | 构建课程索引 | `buildIndex()` |
| `DELETE` | `/api/v1/rag/index/{courseId}` | 清理课程索引 | `clearIndex()` |
| `POST` | `/api/v1/rag/retrieve` | 仅召回（不生成答案） | `retrieve()` |

### 5.2 问答 `POST /api/v1/rag/ask`

请求（`question` / `course_id`(可选) / `top_k`）：
```json
{ "question": "什么是梯度下降？", "course_id": 1, "top_k": 5 }
```
响应（`answer` / `sources[]`）：
```json
{
  "answer": "梯度下降是一种优化算法……",
  "sources": [
    {"document_id": 1, "document_name": "机器学习基础", "relevance_score": 0.95, "snippet": "……"}
  ]
}
```

### 5.3 文档与索引

- `POST /api/v1/rag/documents`：`{ "course_id": 1, "document_name": "机器学习基础", "content": "……" }` → `{ "id": 1, "document_name": "……", "course_id": 1, "content": "……" }`
- `GET /api/v1/rag/documents?course_id=1`：`{ "documents": [ { "id": 1, "document_name": "……" } ] }`
- `DELETE /api/v1/rag/documents/{documentId}`：`{ "success": true }`
- `PUT /api/v1/rag/documents/{documentId}`：`{ "content": "更新后内容" }` → `{ "id": 1, "document_name": "……" }`
- `POST /api/v1/rag/index/build/{courseId}`：`{ "success": true, "indexed_chunks": 50 }`
- `DELETE /api/v1/rag/index/{courseId}`：`{ "success": true }`

### 5.4 召回 `POST /api/v1/rag/retrieve`

请求：`{ "query": "梯度下降", "course_id": 1, "top_k": 5 }`
响应（`results[]`）：
```json
{ "results": [ { "document_id": 1, "document_name": "……", "relevance_score": 0.95, "snippet": "……" } ] }
```

> **RAG 流程要求（Python 端）**：① 向量/BM25 检索 top_k 片段 → ② 拼接上下文 → ③ 调用 LLM 生成答案 → ④ 返回答案 + 引用来源。

---

## 6. 状态码与模式语义

| 概念 | 取值 | 含义 |
|------|------|------|
| `code` | `503` | 引擎未接入 / 不可用（降级占位） |
| `mode` | `ENGINE` | 由 Python 引擎计算返回 |
| `mode` | `LOCAL` | 引擎未启用/不可达，由本端消费侧本地兜底（如本地 BFS） |
| `mode` | `UNAVAILABLE` | 引擎不可用且本地无兜底数据，仅返回降级占位 |
| `error` | `MSG_KG_UNAVAILABLE` / `MSG_RAG_UNAVAILABLE` | 降级说明文案（见 `ContractPaths`） |

通用约定：
- 所有请求/响应 `Content-Type: application/json`。
- 健康检查返回 `200` 即可（body 可为 `{}`）。
- 错误响应：`{ "error": "……", "code": 503 }`。

---

## 7. 重试与超时（契约约束）

`HttpKnowledgeGraphProvider` / `HttpRagProvider` 复用 Sentinel 资源（`kg:http` / `rag:http`，方法级流控 + 慢调用熔断）与带重试 HTTP 客户端：

- **非超时异常**（如连接拒绝 `ConnectException`）：按 `max-retries` 重试，重试间隔随次数递增（`sleep(i+1)` 秒）。
- **读超时（`SocketTimeoutException`，含经 Spring `RestClient` 包装为 `ResourceAccessException` 的情况）**：**不重试**，直接降级。理由：超时重试会放大雪崩、无效等待。
- 重试耗尽或超时：KG 的 `ingest` / `getSubGraph` 与 RAG 全部方法返回 `503` 降级占位；KG 的 `planLearningPath` 回退本地 BFS（始终返回真实路径）。

> 该「超时不重试」语义由 `isReadTimeout(Exception)` 判定（同时识别 `SocketTimeoutException` 本身及其作为 cause 被包装的情况），契约测试 `HttpRagProviderBoostTest.ask_socketTimeout_noRetry` 锁定。

---

## 8. 降级策略（实际行为）

| 场景 | 实际行为 |
|------|----------|
| KG 路径规划（默认 / 引擎不可达 / 引擎未找到路径） | 消费侧本地 BFS 兜底（直读 `knowledge_point_relations`），始终返回真实路径（`mode=LOCAL` 或未找到 `found=false`） |
| KG `ingest` / `getSubGraph`（引擎不可达） | 返回 `503` 占位（`mode=UNAVAILABLE`） |
| RAG 未启用（`rag.enabled=false`，默认） | 所有方法返回 `503` 占位（`DisabledRagProvider`） |
| RAG 启用但不可达 / 调用失败 | 返回 `503` 占位；`AIServiceImpl.ragAsk` 检测到 `code:503` 自动回退纯 LLM 调用，问答链路不中断 |

---

## 9. Java 端数据结构速查（强类型 record）

**`KnowledgeGraphContract`**
- `PathPlanRequest(Long sourceId, Long targetId, int maxHops)`
- `PathEdge(Long sourceId, Long targetId, String relationType)`
- `PathPlanResult(Long sourceId, Long targetId, List<Long> path, List<PathEdge> edges, int hops, boolean found, Mode mode, String note)`
- `IngestResult(int code, String status, String mode, Long courseId, long syncedPoints, String note)`
- `SubGraphResult(int code, String status, String mode, Long courseId, int maxHops, List<Object> nodes, List<Object> edges, String note)`
- `enum Mode { ENGINE, LOCAL, UNAVAILABLE }`

**`RagContract`**（响应记录均 `@JsonInclude(NON_NULL)`，降级时仅 `error`/`code` 非空）
- `RagAskRequest(String question, Long courseId, int topK)` / `RagRetrieveRequest(String query, Long courseId, int topK)`
- `RagQueryResult(String answer, List<RagSource> sources, String mode, String error, Integer code)`
- `RagDocument(Long id, String documentName, Long courseId, String content, String mode, String error, Integer code)`
- `RagDocumentList(List<RagDocument> documents, String error, Integer code)`
- `IndexResult(Boolean success, Integer indexedChunks, String error, Integer code)`
- `RagRetrievalResult(List<RagSource> results, String error, Integer code)`
- `RagSource(Long documentId, String documentName, Double relevanceScore, String snippet)`

---

## 10. Python 端实现指引

- **知识图谱（8001）**：已有基线 `python-services/kg_service/`（FastAPI，内存存储）。端点与字段见 §4，与 `ContractPaths` 完全对齐；生产应替换为持久化存储（Neo4j / NebulaGraph）+ 真实多跳推理引擎。
- **RAG（8002）**：**待实现**。Python 端须按 §5 实现 `/api/v1/rag/*` 契约；实现后通过 `RAG_ENABLED=true` 接入即可，Java 端无需改动。
- **字段对齐强制项**：请求体用 `source_id` / `target_id` / `max_hops` / `course_id` / `question` / `query` / `top_k` / `document_name` / `content` / `document_id`；路径规划响应用 `path` / `edges` / `hops`；问答响应用 `answer` / `sources`。任意一侧新增字段，须同步更新 `ContractPaths` 常量。

---

## 11. 契约保护（测试）

以下测试锁定调用约定，CI 离线可运行，**禁止删除或绕过**：

| 测试类 | 覆盖 |
|--------|------|
| `KnowledgeGraphServiceImplTest` | 门面委派：禁用→本地 BFS；启用+可达→`ENGINE`；启用+不可达→降级 |
| `RAGServiceImplTest` | 门面委派：禁用→`503`；启用+可达→引擎响应 |
| `LocalKnowledgeGraphProviderTest` | 本地占位：BFS / ingest / 子图 / 健康 false |
| `DisabledRagProviderTest` | 本地占位：全部方法 `503` |
| `HttpKnowledgeGraphProviderContractTest` | 8001 HTTP 契约（路径/字段/解析/降级），JDK `HttpServer` 桩 |
| `HttpRagProviderContractTest` | 8002 HTTP 契约（全动词/解析/降级） |
| `HttpRagProviderBoostTest` | 重试语义（超时no-retry / 非超时retry / 耗尽→503）、分支覆盖 |

---

## 12. 文档关联

- 架构设计：见 `docs/架构手册.md`（§6 集成层架构视图与本文档互为补充）
- 开发指南：见 `docs/开发指南.md`（本地联调 Python 基线、契约测试清单 §5）
- 部署 / 配置 / 监控：见 `docs/运维手册.md`（第 11 章现为本文档指针，配置与降级要点保留在本文档 §3 / §8）
- 产品需求、战略规划、交付与完成度：见 `docs/项目资料综合手册-最终版.md`（第七~九章）
