# 第三方资产声明（THIRD_PARTY NOTICES）

本仓库**不再包含任何第三方项目源码**。本文件记录归属事实、历史来源追溯方式，
以及直接运行时依赖的许可证摘要，供申报 / 软著 / 合规审查使用。
项目自有代码的许可见根目录 `LICENSE`。

---

## 1. 归属事实：Python 引擎全部为本项目自有实现

2026-11-25「第三方内容清零」完成后，`python-services/deepgraph_rag/` 目录下的实现
文件全部为本项目自研代码，与原参考实现不再有文件级、行级、字节级关联：

| 文件 | 职责 | 状态 |
| --- | --- | --- |
| `config.py` | 运行配置（只读环境变量，无内置凭据） | 自有重写 |
| `backend.py` | 生成层（LLM 调用 / 实体抽取 / 章节图谱入口） | 自有重写 |
| `neo4j_handler.py` | 图谱存储（三元组入库 / 上下文查询 / 学习路径） | 自有重写 |
| `utils/persistence.py` | 文档 / 索引 / 章节 / 图谱落盘 | 自有重写 |
| `utils/doc_handler.py` | 文档接入流水线 | 自有重写 |
| `utils/build_graph.py` | 层次大纲 / 三元组抽取 / 图检索 | 自有重写 |
| `utils/retriever_pipeline.py` | 混合检索（HyDE + BM25/FAISS + 图 + 重排） | 自有重写 |
| `runtime_state.py` | 进程内运行态（替代原实现的 Streamlit 会话） | 自有新增 |
| `requirements.txt` | 引擎运行面依赖清单 | 自有重写 |
| `api_server.py` / `knowledge_api.py` / `knowledge_engine.py` | FastAPI 契约服务与知识库 | 自有代码 |
| `test_*.py` | 契约 / 归属 / 合并守卫测试 | 自有代码 |

被删除的第三方内容（历史记录，便于审计回溯）：

- 参考实现的 Streamlit UI（`app.py`）、一次性归档脚本（`_archive_scripts/` 9 个）、
  安装脚本（`install.sh`）、其 README / 使用说明 / LICENSE、其 Dockerfile 与 compose 文件。
- 参考实现 `config.py` / `backend.py` / `neo4j_handler.py` / `utils/*` / `requirements.txt`
  的原始字节。

归属守卫由 `test_engine_ownership.py` 强制：禁止 `import streamlit`、禁止内置任何
数据库口令回退值、要求生成层契约符号齐全且全链路带超时。

---

## 2. 架构参考的边界说明（净室重写）

本项目的引擎能力（混合检索、章节图谱、学习路径）在设计初期参考过开源 RAG 项目的
**功能形态与契约面**（端点语义、请求/响应字段、降级行为），但：

1. **不复制代码**：重写过程中未保留对方的文件、注释、函数实现与数据结构；
   同名文件是按本项目分层重新实现的（差异见上表职责列）。
2. **契约才是对齐对象**：`api_server.py` 暴露的端点路径与字段由平台侧
   `ContractPaths.java` / 前端调用方决定，属于本项目的接口设计，而非对方资产。
3. **不再存在许可义务**：原参考实现为 MIT，其义务是"随副本保留版权与许可声明"；
   清零后本仓库**不再有该项目的任何副本**，故不再产生该项义务，也无需保留其 LICENSE。

> 结论：本项目对该引擎代码拥有完整处分权，可自行决定许可以及是否公开。

---

## 3. 主要运行时依赖的许可证（摘要）

> 以下为直接依赖的常见许可证类型；完整清单与传递依赖以构建工具为准
> （后端 `mvn dependency:tree`、前端 `npm ls`、Python 各服务 `requirements*.txt`）。
> 本表用于申报 / 软著材料的合规说明，不构成法律意见。

| 生态 | 组件 | 许可证 |
| --- | --- | --- |
| 后端 | Spring Boot / Spring Framework / Spring Security | Apache-2.0 |
| 后端 | MyBatis-Plus | Apache-2.0 |
| 后端 | Flyway Community | Apache-2.0 |
| 后端 | MinIO Java SDK | Apache-2.0 |
| 后端 | JJWT | Apache-2.0 |
| 后端 | Apache POI | Apache-2.0 |
| 后端 | Sentinel（Alibaba） | Apache-2.0 |
| 后端 | MySQL Connector/J | GPL-2.0 + FOSS Exception（以 JDBC 方式使用不触发传染） |
| 前端 | Vue 3 / Vue Router / Pinia | MIT |
| 前端 | Apache ECharts | Apache-2.0 |
| 前端 | axios / dayjs / DOMPurify | MIT / MIT / Apache-2.0 OR MPL-2.0 |
| 前端 | SheetJS `xlsx` | Apache-2.0（经 CDN tarball 分发，见阶段 1 D-03） |
| Python | FastAPI / uvicorn / pydantic / httpx | MIT / BSD-3 / MIT / BSD-3 |
| Python | LangChain（core / community / classic / ollama / text-splitters） | MIT |
| Python | sentence-transformers / torch / transformers | Apache-2.0 / BSD-3 / Apache-2.0 |
| Python | faiss-cpu (Meta) | MIT |
| Python | rank-bm25 | Apache-2.0 |
| Python | Neo4j Python Driver | Apache-2.0 |
| Python | pypdf / docx2txt / jieba | BSD-3 / MIT / MIT |
| Python | PyMuPDF (fitz) | ~~AGPL-3.0 或商业授权~~ → **已移除（从未使用）**，见 §4 |

已移除的依赖：`streamlit`（引擎无 UI，运行态由 `runtime_state.py` 承载）、
`PyMuPDF`（全仓零 `import fitz`，PDF 解析实际走 `pypdf`）。

---

## 4. 已处置：PyMuPDF 的 AGPL 问题（2026-11-25 决策）

| 项 | 内容 |
| --- | --- |
| 事实复核 | 全仓（排除各服务 venv）检索 `import fitz` / `fitz.open`：**零命中**；PDF 解析走 `pypdf` |
| 结论 | `PyMuPDF` 是"声明了但从未使用"的依赖，只带来 AGPL 义务、不产生任何能力 |
| 处置 | 删除该依赖；`requirements.txt` 保留 `pypdf`（BSD-3-Clause）作为实际实现 |
| 回归保证 | 无源码引用该包，删除对功能零影响 |

---

## 5. 使用建议（给材料撰写同学）

- 软著 / 申报材料中涉及 RAG 检索能力时，可如实声明为本项目自有引擎实现
  （混合检索、章节图谱、学习路径、图-向量融合），但**技术路线**（HyDE、BM25+向量混合、
  GraphRAG 思路）属公开方法论，不必也不应声明为"独创理论"。
- 引用第三方图表 / 模型 / 数据集时单独列明来源与许可，避免与"系统一套"的成果边界混淆。
