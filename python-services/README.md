# Python 微服务（python-services）

> 职责边界（2026-08-13 生效）：知识图谱引擎与 RAG 检索引擎由团队另一成员负责，以 Python 微服务实现。本目录保存**契约基线/占位实现**，供本地联调与契约验证；生产引擎实现由引擎负责人按契约落地。

## 服务一览

| 服务 | 端口 | 状态 | 目录 |
|------|------|------|------|
| 知识图谱（KG） | 8001 | **已有基线实现**（FastAPI，内存存储，本地联调用） | `kg_service/` |
| RAG 检索 | 8002 | **仅接口预留**（Java 端 `rag.enabled=false` 时返回 `503` 降级） | — |
| LoRA 微调 | 8003 | Java 端 `LLMFineTuneServiceImpl` 真实调用（`fine-tune.enabled=true` 时启用） | — |

## 知识图谱基线（kg_service，8001）

```bash
cd kg_service
pip install -r requirements.txt
uvicorn kg_service:app --host 0.0.0.0 --port 8001
```

- 实现端点：`/api/v1/graph/health`、`/api/v1/graph/course/{course_id}`、`/api/v1/graph/nodes`、`/api/v1/graph/relations`、`/api/v1/graph/path/plan`、`/api/v1/graph/ingest`、`/api/v1/graph/subgraph` 等。
- 契约冒烟测试：`pytest -q contract_test.py`（FastAPI TestClient 内嵌运行，CI 的 `python-kg-contract` job 即执行此测试）。
- **注意**：内存字典存储，重启即清空，仅用于本地联调与契约验证；生产应替换为持久化存储（Neo4j / NebulaGraph）与真实多跳推理引擎。

## 对接方式（Java 端启用）

```bash
# 启动 KG 基线后，启用 Java 端对接
KG_ENABLED=true KG_BASE_URL=http://localhost:8001 ./mvnw -o spring-boot:run -Dspring-boot.run.profiles=local
# RAG（8002）同理：RAG_ENABLED=true RAG_BASE_URL=http://localhost:8002
```

> 完整契约（端点、JSON 字段、状态码、降级、重试）与本地联调步骤见 `docs/项目手册.md`（第八部分 Python 微服务集成契约；第六部分 §24.5 本地联调）。
