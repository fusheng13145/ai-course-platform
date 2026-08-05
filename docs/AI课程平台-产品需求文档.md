# AI 课程平台 · 产品需求文档与技术方案（合并权威版）

> **文档定位**：本文件由两份历史文档合并而成，是当前唯一权威的需求与技术基线：
> 1. `docs/prd/` 体系（v1.0，对标超星学习通，含三端 PRD / AI 专题 / 超越超星创新 / 代码映射 / 里程碑）；
> 2. `docs/基于知识图谱+LLM的AI助教系统完整需求文档.md`（课题来源江苏科技大学，含分层 RAG 技术方案 / 93 接口契约 / 20 数据表）。
>
> 合并目的：消除冗余、统一口径、保留两份文档的全部高价值内容，形成**一份最全面、最优雅、最精确**的项目文档。
> **版本**：v2.0（合并版）　**状态**：权威基线　**最后更新**：2026-07-23

---

## 〇、合并说明与原文矛盾修正

合并时对源文档的内部矛盾做了统一，并以「实测枚举」为权威：

| 项目 | 原文声明 | 实测枚举 | 本版处理 |
|------|----------|----------|----------|
| 接口总数 | 128 | 93（9+28+18+17+9+12） | 以 **93** 为权威清单，缺 35 个无出处，不凭空补全 |
| 数据表总数 | 18 | 20 | 以 **20** 为权威清单 |
| 功能点总数 | 68 | 93（与接口数一致） | 以 **93** 为权威 |
| 章节缺失 | 目录列第 10 章「实施计划与开发优先级」 | 正文无对应内容，正文第 10 章为「预期项目成果」 | 以「里程碑与 MVP 范围」章节为准，删除空目录项 |

> 凡涉及上述数字，均以本表口径为准。

---

## 一、项目背景与定位

### 1.1 项目来源与背景

- **项目来源**：江苏科技大学校级教育教学改革课题《生成式人工智能赋能网络类课程教学改革研究》。
- **项目定位**：以《计算机网络》专业课程为试点，融合知识图谱与大语言模型（RAG），构建智能教学辅助系统，解决以下行业痛点：
  1. 专业课程知识组织碎片化——构建结构化知识网络；
  2. 大语言模型「知识幻觉」——以 RAG + 知识图谱确保专业问答准确性；
  3. 个性化学习支持不足——实现因材施教；
  4. 师生沟通成本高——提供 7×24 小时智能答疑。

### 1.2 项目创新特色

- **【应用场景创新】** 区别于市面上面向通用学习场景、仅服务学生端的 AI 问答工具，打造**面向专业课程、覆盖教与学双场景**的一体化智能助教系统。以《计算机网络》为试点，可为其他计算机类专业课程提供可复用、可推广的实现方案。
- **【技术融合创新】** 设计适配课程教学场景的专业知识图谱，基于 RAG 技术结合开源大模型 Prompt 工程，**设计分层检索策略**，有效降低专业问答的知识幻觉发生率，提升课程专业问题的问答精准度与多跳逻辑推理能力。

### 1.3 对标超星的核心价值主张

本平台对标对象为**超星学习通（Chaoxing Xuetang）**。超星以「资源 + 流程」见长，AI 能力为文本式附加模块；本平台以「AI 原生 + 体验代差」建立优势：

- 每条需求标注两档进阶目标：**对标超星**（达到超星同等能力） / **超越超星**（在超星之上做差异化增强：AI 深度嵌入、自适应、多模态、体验打磨）。
- 现有代码虽已实现大量功能，但用户基本不满意，故本 PRD 不把「已实现」当终点，而是对每处现有功能给出进阶版定义。

### 1.4 项目开发基础（版权与溯源）

- 基于开源项目 `ai-smart-course-platform` 二次开发（<https://github.com/Lllxy11/ai-smart-course-platform>）。
- **开源协议**：MIT 协议，支持商业二次开发、定制化改造与闭源部署。
- **技术匹配度**：90% 基础技术能力可直接复用，无需重构技术架构。

### 1.5 成功度量（North Star Metrics）

| 指标 | 目标 |
|------|------|
| 教师建课耗时 | 降低 ≥ 50% |
| 客观题批改自动化 | ≥ 95% |
| 学生疑问 AI 首解率 | ≥ 70% |
| 专业问答准确率 | ≥ 90% |
| 知识幻觉发生率 | ≤ 5% |

---

## 二、用户角色体系

### 2.1 角色与接口分布（来自完整需求文档）

| 角色 | 核心定位 | 功能模块数 | 对应接口数 |
|------|----------|------------|------------|
| **学生** | 学习主体 | 4 大模块 | 18 个 |
| **教师** | 教学主体 | 5 大模块 | 28 个 |
| **管理员** | 系统运维 | 3 大模块 | 17 个 |
| **公共** | 基础服务 | 1 大模块 | 9 个 |
| **AI 功能** | 智能服务 | 1 大模块 | 9 个 |
| **知识图谱** | 知识服务 | 1 大模块 | 12 个 |

### 2.2 进阶 RBAC + 数据权限模型（来自 PRD 架构）

| 角色 | 说明 | 数据范围 |
|------|------|----------|
| 超级管理员 | 平台运营 | 全平台 |
| 学校管理员 | 单校信息化 | 本校 |
| 院系管理员 | 院系教务 | 本院系 |
| 教师 | 主讲 | 本人课程 |
| 助教 | 教学团队 | 被授权课程（可配置：批改/答疑/看数据） |
| 辅导员 | 学生管理 | 所带班级 |
| 学生 | 学习 | 本人 |

- **功能权限**：基于 `role_permissions` 落地到注解 `@PreAuthorize` + 方法级拦截器，支持「按课程授权教学团队」。
- **数据权限**：按组织树做行级过滤（院系/班级），SQL 拦截器自动注入 `org_id` 条件。
- **超越超星点**：权限变更实时生效（Redis 缓存角色，登录态不强制重登）；提供「权限矩阵可视化配置后台」。

### 2.3 多租户组织树

```
租户 Tenant（学校）
 └─ 院系 School/College
     └─ 专业 Major
         └─ 年级 Grade
             └─ 班级 Classroom
                 └─ 学生/教师 User
```

- 引入 `Tenant` / `OrganizationNode`（自引用组织树）实体；所有业务表携带 `tenant_id` + `org_node_id`。
- 支持多校独立部署与数据隔离（SaaS 多租户）。
- **超越超星点**：组织树支持拖拽编辑 + Excel 批量导入 + 与教务系统中间库自动同步；变更有审计日志。

---

## 三、总体架构与技术方案

### 3.1 系统整体架构（四层分层架构）

```
┌─────────────────────────────────────────────────────────┐
│                    应用层 (Application Layer)           │
│  智能问答模块  │  知识可视化  │  学情分析  │  系统管理    │
├─────────────────────────────────────────────────────────┤
│                    RAG引擎层 (RAG Engine Layer)         │
│  查询理解  │  分层检索  │  语义召回  │  生成融合        │
├─────────────────────────────────────────────────────────┤
│                  知识存储层 (Knowledge Layer)           │
│  Neo4j图数据库  │  Milvus向量库  │  MySQL关系库         │
├─────────────────────────────────────────────────────────┤
│                    基础服务层 (Infrastructure)          │
│  大模型API  │  嵌入模型  │  缓存  │  消息队列           │
└─────────────────────────────────────────────────────────┘
```

### 3.2 技术栈与改造映射

| 技术领域 | 现有项目技术栈 | 本次需求技术栈 | 匹配度 | 改造说明 |
|----------|----------------|----------------|--------|----------|
| 前端 | Vue3 + Vite + Element Plus + ECharts + Axios + Pinia | Vue3 + Vite + Element Plus + ECharts + Axios + Pinia | 100% | 完全复用，无需改造 |
| 后端 | SpringBoot 3.x + Spring MVC + MyBatis-Plus + Spring Security | SpringBoot 3.x + Spring MVC + MyBatis-Plus + Spring Security | 100% | 完全复用，无需改造 |
| 数据库 | MySQL 8.0 + Neo4j 5.x | MySQL 8.0 + Neo4j 5.x + Milvus | 90% | 补充 Milvus 向量数据库 |
| AI 服务 | DeepSeek API + 基础 RAG | DeepSeek + LangChain + 分层 RAG | 85% | 升级为分层检索增强架构 |
| 部署 | Docker + Nginx + SLF4J + Logback | Docker + Nginx + SLF4J + Logback + Prometheus | 100% | 补充监控能力 |

> 当前实际落地的技术栈（重构后）：后端 Spring Boot 3.4.4 + MyBatis-Plus 3.5 + Java 21 + Lombok + JWT(jjwt) + springdoc；前端 Vue 3 + Vite + Element Plus + Pinia + axios + echarts + xlsx + dompurify。后端 context `/api`，前端 baseURL `/api`；前端端口 5173，后端端口 8080。

### 3.3 专业知识图谱构建方案

- **数据源**：官方教材谢希仁《计算机网络》（第 8 版）、江苏科技大学官方教学大纲、计算机学院标准化课件、历年期末考试/考研真题、教师授课 PPT 与补充材料。
- **构建流程**：
  - 步骤 1 知识结构化梳理（课程→章节→知识点→子知识点，属性含难度等级、重要程度、前置依赖、后续延伸）；
  - 步骤 2 知识抽取与实体识别（NER 提取概念/定理/协议，关系抽取前置/后续/包含/关联，AI 辅助抽取置信度 ≥ 80% 自动入库）；
  - 步骤 3 知识存储与可视化（Neo4j 存储，拖拽缩放、节点详情、关联路径查询、知识点溯源）。
- **核心实体设计**：

| 实体类型 | 核心属性 | 关系类型 |
|----------|----------|----------|
| 知识点 | 名称、描述、难度、重要程度 | 前置依赖、后续延伸、包含 |
| 概念 | 定义、公式、示例 | 属于知识点、关联概念 |
| 协议 | RFC 编号、层次、功能 | 属于知识点、关联协议 |
| 题型 | 题干、答案、解析 | 考察知识点、难度等级 |

### 3.4 分层 RAG 检索增强生成框架（核心技术点）

- **第一层：知识图谱精准检索**——基于 Cypher 查询语言结构化检索，支持多跳关系推理 A→B→C 链式知识查询，知识点依赖路径自动发现。
- **第二层：向量语义检索**——使用 BGE-large-zh 嵌入模型知识向量化，Milvus 向量库 ANN 近似最近邻检索，语义相似度匹配阈值动态可调。
- **第三层：全文关键词检索**——Elasticsearch 全文检索兜底，关键词高亮、片段召回，多源数据融合排序。

**RAG 工作流程**：

```
用户提问 → 查询理解与重写 → 分层并行检索 → 结果重排序
     ↓
大模型生成 ← Prompt工程组装 ← 知识上下文构建 ← 知识去重融合
```

**关键技术点**：查询重写；上下文窗口管理（智能截断与重要片段保留）；引用溯源（答案标注知识来源，支持点击跳转）；幻觉检测（生成内容与检索知识一致性校验）。

### 3.5 核心数据模型（进阶版需新增实体）

| 实体 | 用途 | 档位 |
|------|------|------|
| Tenant / OrganizationNode | 多租户与组织树 | 对标超星 |
| Permission / RolePermission（重构） | 细粒度权限 | 对标超星 |
| GradeRule / GradeComponent | 过程性成绩权重模型 | 对标超星 |
| Interaction / SignIn / Vote / Discussion / LiveRoom | 课堂互动 | 对标超星 |
| ProctorEvent | 考试防作弊事件 | 对标超星 |
| DigitalHumanSession | 数字人答疑会话 | **超越超星** |
| Achievement / Badge | 游戏化成长 | **超越超星** |
| AIUsageLog | AI 调用审计与成本 | **超越超星** |

### 3.6 关键业务流程

**(1) 建课 → 学习 → 作业/考试 → 成绩 → 互动 闭环**

```
教师建课(课程/章节/任务点)
   │
   ▼
学生加入班级 ──► 按章节学习(视频/文档/测验任务点)
   │                    │
   │                    ▼
   │              任务点完成度采集(防拖拽/防快进)
   │                    │
   ├──────────► 提交作业 / 参加在线考试(防作弊)
   │                    │
   │                    ▼
   │              自动批改(客观) + 教师/AI 批改(主观)
   │                    │
   │                    ▼
   └──────────► 过程性成绩合成(权重模型) ──► 学情看板/预警
                        │
                        ▼
                  AI 学情诊断 + 个性化路径推荐
                        │
                        ▼
                  课堂互动(签到/讨论/直播) 反馈回学习数据
```

**(2) 任务点完成度与防拖拽/防快进**

| 机制 | 说明 | 进阶档位 |
|------|------|----------|
| 观看时长校验 | 视频观看进度 ≥ 设定阈值（默认 90%）才计完成 | 对标超星 |
| 防拖拽 | 限制进度条拖动到未观看区域 | 对标超星 |
| 防快进 | 限制倍速上限（如 ≤ 2x）或快进不计时长 | 对标超星 |
| 防窗口切换 | 切出页面暂停计时 + 异常行为上报 | 对标超星 |
| **智能完成判定** | 结合弹题作答、笔记、回看行为综合判定掌握度 | **超越超星** |

**(3) 过程性成绩合成模型**

```
总评 = Σ( 组件ᵢ × 权重ᵢ )
组件 ∈ { 章节任务点, 章节测验, 作业, 考试, 分组PBL, 签到, 课程积分, 讨论, 线下 }
约束：Σ权重 = 100%；支持按班级/按人明细分配
```

---

## 四、三端 PRD（管理员 / 教师 / 学生）

> 需求编号体系：`F-{Role}-{NNN}`（F-Admin / F-Teacher / F-Student / F-AI）。优先级：P0 核心闭环 MVP 必做；P1 完整版；P2 创新版/迭代。

### 4.1 管理员端 PRD（8 大功能域，F-Admin-001…704）

| 功能域 | 节 | 关键需求编号 |
|--------|----|--------------|
| 组织与用户管理 | 3.1 | F-Admin-001~006 |
| 课程管理 | 3.2 | F-Admin-101~106 |
| 教学运行监管 | 3.3 | F-Admin-201~204 |
| 数据与报表 | 3.4 | F-Admin-301~305 |
| 资源管理 | 3.5 | F-Admin-401~404 |
| 系统配置 | 3.6 | F-Admin-501~505 |
| 考试与防作弊治理 | 3.7 | F-Admin-601~604 |
| 运营 | 3.8 | F-Admin-701~704 |

### 4.2 教师端 PRD（12 大功能域，F-Teacher-001…1105）

| 功能域 | 节 | 关键需求编号 |
|--------|----|--------------|
| 课程建设 | 4.1 | F-Teacher-001~005 |
| 资源管理 | 4.2 | F-Teacher-101~106 |
| 章节任务点 | 4.3 | F-Teacher-201~205 |
| 直播教学 | 4.4 | F-Teacher-301~304 |
| 作业与题库 | 4.5 | F-Teacher-401~408 |
| 在线考试 | 4.6 | F-Teacher-501~506 |
| 课堂互动 | 4.7 | F-Teacher-601~606 |
| 答疑与讨论 | 4.8 | F-Teacher-701~704 |
| 过程性成绩 | 4.9 | F-Teacher-801~805 |
| 学情统计 | 4.10 | F-Teacher-901~904 |
| 移动端教学 | 4.11 | F-Teacher-1001~1003 |
| AI 教学能力 | 4.12 | F-Teacher-1101~1105 |

### 4.3 学生端 PRD（9 大功能域，F-Student-001…805）

| 功能域 | 节 | 关键需求编号 |
|--------|----|--------------|
| 学习空间 / 首页 | 5.1 | F-Student-001~004 |
| 课程学习 | 5.2 | F-Student-101~106 |
| 作业与考试 | 5.3 | F-Student-201~205 |
| 互动 | 5.4 | F-Student-301~306 |
| 通识 / 尔雅课 | 5.5 | F-Student-401~402 |
| 笔记与收藏 | 5.6 | F-Student-501~503 |
| 个人中心 | 5.7 | F-Student-601~604 |
| 移动端 | 5.8 | F-Student-701~703 |
| AI 学习能力 | 5.9 | F-Student-801~805 |

---

## 五、AI 能力专题（F-AI-001…705）

| 节 | 主题 | 定位/要点 |
|----|------|----------|
| 6.1 | AI 助教（课程专属知识库智能体） | 每位教师训练专属 AI 助教，基于课程资料 7×24 答疑；含 F-AI-001~006（知识库/RAG 强溯源/多模型路由/多轮/教师认领/数字人接入） |
| 6.2 | 知识图谱 | 学科/课程/能力/思政多类型图谱，驱动闯关、自适应、诊断；F-AI-101~105（抽取/关系/多类型/任务引擎/AI 助建课） |
| 6.3 | 智能出题与自动批阅 | F-AI-201~205（AI 出题/智能组卷/客观自动批/主观多模态批阅/一致性校验） |
| 6.4 | 学情诊断与预警 | F-AI-301~304（掌握度诊断/薄弱点归因/预测式预警/干预建议） |
| 6.5 | 个性化学习路径 | F-AI-401~404（路径生成/自适应调节/分层练习/节奏预测） |
| 6.6 | AIGC 检测与作业查重 | F-AI-501~504（文本查重/AIGC 率/多模态查重/申诉流） |
| 6.7 | 智能体平台（低代码搭建校级/课程智能体） | F-AI-601~603（创建向导/智能体市场/工具插件） |
| 6.8 | AI 基础设施（统一契约 + 审计 + 成本） | F-AI-701~705（多供应商路由降级/统一 API 契约/审计日志/成本熔断/微调接入） |

> **结论（6.9 自检）**：AI 全栈能力在「溯源、多模态、自适应、智能体、基础设施审计」五个维度**全面超越**超星文本式智能体。

### 5.1 核心 AI 功能实现方案（6 项）

1. **AI 智能出题**：后端构造结构化 Prompt，传入知识点、题型、难度等参数，调用 DeepSeek 生成 JSON 格式试题，解析后入库，支持教师手动修正。
2. **知识点关系抽取**：对上传的课程大纲/课件进行文本分割与向量化，调用大模型抽取知识点及关系，计算置信度，置信度 ≥ 80% 自动入库，低于阈值提示教师审核。
3. **双视角学情分析**：聚合学生作业、考试数据，统计各知识点掌握率，通过 Prompt 传入结构化数据，AI 生成班级/个人分析报告与优化建议，前端通过 ECharts 可视化展示。
4. **个性化学习路径规划**：结合知识图谱的知识点依赖关系与学生学情数据，使用拓扑排序算法确定学习顺序，AI 生成每日任务与阶段目标，每完成一个阶段自动更新路径。
5. **智能问答**：学生提问后，先检索 Neo4j 知识图谱与 Milvus 向量数据库中的关联数据，拼接上下文 Prompt 调用大模型，生成精准答疑内容，支持代码块高亮与公式渲染。
6. **作业查重**：对学生提交的作业内容进行向量化，与已有作业库进行相似度计算，超过阈值提示重复，支持教师查看重复内容。

---

## 六、超越超星的创新功能（核心竞争力）

> 本章是本平台相对超星学习通的**核心竞争力**。超星以「资源 + 流程」见长，AI 能力为文本式附加模块；本平台以「AI 原生 + 体验代差」建立优势。

| 创新项 | 超越超星点 | 关联需求 | 落地阶段 |
|--------|------------|----------|----------|
| 7.1 AI 数字人教师 | 形象+语音+情感（超星仅文本） | F-AI-006、F-Student-804 | 创新版 |
| 7.2 自适应学习引擎 | 动态调节（超星静态闯关） | F-AI-402、F-Student-106 | 完整版→创新版 |
| 7.3 多模态作业批改 | 图/语音/代码/公式（超星偏文本） | F-AI-204、F-AI-503、F-Student-205、F-Teacher-407 | 完整版→创新版 |
| 7.4 学习社区图谱 | 同伴互助网络（超星社交弱） | F-Student-306、F-Student-604 | 创新版 |
| 7.5 游戏化成长 | 体系化成长（超星仅积分） | F-Student-603、F-Admin-702 | 完整版 |
| 7.6 虚实融合实验 | 可交互仿真（超星演示为主） | — | 创新版 |

**各项依据（逐字摘要）**：

- **7.1 AI 数字人教师**：为每门课程提供有形象、可语音、可情感化陪伴的「数字人教师」，替代/增强纯文本 AI 助教（形象 + 语音合成 TTS + 表情驱动 + 课程人设）。
- **7.2 自适应学习引擎**：基于知识图谱 + 实时学情，动态调整学习难度、顺序与任务供给，实现「千人千面」（超星以静态闯关为主）。
- **7.3 多模态作业批改**：支持图、语音、代码、公式、手写等多种作业形态的 AI 批改（超星以文本作业 + 客观题自动批为主）。
- **7.4 学习社区知识图谱**：将学生、知识点、互动行为建成「学习社交图谱」，实现同伴互助推荐与协同过滤（超星社交弱）。
- **7.5 游戏化成长体系**：积分、勋章、排行、学习战令提升持续参与度（超星仅有课堂积分）。
- **7.6 虚实融合实验**：提供可操作、可出错、AI 指导的仿真实验台（超星以视频/文档演示为主）。

> 至少落地 7.1–7.5 五项（满足「≥4 项」），7.6 作为创新版探索。

---

## 七、完整功能 — 接口矩阵（权威清单，共 93 个）

### 7.1 统一响应格式

- 成功：`{"code":200,"message":"success","data":{/* ... */},"timestamp":1620000000000}`
- 错误：`{"code":400/401/403/404/500,"message":"错误描述","data":null,"timestamp":1620000000000}`
- 分页：`{"code":200,"message":"success","data":{"total":100,"list":[],"page":1,"pageSize":10,"totalPages":10},"timestamp":1620000000000}`

### 7.2 接口清单

| # | 模块/分类 | 方法 | 路径 | 功能描述 | 请求/响应要点 | 权限 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 公共 | POST | /api/auth/login | 账号密码登录、JWT 令牌生成 | 请求体：username, password；响应：token, userInfo, roles | 匿名 |
| 2 | 公共 | POST | /api/auth/register | 手机号/邮箱验证码注册、角色选择 | 请求体：username, password, email, role, realName, verifyCode；响应：userId, message | 匿名 |
| 3 | 公共 | POST | /api/auth/refresh | JWT 令牌过期自动刷新 | 请求体：refreshToken；响应：token, refreshToken | 已登录 |
| 4 | 公共 | GET | /api/auth/me | 获取登录用户详细信息与权限 | 无；响应：userId, username, email, roles, avatar | 已登录 |
| 5 | 公共 | PUT | /api/auth/password | 登录用户修改个人密码 | 请求体：oldPassword, newPassword | 已登录 |
| 6 | 公共 | POST | /api/common/upload | 单文件上传，支持图片/文档/视频 | 请求体：file, type；响应：fileId, fileUrl, fileName, fileSize | 已登录 |
| 7 | 公共 | POST | /api/common/upload/batch | 多文件同时上传 | 请求体：files；响应：files | 已登录 |
| 8 | 公共 | POST | /api/common/verify-code | 注册/重置密码验证码发送 | 请求体：email/phone, type；响应：success, expireTime | 匿名 |
| 9 | 公共 | POST | /api/auth/reset-password | 通过验证码找回密码 | 请求体：email/phone, verifyCode, newPassword | 匿名 |
| 10 | 教师·课程 | POST | /api/teacher/courses | 新建课程，填写基本信息 | — | 教师 |
| 11 | 教师·课程 | GET | /api/teacher/courses | 分页查询教师创建的所有课程 | — | 教师 |
| 12 | 教师·课程 | GET | /api/teacher/courses/{courseId} | 查看课程完整信息与章节结构 | — | 教师 |
| 13 | 教师·课程 | PUT | /api/teacher/courses/{courseId} | 修改课程基本信息与状态 | — | 教师 |
| 14 | 教师·课程 | DELETE | /api/teacher/courses/{courseId} | 删除未使用的课程 | — | 教师 |
| 15 | 教师·课程 | POST | /api/teacher/courses/{courseId}/chapters | 创建课程章节，支持多级结构 | — | 教师 |
| 16 | 教师·课程 | PUT | /api/teacher/courses/{courseId}/chapters/{chapterId} | 修改章节名称与内容 | — | 教师 |
| 17 | 教师·课程 | DELETE | /api/teacher/courses/{courseId}/chapters/{chapterId} | 删除课程章节 | — | 教师 |
| 18 | 教师·作业 | POST | /api/teacher/assignments | 新建作业，配置题目与截止时间 | — | 教师 |
| 19 | 教师·作业 | GET | /api/teacher/assignments | 分页查询所有作业及状态 | — | 教师 |
| 20 | 教师·作业 | GET | /api/teacher/assignments/{assignmentId} | 查看作业题目、提交情况统计 | — | 教师 |
| 21 | 教师·作业 | PUT | /api/teacher/assignments/{assignmentId} | 修改作业信息与截止时间 | — | 教师 |
| 22 | 教师·作业 | DELETE | /api/teacher/assignments/{assignmentId} | 删除未发布的作业 | — | 教师 |
| 23 | 教师·作业 | GET | /api/teacher/assignments/{assignmentId}/submissions | 查看所有学生作业提交情况 | — | 教师 |
| 24 | 教师·作业 | PUT | /api/teacher/assignments/{assignmentId}/submissions/{submissionId} | 给学生作业打分、写评语 | — | 教师 |
| 25 | 教师·作业 | PUT | /api/teacher/assignments/{assignmentId}/publish | 正式发布作业给学生 | — | 教师 |
| 26 | 教师·题库 | POST | /api/teacher/questions | 手动创建单选/多选/判断/简答题 | — | 教师 |
| 27 | 教师·题库 | GET | /api/teacher/questions | 按题型/难度/知识点筛选题目 | — | 教师 |
| 28 | 教师·题库 | GET | /api/teacher/questions/{questionId} | 查看题目完整信息与解析 | — | 教师 |
| 29 | 教师·题库 | PUT | /api/teacher/questions/{questionId} | 修改题目内容、选项、答案 | — | 教师 |
| 30 | 教师·题库 | DELETE | /api/teacher/questions/{questionId} | 删除未使用的题目 | — | 教师 |
| 31 | 教师·题库 | POST | /api/teacher/questions/import | Excel/JSON 批量导入题库 | — | 教师 |
| 32 | 教师·题库 | GET | /api/teacher/questions/export | 导出题目为 Excel 文件 | — | 教师 |
| 33 | 教师·学情 | GET | /api/teacher/analytics/class/{courseId} | 班级整体成绩、完成率、知识点掌握 | — | 教师 |
| 34 | 教师·学情 | GET | /api/teacher/analytics/students/{studentId} | 单个学生学习进度、分数趋势、薄弱点 | — | 教师 |
| 35 | 教师·学情 | GET | /api/teacher/analytics/assignments/{assignmentId} | 作业提交率、平均分、分数分布 | — | 教师 |
| 36 | 教师·学情 | GET | /api/teacher/analytics/knowledge-points | 各知识点掌握率与错误统计 | — | 教师 |
| 37 | 教师·学情 | GET | /api/teacher/analytics/report/export | 学情分析报告 PDF 导出 | — | 教师 |
| 38 | 学生·学习中心 | GET | /api/student/courses | 已加入课程列表与学习进度 | — | 学生 |
| 39 | 学生·学习中心 | GET | /api/student/courses/{courseId} | 课程章节、资源、学习进度 | — | 学生 |
| 40 | 学生·学习中心 | POST | /api/student/courses/{courseId}/progress | 记录学习时长与完成状态 | — | 学生 |
| 41 | 学生·学习中心 | POST | /api/student/courses/{courseId}/enroll | 自由加入或申请加入课程 | — | 学生 |
| 42 | 学生·学习中心 | DELETE | /api/student/courses/{courseId}/enroll | 退出已加入的课程 | — | 学生 |
| 43 | 学生·作业 | GET | /api/student/assignments/pending | 待提交作业列表与倒计时 | — | 学生 |
| 44 | 学生·作业 | GET | /api/student/assignments/completed | 已提交作业与批改结果 | — | 学生 |
| 45 | 学生·作业 | GET | /api/student/assignments/{assignmentId} | 作业题目与我的提交记录 | — | 学生 |
| 46 | 学生·作业 | POST | /api/student/assignments/{assignmentId}/submit | 提交作业答案与附件 | — | 学生 |
| 47 | 学生·作业 | GET | /api/student/assignments/{assignmentId}/result | 查看分数、评语、正确答案 | — | 学生 |
| 48 | 学生·错题本 | GET | /api/student/wrong-questions | 按课程/知识点筛选错题 | — | 学生 |
| 49 | 学生·错题本 | POST | /api/student/wrong-questions | 手动添加错题到错题本 | — | 学生 |
| 50 | 学生·错题本 | PUT | /api/student/wrong-questions/{id}/mastered | 标记错题已掌握 | — | 学生 |
| 51 | 学生·错题本 | DELETE | /api/student/wrong-questions/{id} | 从错题本删除题目 | — | 学生 |
| 52 | 学生·错题本 | POST | /api/student/wrong-questions/practice | 生成错题专项练习 | — | 学生 |
| 53 | 学生·进度 | GET | /api/student/progress/overview | 总学习时长、课程数、平均分 | — | 学生 |
| 54 | 学生·进度 | GET | /api/student/progress/calendar | 每日学习时长日历展示 | — | 学生 |
| 55 | 学生·进度 | GET | /api/student/progress/statistics | 学习趋势、知识点掌握情况 | — | 学生 |
| 56 | 管理员·用户 | GET | /api/admin/users | 按角色/状态/关键词筛选用户 | — | 管理员 |
| 57 | 管理员·用户 | GET | /api/admin/users/{userId} | 查看用户详细信息 | — | 管理员 |
| 58 | 管理员·用户 | POST | /api/admin/users | 后台创建用户账号 | — | 管理员 |
| 59 | 管理员·用户 | PUT | /api/admin/users/{userId} | 修改用户信息与状态 | — | 管理员 |
| 60 | 管理员·用户 | DELETE | /api/admin/users/{userId} | 删除用户账号 | — | 管理员 |
| 61 | 管理员·用户 | PUT | /api/admin/users/{userId}/reset-password | 重置用户登录密码 | — | 管理员 |
| 62 | 管理员·用户 | POST | /api/admin/users/import | Excel 批量导入用户 | — | 管理员 |
| 63 | 管理员·配置 | GET | /api/admin/configs | 获取所有系统配置项 | — | 管理员 |
| 64 | 管理员·配置 | PUT | /api/admin/configs/{configKey} | 修改系统配置参数 | — | 管理员 |
| 65 | 管理员·配置 | GET | /api/admin/logs | 查看操作日志与错误日志 | — | 管理员 |
| 66 | 管理员·配置 | GET | /api/admin/health | 系统与各服务健康状态 | — | 管理员 |
| 67 | 管理员·统计 | GET | /api/admin/statistics/overview | 用户、课程、作业总数统计 | — | 管理员 |
| 68 | 管理员·统计 | GET | /api/admin/statistics/user-growth | 用户注册增长趋势 | — | 管理员 |
| 69 | 管理员·统计 | GET | /api/admin/statistics/courses | 课程学习数据统计 | — | 管理员 |
| 70 | 管理员·统计 | GET | /api/admin/statistics/ai-usage | AI 功能调用次数与费用 | — | 管理员 |
| 71 | 管理员·统计 | GET | /api/admin/statistics/export | 数据报表导出 | — | 管理员 |
| 72 | 管理员·统计 | POST | /api/admin/backup | 数据库全量/增量备份 | — | 管理员 |
| 73 | AI | POST | /api/ai/qa/ask | RAG 增强专业问题解答 | — | 已登录 |
| 74 | AI | POST | /api/ai/qa/continue | 多轮上下文连续对话 | — | 已登录 |
| 75 | AI | GET | /api/ai/qa/sessions | 历史问答记录 | — | 已登录 |
| 76 | AI | POST | /api/ai/essay/correct | AI 自动批改作文 | — | 已登录 |
| 77 | AI | POST | /api/ai/essay/polish | AI 优化作文表达 | — | 已登录 |
| 78 | AI | GET | /api/ai/recommendations/learning | 个性化学习内容推荐 | — | 学生 |
| 79 | AI | GET | /api/ai/recommendations/questions | 智能推荐作业题目 | — | 教师 |
| 80 | AI | GET | /api/ai/analysis/weak-points | 知识点薄弱项诊断 | — | 学生/教师 |
| 81 | AI | POST | /api/ai/learning-path/generate | AI 生成学习路径 | — | 学生 |
| 82 | 知识图谱 | GET | /api/knowledge/points/{pointId} | 知识点完整信息 | — | 已登录 |
| 83 | 知识图谱 | GET | /api/knowledge/points | 知识点树形结构 | — | 已登录 |
| 84 | 知识图谱 | GET | /api/knowledge/points/{pointId}/relations | 关联知识点图谱数据 | — | 已登录 |
| 85 | 知识图谱 | GET | /api/knowledge/points/{pointId}/prerequisites | 学习依赖路径 | — | 已登录 |
| 86 | 知识图谱 | GET | /api/knowledge/points/{pointId}/successors | 后续学习建议 | — | 已登录 |
| 87 | 知识图谱 | POST | /api/knowledge/path/plan | 起点到目标的最优路径 | — | 已登录 |
| 88 | 知识图谱 | GET | /api/knowledge/search | 全文搜索知识点 | — | 已登录 |
| 89 | 知识图谱 | GET | /api/knowledge/overview | 知识图谱统计信息 | — | 已登录 |
| 90 | 知识图谱 | POST | /api/knowledge/points | 新增知识点节点 | — | 教师/管理员 |
| 91 | 知识图谱 | POST | /api/knowledge/relations | 新增知识点关联 | — | 教师/管理员 |
| 92 | 知识图谱 | PUT | /api/knowledge/points/{pointId} | 修改知识点信息 | — | 教师/管理员 |
| 93 | 知识图谱 | DELETE | /api/knowledge/points/{pointId} | 删除知识点节点 | — | 教师/管理员 |

> 注：第 5.2 节（公共模块）原文含完整请求/响应参数；其余模块原文仅列接口名称/方法/URL/权限，上表以「—」标注未列项。后端经统一重构后，`/api/ai/**` 与 `/api/knowledge/**` 已由统一 `AIController` / `RAGController` / `KnowledgeController` 承接，实际路径以当前代码 springdoc 文档为准。

---

## 八、核心数据表设计（权威清单，共 20 张）

### 8.1 用户与权限

| 表名 | 核心字段 |
|------|----------|
| **user** | id, username, password, name, role, status, phone, email, phone_verified, email_verified, avatar, last_login_time, login_ip, created_at, updated_at |
| **system_config** | id, config_key, config_value, config_group, description, created_at, updated_at |
| **role_permission** | id, role, permission_key, permission_name, created_at |
| **login_log** | id, user_id, login_time, ip, device, status, created_at |

### 8.2 课程与选课

| 表名 | 核心字段 |
|------|----------|
| **course** | id, course_code, course_name, teacher_id, status, start_time, end_time, enrollment_type, description, cover_image, course_outline, created_at, updated_at |
| **course_student** | id, course_id, student_id, join_time, approval_status, approval_time, progress, created_at |
| **course_chapter** | id, course_id, parent_id, chapter_name, content, sort, created_at, updated_at |

### 8.3 知识图谱

| 表名 | 核心字段 |
|------|----------|
| **knowledge_point** | id, name, course_id, parent_id, description, difficulty, extraction_method, created_at, updated_at |
| **knowledge_relation** | id, knowledge_id, related_id, relation_type, confidence, created_at |

### 8.4 资源与作业

| 表名 | 核心字段 |
|------|----------|
| **learning_resource** | id, resource_name, resource_type, category, course_id, chapter_id, knowledge_ids, file_path, video_url, file_size, version, upload_time, comment_count, favorite_count |
| **course_assignment** | id, assignment_name, course_id, question_ids, total_score, publish_time, deadline, target_scope, created_at |
| **student_submission** | id, assignment_id, student_id, submit_content, submit_time, status, score, teacher_comment, graded_at, resubmit_count, similarity |

### 8.5 题库与考试

| 表名 | 核心字段 |
|------|----------|
| **question_bank** | id, question_text, question_type, options, correct_answer, explanation, difficulty, score, course_id, knowledge_ids, created_by, created_at |
| **mock_exam** | id, exam_name, course_id, student_id, question_ids, duration_minutes, total_score, created_at |
| **exam_record** | id, exam_id, student_id, answers, score, start_time, end_time, status, created_at |
| **wrong_question** | id, student_id, question_id, wrong_answer, reason, mastered, created_at, updated_at |

### 8.6 AI 业务相关

| 表名 | 核心字段 |
|------|----------|
| **learning_path** | id, student_id, course_id, path_name, stage, plan_detail, daily_tasks, created_at, updated_at |
| **qa_record** | id, student_id, question, question_type, answer, related_knowledge, related_resources, session_id, asked_at, answered_at |
| **ai_usage_log** | id, user_id, function_type, call_time, cost, status, response_time, created_at |
| **student_analytics** | id, student_id, course_id, study_time, completion_rate, avg_score, weak_points, created_at, updated_at |

---

## 九、非功能需求

### 9.1 性能与并发（目标）

| 指标 | 目标 | 档位 |
|------|------|------|
| 首页/课程页加载 | < 1.5s（P95） | 对标超星 |
| 视频首帧 | < 2s | 对标超星 |
| 在线考试并发 | 单校 ≥ 1 万同时在线，全校 ≥ 5 万 | 对标超星 |
| API 响应 | 普通 < 300ms，AI 类 < 5s（流式） | 对标超星 |
| 批量导入 | 5000 用户 < 2min | 对标超星 |

要求：前端路由懒加载 + 分包；后端热点接口缓存（Redis）；数据库读写分离（大校）；AI 请求异步化与限流。

### 9.2 安全与等保

等保三级（身份认证/访问控制/审计/密评）；JWT 无状态+刷新令牌、失败锁定、敏感二次验证；全站 HTTPS/TLS1.3；考试人脸+行为双因子；多租户 `tenant_id` 行级隔离；`@PreAuthorize`+数据权限拦截器；全量审计+AI 调用审计。**超越点**：AI 调用审计与成本熔断为超星未公开能力（F-AI-703/704）。

### 9.3 可用性与容灾

核心可用性 ≥ 99.9% SLA；SaaS/混合/私有化三形态；主从+备份+Redis 高可用；AI 供应商故障自动切换（F-AI-701）；故障演练与回滚。

### 9.4 可观测性

统一日志（ELK/SLS）+ 链路追踪（OpenTelemetry）+ Metrics（Prometheus/Grafana）；前端错误监控（Sentry）；业务看板与数据统计打通。

### 9.5 国际化与合规

i18n 中/英（预留更多）；Web+移动端+未来教室端响应式；基础无障碍（对比度/键盘/读屏）。合规：个保法（最小必要、授权与导出/删除权）；未成年人保护；内容安全（AI 政治/知识/版权三关）；AI 伦理（评分可解释、可申诉、不替代教师最终判定）。

### 9.6 工程化

| 项 | 目标 | 档位 |
|----|------|------|
| 语言 | 前端全面 TypeScript（替换 JS） | 超越 |
| 测试 | 单测覆盖率 ≥ 60%，关键服务集成测试 | 对标+超越 |
| 组件 | Design System + Storybook 文档 | 超越 |
| CI/CD | 自动化构建/测试/部署流水线 | 对标 |
| 文档 | API 文档（springdoc）+ 本 PRD 联动 | 对标 |

---

## 十、现有代码映射与重构路线

> 按模块给出「基础版 → 进阶版」映射，并标注重构优先级。所有 P0 项均为「现有基础版不可用/不满意」，须重点投入。

| 优先级 | 模块 | 说明 |
|--------|------|------|
| **P0（必重做）** | 组织树/权限、任务点闭环、结构化题库、考试防作弊、过程性成绩、课堂互动、视频播放器、RAG 契约 | 短板，决定可用性底线 |
| **P1（进阶增强）** | 学情 AI 诊断、自适应路径、AI 出题/批阅、数字人、成长体系、知识图谱多类型 | 超越主轴 |
| **P2（打磨）** | 游戏化、虚实实验、国际化、工程化 | 创新版/迭代 |

### 10.1 关键模块映射（节选）

| 编号 | 描述 | 现状基础版 | 进阶目标 | 重构建议 |
|------|------|------------|----------|----------|
| F-Admin-001/003 | 组织树+细粒度权限 | 仅 department 字符串+班级；权限未生效 | **对标+超越** | 新增 Tenant/OrganizationNode；RBAC 落地 @PreAuthorize + 数据权限拦截器 |
| F-Teacher-201~203 | 任务点闭环 | 未强绑定、无完成度/防拖拽 | 对标 | 任务点完成判定服务 + 防拖拽/快进校验 |
| F-Student-102 | 视频播放器 | 视频占位 | 对标 | 接入真实播放器（video.js）+ 防拖拽 UI |
| F-Teacher-401 | 结构化题库 | 非结构化 JSON 存选项/答案 | **对标+超越** | 拆 QuestionOption/QuestionAnswer/KnowledgePoint 关联表 |
| F-Teacher-406/407 | 智能组卷/AI批阅 | 缺失 | **超越** | 新增组卷服务 + F-AI-204 多模态批阅 |
| F-Teacher-501~505 | 组卷/阅卷 | 基础组卷+记录 IP/UA | 对标+超越(AI监考) | 新增 ProctorEvent；前端切屏/摄像头/锁屏/乱序 |
| F-Teacher-801~803 | 过程性成绩 | 缺权重模型 | 对标 | 新增 GradeRule/GradeComponent + 合成服务 |
| F-Teacher-601~606 | 签到/投票/讨论/分组 | 仅 Notification/Reminder | **对标+超越** | 新增 Interaction/SignIn/Vote/Discussion/LiveRoom |
| F-AI-001~005 | AI 助教/RAG | 前缀不统一、返回裸 String | **对标+超越** | 统一 API 契约 + 结构化返回 + 引用溯源 + 教师端接入 |
| F-AI-701~705 | AI 基础设施 | 多供应商切换有，审计/成本缺 | **超越** | AI 审计日志 + 成本熔断 |

---

## 十一、里程碑与 MVP 范围

按「对标超星（可用）→ 完整版（好用）→ 创新版（超越）」三阶段推进。

### 阶段一：MVP（对标超星 · 可用底线）

- 组织树 + 细粒度权限落地（F-Admin-001/003）
- 步骤建课 + 章节任务点闭环 + 防拖拽（F-Teacher-001~003、F-Teacher-201~203、F-Student-101/102）
- 真实视频播放器 + 完成判定（F-Student-102）
- 结构化题库 + 作业布置批改（F-Teacher-401~404）
- 在线考试 + 基础防作弊 + 考试治理（F-Teacher-501~505、F-Admin-601/602、F-Student-202）
- 课堂互动（签到/投票/讨论/分组）（F-Teacher-601~605、F-Student-301~305）
- 过程性成绩模型（F-Teacher-801~803）
- 学情看板（基础）（F-Teacher-901~903、F-Admin-301/302）
- RAG 统一契约 + AI 助教（学生端）（F-AI-001~003、F-AI-702）
- AI 基础设施路由/降级（F-AI-701）

**退出标准**：三端可走通「建课→学习→作业/考试→成绩→互动」完整闭环；P0 重构项全部完成。

### 阶段二：完整版（好用 · AI 增强）

- AI 辅助建课 / AI 课件（F-Teacher-004/105、F-AI-105）
- AI 出题 / 智能组卷 / 主观题 AI 批阅（F-Teacher-406/407、F-AI-201~204）
- 学情诊断 + 预警 + 教学建议（F-AI-301~304、F-Teacher-904、F-Student-802）
- 个性化学习路径（基础版）（F-AI-401/403、F-Student-803）
- AIGC 检测 + 查重（F-AI-501/502、F-Teacher-408）
- 游戏化成长体系（F-Student-603、F-Admin-702）
- 移动端教学/学习完善（F-Teacher-1001~1003、F-Student-701~703）
- 智能体平台（基础）（F-AI-601/602）
- AI 审计与成本统计（F-AI-703/704）

**退出标准**：教师建课耗时降 ≥ 50%，客观题批改自动化 ≥ 95%，学生疑问 AI 首解率 ≥ 70%。

### 阶段三：创新版（超越超星 · 代差优势）

- AI 数字人教师（F-AI-006、F-Student-804、7.1）
- 自适应学习引擎（F-AI-402、F-Student-106、7.2）
- 多模态作业批改（F-AI-204/503、F-Student-205、7.3）
- 学习社区知识图谱（F-Student-306、7.4）
- 虚实融合实验（7.6）
- 知识图谱多类型 + AI 助建课（F-AI-103/105）
- 微调能力接入（F-AI-705）

**退出标准**：至少 5 项创新功能上线且标注「超越超星」依据；平台在 AI 维度形成可演示的代差优势。

**范围裁剪原则**：① 任何范围变更先在本文档确认阶段归属；② P0（MVP）不可裁剪，P1/P2 可按资源滚动；③ 创新功能可并行试点，但须保证 MVP 闭环先稳。

---

## 十二、核心业务流程（端到端）

### 12.1 教师端核心业务流程

1. 账号准备：教师完成注册 → 管理员审核通过（可选）→ 登录系统
2. 课程搭建：创建课程 → 填写课程基本信息 → 配置选课模式 → 上传课程大纲
3. 知识体系构建：创建知识点 → 配置知识点关系 → 生成课程知识图谱 → AI 辅助完善关系
4. 教学资源准备：按章节上传课件、视频等教学资源 → 关联对应知识点
5. 教学实施：AI 智能出题 → 发布作业 → 查看学生提交情况 → 批改作业 → 生成班级学情报告
6. 教学优化：根据 AI 学情分析报告与教学建议 → 调整教学内容与进度 → 更新教学资源

### 12.2 学生端核心业务流程

1. 账号准备：学生完成注册 → 登录系统
2. 课程选择：浏览课程列表 → 查看课程详情 → 加入课程（或提交选课申请）
3. 自主学习：进入课程 → 按学习路径学习资源 → 完成章节练习
4. 作业与考试：完成教师发布的作业 → 参加模拟考试 → 查看成绩与批改结果 → 错题自动收录
5. 学情诊断与提升：查看个人 AI 学情报告 → 获取薄弱点分析 → 跟随 AI 推荐学习路径学习
6. 疑问解决：遇到学习问题 → 使用智能问答功能 → 查看答疑记录 → 巩固知识点

### 12.3 管理员端核心业务流程

1. 系统初始化：使用初始化账号登录 → 配置系统基础信息 → 设置全局参数
2. 用户管理：审核教师注册申请 → 管理学生账号 → 分配用户角色与权限
3. 系统运维：维护基础字典数据 → 监控系统运行状态 → 查看系统日志
4. AI 服务管理：配置 AI API 密钥 → 监控 AI 服务调用情况 → 处理 AI 服务异常
5. 内容审核：审核违规课程与资源 → 处理用户举报 → 保障系统内容合规
6. 数据管理：定期备份数据库 → 处理数据异常 → 保障数据安全

---

## 十三、附录

### 13.1 术语表（部分）

- **RAG**：检索增强生成（Retrieval-Augmented Generation）
- **知识图谱**：以图结构组织知识点及其关系的知识库
- **分层检索**：图谱精准检索 + 向量语义检索 + 全文关键词检索的三层融合
- **对标超星 / 超越超星**：需求进阶两档目标标签
- **P0/P1/P2**：重构优先级（必重做 / 进阶增强 / 打磨）
- **F-{Role}-{NNN}**：功能需求编号体系

### 13.2 参考链接

- 开源基座：`ai-smart-course-platform`（<https://github.com/Lllxy11/ai-smart-course-platform>，MIT）
- 对标对象：超星学习通（Chaoxing Xuetang）
- 教材：谢希仁《计算机网络》（第 8 版）

### 13.3 文档关联

- 本文件为唯一权威需求基线，取代 `docs/prd/` 体系与 `docs/基于知识图谱+LLM的AI助教系统完整需求文档.md`。
- 配套文档（docs 目录 2026-08-02 合并为 4 份）：
  - `README.md`（项目门面）；
  - `docs/软件产品路线图.md`（战略规划：技术架构演进 + 产品路线图 + 功能扩展可行性分析）；
  - `docs/项目交付与完成度报告.md`（交付清单 + 完成度 + 2026-07-31 审查记录）；
  - `docs/运维手册.md`（部署运维 + Python 微服务接口契约）。

### 13.4 待核实清单

- 原文声称 128 接口，实测枚举 93，缺 35 个无出处（以 93 为准）。
- Neo4j / Milvus 落地状态：当前代码以 MySQL + Redis 为主，图谱/向量为规划能力——知识图谱与 RAG 已由 Java 端自包含实现（直读 MySQL + Okapi BM25），Python 微服务（8001/8002）契约保留为可选扩展基线（见运维手册 §11）。
- 考试防作弊（人脸+行为双因子）前端机制待补全：防切屏检测已实装（Phase 1），人脸/行为双因子为进阶规划。
- 多租户 `tenant_id` 行级隔离为进阶目标（路线图 v2.0 规划），当前实现以单校为主。
