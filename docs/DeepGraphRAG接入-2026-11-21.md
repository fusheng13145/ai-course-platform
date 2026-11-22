# DeepGraph RAG（文档图谱引擎）接入记录 · 2026-11-21

> 本次集成把**已通过验收**的 `DeepSeek-RAG-Chatbot-main`（本机目录，验收态良好）以
> "复制内容一字不改 + 新增契约适配器" 方式真实接入 ai-course-platform，成为平台 Python
> 第 4 个微服务（8501，目录 `python-services/deepgraph_rag/`）。
> 集成约束（任务口径）：**严禁修改 `DeepSeek-RAG-Chatbot-main` 目录内任何文件**；复制内容保持
> 已实现状态不变；所有兼容/适配改动只落在 ai-course-platform 仓库内。

## 1. 为什么接、接什么

- 平台 PRD 与开题答辩规划的 RAG 增强链（HyDE 查询扩展、BM25+FAISS 混合检索、GraphRAG 增强、
  Cross-Encoder 重排）在 8002 rag_service 中已有实现（知识点语料，ENGINE_VERSION=1.1）；
  DeepSeek-RAG-Chatbot 在**整篇文档语料**这一形态上给出了已验收的完整实现（PDF/DOCX/TXT
  上传解析 → FAISS+BM25 → Neo4j 图谱 → 章节图谱 → 学习路径 → DeepSeek/Ollama 双提供商问答）。
- 该项目当年以 `deepgraph_rag` 名义存在过（原 `python-services/deepgraph_rag/`，8501），
  归档原因（`7c6ced0`）是"utils/ 模块从未提交导致无法启动"。本次复制源已含完整
  `utils/`（build_graph 1621 行 / doc_handler / retriever_pipeline / llm_client / persistence）+
  `neo4j_handler.py` + 中文实体抽取（LLM 抽取 + jieba 图检索），归档理由已消除。
- 平台 README 中"DeepSeek-RAG-Chatbot 仅支持英文实体正则"的旧边界提示针对的是其开源旧版；
  本次验收版实体抽取已改为 LLM 中文提示（`backend.extract_entities_from_prompt`）+ jieba
  分词图检索（`build_graph.retrieve_from_graph`），中文课程资料场景可用。

## 2. 复制清单（逐字节校验一致，cmp 全部 IDENTICAL）

复制到 `python-services/deepgraph_rag/`：
`app.py`、`backend.py`、`config.py`、`neo4j_handler.py`、`requirements.txt`、`LICENSE`、
`README.md`、`使用说明.md`、`install.sh`、`Dockerfile`、`docker-compose.yml`、`.env`（本地回退默认值，
已被仓库根 `.gitignore` 的 `.env` 规则覆盖）、`.gitignore`、`utils/{build_graph,doc_handler,
retriever_pipeline,llm_client,persistence}.py`；一次性代码生成脚本（`append_*`/`create_*`/`fix_*`/
`restore_*`/`test1.py`）按当年归档结构放入 `_archive_scripts/`。
未复制：`.venv`（已损坏，指向不存在的 `D:\Anaconda`）、`__pycache__`、运行期数据
（`data/`、`graph_data/`、`temp/`）、未被任何代码引用的 `lib/` 静态资源。

## 3. 新增/改动清单（全部在 ai-course-platform 仓库内）

### 3.1 Python（新增，复制内容零改动）

| 文件 | 内容 |
| --- | --- |
| `python-services/deepgraph_rag/api_server.py` | **新增** FastAPI 契约适配器（8501）：`/api/v1/deepgraph/{health,ingest,documents,retrieve,qa,chapter-graph,learning-path}`。headless 直接调用原实现（Streamlit 1.56 实测：无运行上下文时 `st.session_state` 可读写、`st.write/*` 仅告警），`process_documents`/`retrieve_documents`/`generate_llm_response`/`build_chapter_knowledge_graph`/`neo4j_db` 全部走复制代码。X-Internal-Key 中间件、HTTP 503 + `code=503`/`mode=UNAVAILABLE` 降级语义与 8002 完全同款 |
| `python-services/deepgraph_rag/test_deepgraph_contract.py` | **新增** 契约护栏测试（12 例）：健康形状、内部认证、降级语义、422/404 校验 |

适配器侧接线说明（不动复制代码）：`_reranker()` 把 CrossEncoder 实例挂进管道 dict 的
`retrieval_pipeline["reranker"]` 预留位——复制代码 `retriever_pipeline.retrieve_documents` 读取
该键，使「启用 Neural Reranking」开关在 API 通道真正生效；文档按 `course{courseId}_` 前缀
命名空间隔离课程；服务重启后从持久化 FAISS/图谱 JSON 重建课程级管道（BM25 用真实 chunk 重建）。

### 3.2 Java（消费侧）

| 文件 | 改动 |
| --- | --- |
| `integration/ContractPaths.java` | 新增 `DEEPGRAPH_ENABLED/BASE_URL/DEFAULT_BASE_URL`、8 条 `/api/v1/deepgraph/*` 端点常量、响应扩展字段（`graph_context/llm_prompt/stored_as/chunk_count/has_graph/graph_edges/path_text/retrieval`）、`MSG_DEEPGRAPH_UNAVAILABLE` |
| `integration/deepgraph/DeepGraphRagContract.java` | **新增** 契约接口 + 记录（检索含 `graphContext`；`engineReady()` 口径与 8002 一致） |
| `integration/deepgraph/HttpDeepGraphRagProvider.java` | **新增** RestClient 实现，Sentinel 资源 `deepgraph:http`，读超时/连接拒绝/503 不重试（与 `HttpRagProvider` 同口径），可选 `X-Internal-Key` |
| `integration/deepgraph/DisabledDeepGraphRagProvider.java` | **新增** 占位实现（`enabled=false` 默认），全部 503 |
| `service/DeepGraphRagService.java` + `impl/DeepGraphRagServiceImpl.java` | **新增** 门面（`deepgraph-rag.*` 配置，读超时默认 180s 覆盖本地大模型长调用） |
| `service/component/ai/CourseEvidenceComponent.java` | 新增双引擎合并：`retrieveWithDocuments`（同步问答）与 `retrieveMergedTyped`（流式问答）；8002 证据在前、8501 补充（去重、上限 5 条）；第二引擎 null/降级/异常一律静默跳过；旧两参构造器保留，既有 6 处测试直构不受影响 |
| `service/component/ai/AiRagComponent.java` | `ragAsk` 改走 `retrieveWithDocuments` |
| `service/component/ai/AiDialogueComponent.java` | `streamChat` 改走 `retrieveMergedTyped`（移除不再使用的 `ragService` 字段，构造器 3→2 参，3 处测试同步） |
| `controller/DeepGraphRagController.java` | **新增** `/rag/deepgraph/*` 治理入口：文档入库/列表/删除（教师/管理员 + `assertCanManageCourse`）、检索与学习路径（`assertCanReadCourse`）、章节图谱、健康探测 |
| `health/PythonServicesHealthIndicator.java` | 增加 `deepgraph-rag` 探针（仅 enabled 时参与判定） |
| `resources/application.yaml` | 新增 `deepgraph-rag:` 配置块（`DG_RAG_ENABLED:false` 默认、8501、5/180/1）；Sentinel 新增 `deepgraph:http` 流控（QPS 10）与慢调用熔断（10s 阈值）规则 |
| `src/test/resources/application-{test,service-test,contract-test}.yml` | 显式 `deepgraph-rag.enabled: false`（CI 不依赖 8501） |
| 测试 | **新增** `HttpDeepGraphRagProviderContractTest`（12 例）、`DisabledDeepGraphRagProviderTest`（7 例）、`DeepGraphEvidenceMergeTest`（9 例）；更新 `AiDialogueComponent` 相关 3 个测试构造器 |

### 3.3 编排与配置

| 文件 | 改动 |
| --- | --- |
| `scripts/start-engines.py` | `ORDER/PORTS/SERVICE_DIRS/APP_MODULES` 增加 deepgraph（8501，入口 `api_server:app`）；`ENGINE_EXPORTS["deepgraph"]` 按 config.py/llm_client.py 实读点导出（`LLM_PROVIDER/MODEL/DEEPSEEK_*/OLLAMA_API_URL/NEO4J_*/CROSS_ENCODER_MODEL/HF_*/INTERNAL_API_KEY`）；`DEEPSEEK_API_KEY` 走与 rag 相同的 api_key 通道（占位值不转）；探针 `/api/v1/deepgraph/health`；`should_start(DG_RAG_ENABLED)` |
| `scripts/start-all.py` | `ENGINE_SERVICES/PORTS/SERVICES` 增加 deepgraph；`engine_flags` 现探 8501；`backend_env` 在 8501 已监听且 `.env` 未显式写值时自动置 `DG_RAG_ENABLED=true`（与 KG/RAG 同口径）；`wanted_services` 收录 |
| `.env` / `.env.example` | 增量新增 `DG_RAG_ENABLED`、`DG_RAG_BASE_URL`、`DG_CROSS_ENCODER_MODEL`（`DG_CROSS_ENCODER_MODEL=BAAI/bge-reranker-base` 指向平台已缓存的中文重排模型；复制内容 config.py 本身支持该环境变量，不改代码） |
| `README.md` / `AGENTS.md` | 三服务 → 四服务口径；8501 端口表；deepgraph_rag 归档历史注记更新 |
| 启动器护栏测试 | `test_start_engines.py`/`test_start_all.py` 的 plan/wanted_services/engine_flags 断言同步五引擎口径（116 例全绿） |

## 4. 红线与语义保持

- **AI 面失败不伪装成功**：8501 任何不可用（未启用/未启动/无索引/依赖缺失）都以
  HTTP 503 + `mode=UNAVAILABLE` 表达；Java 合并层按 `engineReady()` 判定后**静默跳过第二引擎**，
  问答链回落 8002 + 知识图谱接地，行为与未集成前一致——不产生新的"假装成功"路径。
- 复制内容（app.py/backend.py/utils/neo4j_handler.py 等）**零修改**；`git status` 中该目录
  只有 `api_server.py` 与 `test_deepgraph_contract.py` 两个新文件。
- Streamlit UI（`streamlit run app.py`）在复制目录内仍可独立运行（已验收形态原样保留）。

## 5. 本机环境适配记录（2026-11-21 实测）

| 现象 | 根因 | 处置（均在适配器/配置层，复制代码不动） |
| --- | --- | --- |
| FAISS `save_local`/`load_local` 全部报 `could not open ... for writing/reading` | faiss C++ 文件 API 在**中文绝对路径**（本仓库位于"大创赛"目录）下必败；平台 rag_engine 同因改用内存序列化写字节 | `api_server._relocate_data_dir_if_non_ascii()`：非 ASCII 安装路径时把 persistence 模块常量重定位到 `%USERPROFILE%\.aicourse-platform\deepgraph_rag`（等价于装到 ASCII 目录，纯部署配置） |
| 适配器 `/qa`、`/chapter-graph` 的 LLM 调用返回空 | `deepseek-r1:8b`（qwen3 架构蒸馏，thinking 模型）在默认思考模式下把全部输出写入 `thinking` 字段，`num_predict` 被思考耗尽、`response` 恒空；复制代码 `llm_client` 只读 `response`（其 `<think>` 剥离逻辑针对旧版内联思考设计） | 生成类验证切到非思考模型（`qwen2.5-3b-instruct`，经 ModelScope GGUF `ollama create` 本地导入，抽取 8 条三元组仅 1.4s）；Java 问答链由 `OllamaProvider` 的 `OLLAMA_THINK=false` 原生支持（与适配器无关） |
| DeepSeek API 返回 402 | 平台 `.env` 的 `DEEPSEEK_API_KEY` 额度耗尽 | 属已知降级场景（DR-74 同型）：`llm_client` 抛错→适配器如实 503；充值后 `.env` 键即恢复（`LLM_PROVIDER=deepseek` 为已验收项目的首选提供商） |
| registry.ollama.ai DNS 不通 / 拉模型 EOF | 本机外网必须走系统代理 127.0.0.1:65532，且 pull 由 Ollama 服务端进程执行 | 模型改走 hf-mirror.com / ModelScope CDN 下载 GGUF 后 `ollama create` 本地导入（`nomic-embed-text:latest`、`qwen2.5-3b-instruct` 均如此；hf-mirror 对 Xet 存储仓库会 302 到不可解析的 cas-bridge.xethub.hf.co，ModelScope CDN 无此问题） |
| sentence-transformers 加载重排模型 SSL EOF | 代理干扰 hf-mirror TLS | 8501 进程对 HF 走离线模式（`HF_HUB_OFFLINE=1`，模型已在本地缓存），`NO_PROXY` 放行本机 Ollama |

## 6. 课程知识图谱落库（2026-11-22 补齐：平台「知识图谱」页可见）

用户反馈"知识图谱没有变化"后的根因定位与收口：

- **平台「知识图谱」页渲染的是 MySQL `knowledge_points`/`knowledge_point_relations`**
  （`/knowledge/points/graph` 直查业务表），此前桥接只到了 kg_service（8001）——页面数据源未变，
  读数停留在种子口径 59 节点/55 边。
- **教材从未入库**：谢希仁《计算机网络》（第八版）全文 655,060 字此前未进集成引擎。

新增改动（全部在 ai-course-platform 内，复制内容仍零改动）：

| 层 | 内容 |
| --- | --- |
| 适配器 | `GET /api/v1/deepgraph/course-graph?course_id=&document_name=`：聚合某文档全部章节图谱归档（`graph_data/{doc}_kg_*.json`）与层次大纲树，输出 nodes/edges（含 type=CONCEPT/CHAPTER 与抽取溯源 evidence）；跨章重复三元组判重 |
| 适配器 | `chapter-graph` 端点内置 kg_service 桥接（`sync_chapter_graph_to_course_kg`，实体按名幂等/三元组判重）；`ingest` 端点内置层次树桥接（`sync_hierarchical_tree_to_course_kg`，章→节=包含章节，type=CHAPTER） |
| 适配器 | 嵌入批处理垫片 `_install_embed_batching_shim()`：langchain_ollama 把全部分块塞进一次 /api/embed 导致 Ollama runner tokenize 崩溃（教材级 1300 块必现），运行期按 `DG_EMBED_BATCH=32` 分批（复制代码不动） |
| Java | 契约新增 `getCourseGraph`（`DEEPGRAPH_COURSE_GRAPH` 端点常量 + `CourseGraphNode/Edge/CourseGraphData` 记录）；门面新增 `syncCourseKnowledgeGraph`——按 (courseId,name) 幂等落库 `knowledge_points`、按 (source,target,type) 幂等落库 `knowledge_point_relations`；控制器新增 `POST /rag/deepgraph/course-graph/sync`（教师/管理员 + 课程 ACL） |

实测读数（`scripts/ingest-textbook-deepgraph.py` + 同步端点）：

- 教材入库：655,060 字 → **FAISS 持久化（58 个嵌入批次全 200，垫片生效）**、18 章切分、层次树桥接进 kg_service；
- **18/18 章图谱全部建成**（LLM 真实抽取，单章 9~60 边，Neo4j 同步 + kg_service 桥接；4 章首轮抽取为空，重试全部成功——如实降级语义下重试是合法操作）；
- `POST /rag/deepgraph/course-graph/sync` → **MySQL 新增 495 知识点 + 548 关系（判重跳过 23）**；
- 平台图谱页数据源 `/knowledge/points/graph`：**554 节点 / 603 边**（此前 59/55，约 10 倍），pointType 分布 CONCEPT 440 / CHAPTER 106 / SKILL 8；
- 幂等复跑同步：addedPoints=0、addedRelations=0、skipped=571（全判重，安全可重放）；
- SSE 问答主链复测 4/4 仍通过（双引擎 10 条引用源）。



### 6.5 静态门禁（集成回归）

| 门禁 | 读数 |
| --- | --- |
| Python 契约测试 | `pytest python-services/deepgraph_rag -q` → **12 passed**（含 health/认证/降级/校验分支） |
| Python 四套件全量 | `pytest deepgraph_rag rag_service kg_service scripts/tests -q` → **269 passed**（原 8001/8002/启动器护栏无回归） |
| Java 新增测试 | 契约 12 + Disabled 7 + 合并 9 + 控制器 13 + Boost 14 + 门面 3 + 健康探针 3 = **61 例全绿** |
| Java 全量门禁 | `mvn -B -o clean verify -DskipITs` → **BUILD SUCCESS：4067 例 / 0 失败 / 0 错误 / 1 跳过**；覆盖率 LINE **89.95%**、BRANCH **77.10%**（门禁 85/77 通过，"All coverage checks have been met"）；SpotBugs **0**；ArchUnit 通过 |
| 启动器护栏 | `pytest scripts/tests -q` → **116 passed**（plan/wanted_services/engine_flags 五引擎口径） |

### 6.2 引擎级 E2E（直连 8501，`scripts/verify-deepgraph-e2e.py`）→ **6/6 通过**

1. 健康检查：provider=ollama、Neo4j reachable=True、embeddings=nomic-embed-text:latest、cross_encoder=BAAI/bge-reranker-base
2. 文档入库：课程 4178 十章真实语料经**原实现 `process_documents` 全链路**（PyPDFLoader 同款 TextLoader → 500/100 分块 → Ollama nomic 嵌入 → FAISS+BM25 → 持久化），**累计 chunk=140**
3. 混合检索：BM25+FAISS 混合召回 3 条 → **CrossEncoder 重排真实执行**（8501 日志追踪点 4→5），首条 relevance_score=1.0
4. 真实问答：`get_retrieval_context` 格式接地 → `generate_llm_response`（本地 qwen2.5-3b）→ 734 字带引用回答，3 条来源
5. 章节图谱：**LLM 三元组抽取 12 节点 / 13 边，Neo4j 同步 neo4j_synced=True**（`insert_triplets_from_graph` 真实入库）
6. 学习路径：`query_learning_path` Neo4j 反向追溯，返回结构化路径文本

### 6.3 集成级 E2E（Java 8080 → 8501 → SSE，`scripts/verify-deepgraph-java-e2e.py`）→ **4/4 通过**

1. 登录 admin → `GET /api/rag/deepgraph/health` → `{enabled:true, reachable:true}`（Java 门面→契约→8501 全链）
2. `POST /api/rag/deepgraph/documents`（教师/管理员 ACL）入库 10 章成功（门禁口为 Java 控制器权限链）
3. `POST /api/rag/deepgraph/retrieve` → `mode=ENGINE`、命中 3 条真实片段
4. **`GET /api/ai/qa/stream`（SSE 问答主链）→ 终帧 sources=5 条 deepgraph 真实引用源**（score 1.0/0.95/0.9…，片段来自已入库课程文档；答案由 Java LLMProvider 生成）——双引擎合并（`CourseEvidenceComponent.retrieveMergedTyped`）在真实流式链路生效的直接证据
5. 降级链路（索引删除/引擎未启）另经无索引运行验证：retrieve 以 `data.code=503/mode=UNAVAILABLE` 如实降级，SSE 问答不中断（sources=0，纯 LLM + 知识点接地）

### 6.4 运行栈快照（验证结束时全部在线）

MySQL 3306 / Redis 6379 / Neo4j 7687 / MinIO 9000 / Ollama 11434 / **deepgraph 8501** / Java 8080 / Vite 5173；
Java 健康指示器：`pythonServices → deepgraph-rag → UP (enabled=true)`。
