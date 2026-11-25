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
| Python | **PyMuPDF (fitz)** | **AGPL-3.0 或商业授权**（见下方 §3，需决策） |

---

## 3. 待决策：PyMuPDF 的 AGPL 问题

| 项 | 内容 |
| --- | --- |
| 事实 | 上游 `requirements.txt` 引入 `PyMuPDF`，`utils/doc_handler.py` 使用 `fitz` 解析 PDF |
| 许可性质 | AGPL-3.0：若通过网络向他人提供服务，需向使用者提供对应源码（并可主张适用 AGPL） |
| 与项目目标冲突点 | 项目要申报软著、并对"自有代码"主张版权；AGPL 组件混入服务端链路会削弱该主张的清晰度 |
| 可选处置 | ① 保持现状并在软著材料中明示该组件为第三方 AGPL 组件、仅用于文档解析；② 解析链路改用 `pypdf`（上游 `requirements.txt` 已同时引入，切换成本可控）；③ 购买 PyMuPDF 商业授权 |
| 现状 | **未处置**，仅登记。上游文件被逐字节冻结，改动须走"替换实现 + 重算清单"流程 |

---

## 4. 使用建议（给材料撰写同学）

- 软著/申报材料中，凡涉及 RAG 检索能力，**只声明"基于开源参考实现做适配与集成"**，
  不要声明为本项目原创算法；自有部分是：适配层、平台业务、图谱数据治理、前端三视图渲染。
- 引用第三方图表/模型/数据集时单独列明来源与许可，避免与"系统一套"的成果边界混淆。
