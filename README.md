# 基于知识图谱+LLM的AI助教系统

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.x-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.4.x-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

基于知识图谱+LLM的AI助教系统是一套融合人工智能与教学管理的现代化在线教育系统。系统面向学生、教师与管理员三大核心角色，提供个性化学习体验、高效的教学支持及全面的管理能力。

**核心能力域**：多角色 RBAC、课程/章节/选课管理、题库与在线考试（防作弊）、作业批改与查重、知识图谱可视化与学习路径规划、AI 智能问答/出题/批改/学情建议、数据分析与学情报告、WebSocket 实时通知。

> **职责边界**：知识图谱引擎（图谱构建、多跳推理、GraphRAG）与 RAG 检索引擎（BM25 / 向量检索 / embedding / 融合）均由团队另一成员以 Python 微服务实现（8001 / 8002）。本端（Java 后端）实现**消费侧**与**标准化集成层** `com.aicourse.integration`：`ContractPaths` 常量（调用约定唯一事实来源）+ `KnowledgeGraphContract` / `RagContract` 契约接口（强类型 record）+ 双实现（本地占位默认不依赖 Python；HTTP 远端经 `knowledge-graph.enabled` / `rag.enabled=true` 调 Python 8001 / 8002）。其中 `POST /api/knowledge/path/plan` 在引擎未启用/不可达/未找到路径时由消费侧**本地 BFS 兜底**，始终返回真实数据；RAG 未接入时返回 `{"code":503}` 且 AI 问答自动回退纯 LLM。本端不实现引擎逻辑，以便后续接入独立 Python 项目扩展。**完整对接契约见 `docs/项目手册.md`（第八部分，唯一标准基准）。**

> **⚠️ 参考项目方法学边界（避免误导）**：`大创赛/类似项目/DeepSeek-RAG-Chatbot` 的 GraphRAG 实体抽取采用正则 `\b[A-Z][a-z]+...`，**仅支持英文实体**，且为单机通用文档问答工具，与本项目的**中文知识图谱**（`knowledge_points` / `knowledge_point_relations`）在语言适配、领域建模、工程规模上均不可类比。**请勿将其作为本项目中文 KG 方案的对标或引用依据**；本项目中文 KG 的关系建模以 `相关论文/`（杜彩丽 IPA/IREM、中职计算机网络多源异构 KG 等）与 PRD 接口矩阵为准。

---

## 文档导航

项目文档已整合为**单一权威手册** `docs/项目手册.md`（后续所有任务唯一依据，统一标准基准），按十个部分 + 四个附录组织：

| 部分                              | 主题                                                           |
| --------------------------------- | -------------------------------------------------------------- |
| 第一部分 项目总览与背景           | 定位目标、参赛与申报要点、外部研究支撑精华                     |
| 第二部分 需求与技术基线           | 角色权限、三端功能、AI 能力、核心流程、非功能需求、产品演进    |
| 第三部分 系统架构                 | 总体架构、技术栈、目录结构、核心设计决策、标准化集成层         |
| 第四部分 数据模型与接口           | 48 表数据模型、接口矩阵、功能需求点完成明细                    |
| **第五部分 已实现功能**（活章节） | 已落地并验证的功能清单，随任务推进持续同步                     |
| 第六部分 开发指南                 | 环境、启动、编码规范、测试、质量门禁、CI、开发协作             |
| 第七部分 部署与运维               | 部署、健康检查、日志、备份、故障、安全加固、性能、监控告警体系 |
| 第八部分 Python 微服务集成契约    | KG(8001)/RAG(8002)/LoRA(8003) 完整契约与降级语义               |
| 第九部分 测试·质量·完成度评估     | 测试体系、质量门禁、完成度量化结论、安全机制、验收标准         |
| **第十部分 下一步计划**（活章节） | P0/P1/P2 待办清单与状态标记，随任务推进持续同步                |
| 附录A 术语表                      | 领域术语与缩略语统一释义                                       |
| 附录B 统计口径（唯一基准）        | 全局统计数字唯一来源，取代旧「数据口径权威总表」               |
| 附录C 贡献与 PR 流程              | 分支策略、编码规范、PR 流程（原 CONTRIBUTING 内容）            |
| 附录D 审查修正台账                | 逐轮对抗式复核的实测清单、已修复缺陷确认与新增发现             |

> 快速索引：架构设计 / 开发测试 / 部署运维 / 知识图谱与 RAG 契约 / 需求与路线图 / 交付进度 / **统计口径唯一基准** → 均见 `docs/项目手册.md` 相应部分。

---

## 技术栈

| 层级               | 技术                         | 版本                  |
| ------------------ | ---------------------------- | --------------------- |
| 后端框架           | Spring Boot                  | 3.5.15（JDK 21）      |
| ORM                | MyBatis-Plus                 | 3.5.7                 |
| 安全               | Spring Security + JWT        | 6.x / jjwt 0.12.6     |
| 数据库             | MySQL                        | 8.0+                  |
| 缓存               | Redis                        | 7.x（**必选**）       |
| 前端框架           | Vue 3                        | 3.5.x                 |
| 构建工具           | Vite                         | 8.x                   |
| UI库               | Element Plus                 | 2.4.x                 |
| 状态 / 路由 / 图表 | Pinia / Vue Router / ECharts | 2.1.x / 4.2.x / 6.1.x |
| 实时通信           | WebSocket                    | 集成Spring WebSocket  |
| 限流熔断           | Sentinel                     | 下游流控 + 慢调用熔断 |

---

## 快速开始

项目提供两条启动路径：**本地开发**（默认，推荐——使用本机 MySQL / Redis，Docker 仅作兜底）与 **容器化部署**（备用/上线——全套 Docker）。详细步骤见 `docs/项目手册.md`（第六部分开发 / 第七部分部署）。

### 方式一：本地开发（推荐）

前置：JDK 21、Node 20.19+、MySQL 8.0+、Redis 7.x（必选，启动强校验）。

```bash
# 数据库初始化（首次）
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p ai_course_platform < backend/src/main/resources/index.sql

# 后端（一键脚本：自动加载 .env、校验 JDK 21、检测 MySQL/Redis）
start-backend.bat
# 或手动：cd backend && ./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# 前端
start-frontend.bat
# 或手动：cd frontend && npm install && npm run dev
```

访问：前端 http://localhost:5173 ｜ API http://localhost:8080/api ｜ Swagger http://localhost:8080/api/swagger-ui.html

> 本机未装 MySQL / Redis 时：`docker compose up -d mysql redis` 拉起基础设施容器（MySQL 宿主端口 3307），`start-backend.bat` 自动切换数据源。

### 方式二：Docker 容器化部署（备用 / 上线）

```bash
cp .env.example .env      # Windows: copy .env.example .env（填入真实强密码与密钥）
docker compose --profile full up -d --build
# 可选叠加：--profile ops（每日备份）/ --profile monitor（Prometheus+Grafana+Loki 监控）
```

访问：前端 http://localhost ｜ API http://localhost:8080/api

> 生产部署前请逐项核对 `docs/项目手册.md` 第七部分安全加固检查清单。

---

## 演示账号

| 角色   | 用户名               | 密码   | 说明                     |
| ------ | -------------------- | ------ | ------------------------ |
| 管理员 | admin                | 123456 | 系统管理员，拥有全部权限 |
| 教师   | teacher / teacher2   | 123456 | 张教授 / 李副教授        |
| 学生   | student / student2~5 | 123456 | 王同学等                 |

> 演示密码仅限开发/演示环境，生产环境务必修改（见 `docs/项目手册.md` 第七部分 §29 安全加固检查清单）。

---

## 测试与质量

```bash
# 后端：单元测试 + 集成测试 + JaCoCo/SpotBugs/ArchUnit 门禁
cd backend && mvn test -Dtest='!*IT,!Phase1Phase3IntegrationTest' -DfailIfNoTests=false

# 前端：ESLint + vitest + 构建校验
cd frontend && npm run lint && npx vitest run && npm run build
```

质量门禁基线（渐进收敛制，只许上调不得下调）：JaCoCo LINE ≥ 85% / BRANCH ≥ 65%（生产收敛目标指令覆盖 ≥ 85%）、SpotBugs 0 error、ArchUnit 架构约束。测试方法、契约测试清单与 CI 流水线见 `docs/项目手册.md` 第六部分。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

- 完整许可证文本见仓库根目录 [`LICENSE`](LICENSE)。
- 参与贡献请阅读 [`docs/项目手册.md`](docs/项目手册.md)（附录 C：分支策略、编码规范、质量门禁与 PR 流程）。
- 报告 Bug / 提需求请使用 Issue 模板（`.github/ISSUE_TEMPLATE/`）。
