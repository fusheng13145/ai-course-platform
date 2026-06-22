# Python 微服务接口契约

> 本文档定义 Java 后端（Spring Boot）与 Python 微服务之间的 HTTP API 契约。
> **Python 端需严格按照本文档实现所有接口。**

---

## 1. 服务部署规格

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

## 2. 知识图谱服务（端口 8001）

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
    {
      "id": 1,
      "name": "线性回归",
      "type": "CONCEPT",
      "content": "线性回归是一种统计方法...",
      "course_id": 1,
      "properties": {"difficulty": "medium", "importance": "high"}
    }
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
Body: {
  "course_id": 1,
  "name": "线性回归",
  "content": "线性回归是一种统计方法，用于建模因变量与一个或多个自变量之间的线性关系。",
  "type": "CONCEPT"
}
Response: {"id": 1, "name": "线性回归", "created_at": "2026-06-18T10:00:00"}
```

### 2.6 添加节点关系
```
POST /api/v1/graph/relations
Body: {
  "source_id": 1,
  "target_id": 2,
  "relation_type": "PREREQUISITE"
}
Response: {"id": 1, "source_id": 1, "target_id": 2, "relation_type": "PREREQUISITE"}
```

**关系类型枚举：**
- `PREREQUISITE` — 前置依赖（必须先学A才能学B）
- `INCLUDES` — 包含关系（A包含B）
- `RELATED` — 相关关系（A和B相关）
- `EXTENDS` — 扩展关系（B是A的扩展/深化）

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

## 3. RAG 检索增强服务（端口 8002）

### 3.1 健康检查
```
GET /api/v1/rag/health
Response: 200 OK {}
```

### 3.2 基于知识库的问答（核心接口）
```
POST /api/v1/rag/ask
Body: {
  "question": "什么是梯度下降？",
  "course_id": 1,
  "top_k": 5
}
Response: {
  "answer": "梯度下降是一种优化算法，用于最小化损失函数...",
  "sources": [
    {"document_id": 1, "document_name": "机器学习基础", "relevance_score": 0.95, "excerpt": "..."}
  ],
  "model": "deepseek-chat",
  "tokens_used": 512
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
Body: {
  "course_id": 1,
  "document_name": "机器学习基础",
  "content": "机器学习是人工智能的一个分支..."
}
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
Response: {
  "documents": [
    {"id": 1, "name": "机器学习基础", "chunks": 5, "created_at": "2026-06-18"},
    {"id": 2, "name": "深度学习入门", "chunks": 8, "created_at": "2026-06-18"}
  ]
}
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
Body: {
  "query": "梯度下降",
  "course_id": 1,
  "top_k": 5
}
Response: {
  "results": [
    {"document_id": 1, "document_name": "...", "chunk_text": "...", "relevance_score": 0.95}
  ]
}
```

---

## 4. LoRA 微调服务（端口 8003）

### 4.1 健康检查
```
GET /api/v1/fine-tune/health
Response: 200 OK {}
```

### 4.2 启动微调任务
```
POST /api/v1/fine-tune/start
Body: {
  "course_id": 1,
  "base_model": "deepseek-chat",
  "config": {
    "epochs": 3,
    "learning_rate": 5e-5,
    "lora_rank": 8,
    "lora_alpha": 16,
    "lora_dropout": 0.1,
    "target_modules": ["q_proj", "v_proj"]
  }
}
Response: {
  "task_id": "ft_20260618_001",
  "status": "pending",
  "created_at": "2026-06-18T10:00:00"
}
```

### 4.3 查询任务状态
```
GET /api/v1/fine-tune/tasks/{task_id}/status
Response: {
  "task_id": "ft_20260618_001",
  "status": "running",
  "progress": 45.5,
  "current_epoch": 2,
  "total_epochs": 3,
  "loss": 0.234,
  "started_at": "2026-06-18T10:00:00",
  "estimated_completion": "2026-06-18T10:30:00"
}
```

### 4.4 列出任务
```
GET /api/v1/fine-tune/tasks
GET /api/v1/fine-tune/tasks?course_id=1
Response: {
  "tasks": [
    {"task_id": "ft_20260618_001", "status": "completed", "course_id": 1, "base_model": "deepseek-chat"}
  ]
}
```

### 4.5 取消任务
```
POST /api/v1/fine-tune/tasks/{task_id}/cancel
Response: {"success": true, "status": "cancelled"}
```

### 4.6 微调模型推理
```
POST /api/v1/fine-tune/infer/{task_id}
Body: {
  "prompt": "请解释什么是梯度下降？",
  "params": {"temperature": 0.7, "max_tokens": 2048}
}
Response: {
  "answer": "梯度下降是...",
  "tokens_used": 256,
  "model": "deepseek-chat-lora-ft_20260618_001"
}
```

### 4.7 导出 LoRA 权重
```
GET /api/v1/fine-tune/tasks/{task_id}/export
Response: {
  "download_url": "http://localhost:8003/weights/ft_20260618_001.zip",
  "file_size": "15.2MB",
  "format": "peft_lora"
}
```

### 4.8 删除任务及模型
```
DELETE /api/v1/fine-tune/tasks/{task_id}
Response: {"success": true}
```

---

## 5. 配置说明

Java 后端通过 `application.yaml` 控制服务开关：

```yaml
# 知识图谱服务
knowledge-graph:
  enabled: true          # 设为 false 可禁用，Java端返回503
  base-url: http://localhost:8001

# RAG 服务
rag:
  enabled: true
  base-url: http://localhost:8002

# LoRA 微调服务
fine-tune:
  enabled: false         # 默认关闭，需要时开启
  base-url: http://localhost:8003
```

**环境变量覆盖：**
- `KG_BASE_URL` — 知识图谱服务地址
- `RAG_BASE_URL` — RAG 服务地址
- `FT_BASE_URL` — 微调服务地址
- `KG_ENABLED` / `RAG_ENABLED` / `FT_ENABLED` — 启用开关

---

## 6. 降级策略

当 Python 服务不可用时，Java 后端的行为：

| 场景 | 降级行为 |
|------|---------|
| 知识图谱不可用 | 返回 `{"error":"知识图谱服务暂不可用","code":503}` |
| RAG 不可用 | `ragAsk()` 降级为纯 LLM 调用（无检索增强） |
| 微调服务不可用 | 返回 `{"error":"微调服务暂不可用","code":503}` |