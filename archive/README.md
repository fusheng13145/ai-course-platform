# archive — 历史产物归档区

本目录存放已退出正式开发/部署流程的历史代码与交付快照，仅作追溯参考，**不参与 CI、docker-compose 与文档主线**。

## deepgraph_rag/ — RAG 演示原型（已归档）

- 来源：RAG 组成员（zyjhhy）2026-10-01 通过 GitHub 网页上传（提交 `dbafa82`），系开源项目 SaiAkhil066/DeepSeek-RAG-Chatbot 的改造版 Streamlit 应用（原端口 8501）；2026-10-03 由 leyon 归位至 `python-services/deepgraph_rag/`（提交 `3cedefb`）。
- 归档理由（2026-10-05 评估）：
  1. 正式 RAG/知识图谱功能全部走契约服务 `rag_service`（8002）与 `kg_service`（8001），后端 `ContractPaths.java` 只对接这两个端口，前端无任何指向 8501 的入口；
  2. 该应用依赖的 `utils/` 五个模块（build_graph/llm_client/persistence/retriever_pipeline/doc_handler）从未提交，模块顶层导入即 `ModuleNotFoundError`，实际无法启动；
  3. CI（ci.yml / release.yml）不覆盖该目录，删除其配置不影响任何流水线。
- 处置：`git mv` 整体归档（组员成果不物理删除，git 历史与目录内容完整保留）；docker-compose 的 `deepgraph-rag-service` 服务块、`.env.example` 的 Ollama/演示模型变量、README 相关章节已同步摘除。Neo4j 服务保留（kg_service 正式依赖），容器更名为 `aicourse-neo4j`。
