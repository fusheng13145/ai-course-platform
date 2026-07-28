# Python 微服务接口契约（更新版 · 2026-07-23）

> 本文档定义 Java 后端（Spring Boot）与可选的 Python 微服务之间的 HTTP API 契约。
> **状态说明（重要）**：经统一重构后，知识图谱（8001）与 RAG（8002）的 Python 微服务**并未随项目交付**，其能力已由 Java 端**自包含实现**接管，当前**不再调用**外部 Python 进程。仅 **LoRA 微调（8003）** 仍保留真实 Python 集成（默认关闭，启用时调用）。
>
> 本文档保留 8001/8002 的完整契约作为**可选扩展基线**：若后续引入 Neo4j / Milvus / Python 向量服务，可按此契约实现并重新开启 `knowledge-graph` / `rag` 配置开关。

---

## 0. 当前实现状态总览

| 服务 | 端口 | Java 当前实现 | 是否调用 Python | 配置开关 |
|------|------|--------------|----------------|----------|
| 知识图谱 (8001) | 8001 | `KnowledgeGraphServiceImpl` 直读 MySQL `knowledge_points` / `knowledge_point_relations`，返回 ECharts/D3 兼容图结构 | **否（自包含）** | `application.yaml` 中 `knowledge-graph.base-url` 配置键保留但**当前未使用** |
| RAG 检索增强 (8002) | 8002 | `RAGServiceImpl`：语料取自 `knowledge_points` 表，召回用「词频/IDF 加权中文 bigram 余弦相似度」，生成交由可插拔 `LLMProvider`（DeepSeek/Qwen/OpenAI），未配 Key 时回退展示知识库原文 | **否（自包含）** | `application.yaml` 中 `rag.base-url` 配置键保留但**当前未使用** |
| LoRA 微调 (8003) | 8003 | `LLMFineTuneServiceImpl` 经 `RestClient` 调用 Python | **是（启用时）** | `fine-tune.enabled`（默认 `false`） |

**设计意图**：需求文档中的「分层 RAG（Neo4j + Milvus + Elasticsearch）」与「知识图谱微服务」为规划能力；当前以零外部依赖的自包含实现保证功能可用，后续可无痛替换为 Python 链路（只需替换 `RAGServiceImpl.retrieve` 的召回实现 / 接入 `KnowledgeGraphServiceImpl` 的向量召回）。

---

## 1. 服务部署规格（契约基线）

| 服务 | 端口 | 健康检查路径 | 基路径 |
|------|------|-------------|--------|
| 知识图谱服务 | **8001** | `GET /api/v1/graph/health` | `/api/v1/graph` |
| RAG 检索增强服务 | **8002** | `GET /api/v1/rag/health` | `/api/v1/rag` |
| LoRA 微调服务 | **8003** | `GET /api/v1/fine-tune/health` | `/api/v1/fine-tune` |

**通用约定：**
- 所有请求/响应均使用 `Content-Type: application/json`
- 健康检查接口返回 `200 OK` 即可（body 可为空 `{}`）
- 错误响应格式：`{"error": "错误描述", "code": 503}`

---

## 2. 知识图谱服务（端口 8001）— 可选契约

> 当前 Java 端不调用此服务。以下为若启用 Python KG 微服务时应实现的契约。

### 2.1 健康检查
```
GET /api/v1/graph/health
Response: 200 OK {}
```

### 2.2 获取课程知识图谱
```
GET /api/v1/graph/course/{course_id}
Response: {
  "nodes": [
    {"id": 1, "name": "线性回归", "type": "CONCEPT", "content": "...", "course_id": 1,
     "properties": {"difficulty": "medium", "importance": "high"}}
  ],
  "edges": [
    {"source_id": 1, "target_id": 2, "relation_type": "PREREQUISITE"}
  ]
}
```

### 2.3 获取单个节点
```
GET /api/v1/graph/nodes/{node_id}
Response: {"id": 1, "name": "线性回归", "type": "CONCEPT", "content": "...", "course_id": 1}
```

### 2.4 获取节点关联关系
```
GET /api/v1/graph/nodes/{node_id}/relations?depth={depth}
Response: {
  "node": {"id": 1, "name": "线性回归"},
  "relations": [
    {"node": {"id": 2, "name": "梯度下降"}, "relation_type": "PREREQUISITE", "depth": 1},
    {"node": {"id": 3, "name": "损失函数"}, "relation_type": "RELATED", "depth": 1}
  ]
}
```

### 2.5 添加知识节点
```
POST /api/v1/graph/nodes
Body: {"course_id": 1, "name": "线性回归", "content": "...", "type": "CONCEPT"}
Response: {"id": 1, "name": "线性回归", "created_at": "2026-06-18T10:00:00"}
```

### 2.6 添加节点关系
```
POST /api/v1/graph/relations
Body: {"source_id": 1, "target_id": 2, "relation_type": "PREREQUISITE"}
Response: {"id": 1, "source_id": 1, "target_id": 2, "relation_type": "PREREQUISITE"}
```

**关系类型枚举：** `PREREQUISITE`（前置依赖）/ `INCLUDES`（包含）/ `RELATED`（相关）/ `EXTENDS`（扩展）。

### 2.7 删除节点
```
DELETE /api/v1/graph/nodes/{node_id}
Response: {"success": true}
```

### 2.8 删除关系
```
DELETE /api/v1/graph/relations/{relation_id}
Response: {"success": true}
```

---

## 3. RAG 检索增强服务（端口 8002）— 可选契约

> 当前 Java 端不调用此服务，改由 `RAGServiceImpl` 自包含实现（knowledge_points + TF-IDF 余弦 + LLMProvider）。以下为若启用 Python RAG 微服务时应实现的契约。

### 3.1 健康检查
```
GET /api/v1/rag/health
Response: 200 OK {}
```

### 3.2 基于知识库的问答（核心接口）
```
POST /api/v1/rag/ask
Body: {"question": "什么是梯度下降？", "course_id": 1, "top_k": 5}
Response: {
  "answer": "梯度下降是一种优化算法...",
  "sources": [{"document_id": 1, "document_name": "机器学习基础", "relevance_score": 0.95, "excerpt": "..."}],
  "model": "deepseek-chat", "tokens_used": 512
}
```

**RAG 流程要求：**
1. 向量检索：用 `question` 在 Milvus/FAISS 向量库中检索 top_k 个相关文档片段
2. 上下文拼接：将检索到的文档片段作为上下文拼入 Prompt
3. LLM 生成：调用大模型基于上下文生成答案
4. 返回答案 + 引用来源

### 3.3 添加文档到知识库
```
POST /api/v1/rag/documents
Body: {"course_id": 1, "document_name": "机器学习基础", "content": "..."}
Response: {"document_id": 1, "status": "indexed", "chunks": 5}
```

### 3.4 删除文档
```
DELETE /api/v1/rag/documents/{document_id}
Response: {"success": true}
```

### 3.5 获取文档列表
```
GET /api/v1/rag/documents?course_id={course_id}
Response: {"documents": [{"id": 1, "name": "机器学习基础", "chunks": 5, "created_at": "2026-06-18"}]}
```

### 3.6 更新文档
```
PUT /api/v1/rag/documents/{document_id}
Body: {"content": "更新后的文档内容..."}
Response: {"success": true, "chunks": 6}
```

### 3.7 构建/重建向量索引
```
POST /api/v1/rag/index/build/{course_id}
Response: {"success": true, "indexed_chunks": 50}
```

### 3.8 清理课程索引
```
DELETE /api/v1/rag/index/{course_id}
Response: {"success": true}
```

### 3.9 纯检索（不生成答案）
```
POST /api/v1/rag/retrieve
Body: {"query": "梯度下降", "course_id": 1, "top_k": 5}
Response: {"results": [{"document_id": 1, "document_name": "...", "chunk_text": "...", "relevance_score": 0.95}]}
```

---

## 4. LoRA 微调服务（端口 8003）— 活跃契约

> **当前唯一被 Java 端真实调用的 Python 服务**（`LLMFineTuneServiceImpl`）。默认 `fine-tune.enabled=false`，启用后通过 `RestClient` 调用以下接口。

### 4.1 健康检查
```
GET /api/v1/fine-tune/health
Response: 200 OK {}
```

### 4.2 启动微调任务
```
POST /api/v1/fine-tune/start
Body: {
  "course_id": 1, "base_model": "deepseek-chat",
  "config": {"epochs": 3, "learning_rate": 5e-5, "lora_rank": 8, "lora_alpha": 16, "lora_dropout": 0.1, "target_modules": ["q_proj", "v_proj"]}
}
Response: {"task_id": "ft_20260618_001", "status": "pending", "created_at": "2026-06-18T10:00:00"}
```

### 4.3 查询任务状态
```
GET /api/v1/fine-tune/tasks/{task_id}/status
Response: {"task_id": "ft_20260618_001", "status": "running", "progress": 45.5, "current_epoch": 2, "total_epochs": 3, "loss": 0.234, "started_at": "...", "estimated_completion": "..."}
```

### 4.4 列出任务
```
GET /api/v1/fine-tune/tasks
GET /api/v1/fine-tune/tasks?course_id=1
Response: {"tasks": [{"task_id": "ft_20260618_001", "status": "completed", "course_id": 1, "base_model": "deepseek-chat"}]}
```

### 4.5 取消任务
```
POST /api/v1/fine-tune/tasks/{task_id}/cancel
Response: {"success": true, "status": "cancelled"}
```

### 4.6 微调模型推理
```
POST /api/v1/fine-tune/infer/{task_id}
Body: {"prompt": "请解释什么是梯度下降？", "params": {"temperature": 0.7, "max_tokens": 2048}}
Response: {"answer": "梯度下降是...", "tokens_used": 256, "model": "deepseek-chat-lora-ft_20260618_001"}
```

### 4.7 导出 LoRA 权重
```
GET /api/v1/fine-tune/tasks/{task_id}/export
Response: {"download_url": "http://localhost:8003/weights/ft_20260618_001.zip", "file_size": "15.2MB", "format": "peft_lora"}
```

### 4.8 删除任务及模型
```
DELETE /api/v1/fine-tune/tasks/{task_id}
Response: {"success": true}
```

---

## 5. 配置说明

Java 后端通过 `application.yaml` 控制服务地址（KG/RAG 配置键当前未使用，保留以备扩展）：

```yaml
# 知识图谱服务（当前 Java 自包含，配置键保留但未被读取）
knowledge-graph:
  base-url: ${KG_BASE_URL:http://localhost:8001}

# RAG 服务（当前 Java 自包含，配置键保留但未被读取）
rag:
  base-url: ${RAG_BASE_URL:http://localhost:8002}

# LoRA 微调服务（启用时真实调用）
fine-tune:
  enabled: ${FT_ENABLED:false}
  base-url: ${FT_BASE_URL:http://localhost:8003}
  connect-timeout: 5
  read-timeout: 300
  max-retries: 1
  health-path: /api/v1/fine-tune/health
```

**环境变量覆盖：**
- `KG_BASE_URL` / `RAG_BASE_URL` / `FT_BASE_URL` — 服务地址
- `FT_ENABLED` — 微调服务启用开关（KG/RAG 无独立 enabled 开关，因其当前由 Java 自包含实现）

---

## 6. 降级策略（当前实际行为）

| 场景 | 实际行为 |
|------|----------|
| 知识图谱 | Java 自包含直读 MySQL，始终可用；不依赖 Python 8001 |
| RAG 问答 | Java 自包含（knowledge_points + TF-IDF 召回 + LLMProvider）；未配 LLM Key 时回退展示知识库原文 |
| 微调服务（8003）未启用/不可用 | `LLMFineTuneServiceImpl` 返回 `{"error":"微调服务暂不可用","code":503}` |

---

## 7. 关联文档

- 需求基线：`docs/AI课程平台-产品需求文档.md`（合并权威版，含分层 RAG / 知识图谱规划与当前自包含实现的说明）
- 交付清单：`docs/项目交付与完成度报告.md`
