# 第三方资产声明（THIRD_PARTY NOTICES）

本仓库包含第三方开源资产。本文件用于明确其版权归属与许可，避免与项目自有代码的
版权声明相互混淆。**项目自有代码的许可见根目录 `LICENSE`；本文件所列资产的版权归其各自作者所有。**

---

## 1. 上游参考实现：DeepSeek-RAG-Chatbot（MIT）

| 项 | 内容 |
| --- | --- |
| 资产名称 | `DeepSeek-RAG-Chatbot` 3.0 参考实现 |
| 版权人 | `Copyright (c) [2025] [N SAI AKHIL]` |
| 许可证 | MIT（原文见 `python-services/deepgraph_rag/LICENSE`） |
| 在本仓库的位置 | `python-services/deepgraph_rag/` 下的 25 个文件（清单见同目录 `UPSTREAM_MANIFEST.json`） |
| 修改状态 | **逐字节未修改**（`UPSTREAM_MANIFEST.json` 记录每个文件的 SHA-256；CI 与本地均可复算） |
| 例外说明 | 其中 9 个一次性脚本在上游位于仓库根目录，本仓库收纳进 `_archive_scripts/` 子目录；**仅路径变化，内容字节一致** |

MIT 许可证要求："The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software." 因此：

1. 上述 25 个文件**必须**随附其原始 `LICENSE`（已保留在 `python-services/deepgraph_rag/LICENSE`），不得删除。
2. 本项目在 `LICENSE` 中的版权声明**不覆盖**上述文件。
3. 项目新增的适配层（`api_server.py`、`knowledge_api.py`、`knowledge_engine.py`、
   `test_deepgraph_contract.py`、`test_merged_contract.py` 及 `requirements-platform.txt` 等）
   为本项目自有代码，适用根目录 `LICENSE`。

> 说明：MIT 允许商用、修改、再分发与再许可，唯一义务是保留版权与许可声明。
> 履约方式是"保留声明"，不是"切断一切关联"——切断声明反而构成违约。

---

## 2. 主要运行时依赖的许可证（摘要）

> 以下为直接依赖的常见许可证类型，完整清单与传递依赖以构建工具为准
> （后端 `mvn dependency:tree`、前端 `npm ls`、Python 各服务 `requirements*.txt`）。
> 本表用于申报/软著材料的合规说明，不构成法律意见。

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
| 前端 | SheetJS `xlsx` | Apache-2.0（**注意：经 CDN tarball 分发，见阶段 1 D-03**） |
| Python | FastAPI / uvicorn / pydantic / httpx | MIT / BSD-3 / MIT / BSD-3 |
| Python | sentence-transformers / torch / transformers | Apache-2.0 / BSD-3 / Apache-2.0 |
| Python | faiss-cpu (Meta) | MIT |
| Python | rank-bm25 | Apache-2.0 |
| Python | Neo4j Python Driver | Apache-2.0 |
| Python | **PyMuPDF (fitz)** | ~~AGPL-3.0 或商业授权~~ → **已移除（未使用）**，见下方 §3 |

---

## 3. 已处置：PyMuPDF 的 AGPL 问题（2026-11-25 决策）

| 项 | 内容 |
| --- | --- |
| 事实复核 | 全仓（排除各服务 venv）检索 `import fitz` / `PdfReader(...).open` / `fitz.open`：**零命中**。Python 侧 PDF 解析实际走 `langchain_community.document_loaders.PyPDFLoader`（其底层是 `pypdf`），`deepgraph_rag/utils/doc_handler.py:259` 即该调用点 |
| 结论 | `requirements.txt` 里的 `PyMuPDF` 是**声明了但从未使用**的依赖，只带来 AGPL 义务、不产生任何能力 |
| 处置 | **删除该依赖**（`python-services/deepgraph_rag/requirements.txt`，原第 14 行）。同时保留 `pypdf`（BSD-3-Clause）作为实际 PDF 解析实现 |
| 为何这是最优解 | 相比"保持现状并声明"（仍暴露 AGPL 网络服务义务）、"换成 pypdf 重写解析链路"（本就在用 pypdf，无需重写）、"买商业授权"（为零使用付费），删除的**收益/成本比最高且零功能回归风险** |
| 回归保证 | 改动仅删一行声明，无任何源码引用该包；后端 4376 项测试与 Python 契约测试口径不变（Python 侧本来就没装过 `fitz`） |
| 遗留 | 若阶段 4 复制进来的上游文件出现 `import fitz`，须**在平台侧适配层用同名兼容模块或改写包装**实现（不改上游文件字节），届时再评估 |

---

## 4. 使用建议（给材料撰写同学）

- 软著/申报材料中，凡涉及 RAG 检索能力，**只声明"基于开源参考实现做适配与集成"**，
  不要声明为本项目原创算法；自有部分是：适配层、平台业务、图谱数据治理、前端三视图渲染。
- 引用第三方图表/模型/数据集时单独列明来源与许可，避免与"系统一套"的成果边界混淆。
