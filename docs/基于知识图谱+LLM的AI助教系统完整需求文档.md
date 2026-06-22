# 基于知识图谱+LLM的AI助教系统完整需求文档

**项目名称**：基于知识图谱 + LLM 的 AI 助教系统设计
**项目来源**：江苏科技大学校级教育教学改革课题《生成式人工智能赋能网络类课程教学改革研究》
**接口总数**：128 个

***

## 目录

1. \[项目整体规划]\(#1-项目整体规划)
2. \[核心技术方案]\(#2-核心技术方案)
3. \[详细功能需求与接口对应]\(#3-详细功能需求与接口对应)
4. \[功能 - 接口完整对应矩阵]\(#4-功能-接口完整对应矩阵)
5. \[完整前后端接口清单]\(#5-完整前后端接口清单)
6. \[核心数据表设计]\(#6-核心数据表设计)
7. \[AI 功能技术实现方案]\(#7-ai功能技术实现方案)
8. \[系统主要业务流程]\(#8-系统主要业务流程)
9. \[非功能性需求]\(#9-非功能性需求)
10. \[实施计划与开发优先级]\(#10-实施计划与开发优先级)
11. \[预期项目成果]\(#11-预期项目成果)

***

## 1. 项目整体规划

### 1.1 项目基础信息

#### 1.1.1 项目定位与背景

本项目针对专业课程教学知识碎片化、个性化学习支持不足、大语言模型存在知识幻觉等行业痛点，融合知识图谱与大语言模型技术，**以《计算机网络》专业课程为试点**，构建基于检索增强生成（RAG）框架的 AI 助教系统。

**核心痛点解决：**

- 解决专业课程知识组织碎片化问题，构建结构化知识网络
- 解决大语言模型 "知识幻觉" 问题，确保专业问答准确性
- 解决个性化学习支持不足，实现因材施教
- 解决师生沟通成本高，提供 7×24 小时智能答疑

#### 1.1.2 项目创新特色

**【应用场景创新】**
区别于市面上面向通用学习场景、仅服务学生端的 AI 问答工具，打造**面向专业课程、覆盖教与学双场景**的一体化智能助教系统。以《计算机网络》为试点，可为其他计算机类专业课程提供可复用、可推广的实现方案。

**【技术融合创新】**
设计适配课程教学场景的专业知识图谱，基于 RAG 技术结合开源大模型 Prompt 工程，**设计分层检索策略**，有效降低专业问答的知识幻觉发生率，提升课程专业问题的问答精准度与多跳逻辑推理能力。

#### 1.1.3 项目开发基础

本项目基于开源项目 `ai-smart-course-platform` 进行二次开发，项目地址：<https://github.com/Lllxy11/ai-smart-course-platform>

- **开源协议**：MIT 协议，支持商业二次开发、定制化改造与闭源部署
- **项目成熟度**：已完成核心框架搭建与基础业务功能开发
- **技术匹配度**：90% 基础技术能力可直接复用，无需重构技术架构

### 1.2 技术栈匹配与改造方案

| 技术领域  | 现有项目技术栈                                                      | 本次需求技术栈                                                      | 匹配度  | 改造说明            |
| ----- | ------------------------------------------------------------ | ------------------------------------------------------------ | ---- | --------------- |
| 前端    | Vue3 + Vite + Element Plus + ECharts + Axios + Pinia         | Vue3 + Vite + Element Plus + ECharts + Axios + Pinia         | 100% | 完全复用，无需改造       |
| 后端    | SpringBoot 3.x + Spring MVC + MyBatis-Plus + Spring Security | SpringBoot 3.x + Spring MVC + MyBatis-Plus + Spring Security | 100% | 完全复用，无需改造       |
| 数据库   | MySQL 8.0 + Neo4j 5.x                                        | MySQL 8.0 + Neo4j 5.x + Milvus                               | 90%  | 补充 Milvus 向量数据库 |
| AI 服务 | DeepSeek API + 基础 RAG                                        | DeepSeek + LangChain + 分层 RAG                                | 85%  | 升级为分层检索增强架构     |
| 部署    | Docker + Nginx + SLF4J + Logback                             | Docker + Nginx + SLF4J + Logback + Prometheus                | 100% | 补充监控能力          |

### 1.3 现有功能复用评估

#### 1.3.1 可直接复用的完整功能

1. 用户与权限体系：用户注册登录、密码找回、三类角色 RBAC 权限隔离
2. 课程管理体系：课程 CRUD、两种选课模式、选课审批
3. 资源管理体系：多类型教学资源上传、预览、下载、大文件分片上传
4. 作业考试体系：题库管理、AI 智能出题、作业发布与批改、模拟考试
5. 知识图谱基础：知识点 CRUD、关系配置、Neo4j 存储、基础可视化

#### 1.3.2 需优化升级的功能

1. 用户体系：补充手机号 / 邮箱验证码注册、密码强度校验、登录失败锁定
2. 权限管理：升级为菜单 + 按钮细粒度权限、支持动态权限配置
3. 学情分析：从仅班级维度升级为教师 / 学生双视角，补充 AI 自动生成建议
4. 图谱能力：新增 AI 自动抽取知识点与关系、节点详情查看、关联路径查询
5. 智能问答：升级为多模态提问、RAG 增强检索、上下文连续对话

#### 1.3.3 需全新开发的功能

1. 学生端个性化学习路径规划与动态调整
2. 个人学情分析与薄弱点诊断
3. 管理员端 AI 服务监控、系统日志管理、数据备份恢复
4. 资源版本管理、课程大纲多级管理、作业催交提醒
5. 学生端错题本功能、学习日历与统计功能

***

## 2. 核心技术方案

### 2.1 系统整体架构（四层分层架构）

```Plain
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

### 2.2 专业课程知识图谱构建方案

#### 2.2.1 数据源与知识范围

**【试点课程：《计算机网络》】**

- 官方教材：谢希仁《计算机网络》（第 8 版）
- 课程大纲：江苏科技大学官方教学大纲
- 教学课件：计算机学院标准化课件
- 真题题库：历年期末考试、考研真题
- 课程讲义：教师授课 PPT 与补充材料

#### 2.2.2 知识图谱构建流程

**步骤 1：知识结构化梳理**

- 界定知识点层级关系：课程 → 章节 → 知识点 → 子知识点
- 明确知识点属性：难度等级、重要程度、前置依赖、后续延伸

**步骤 2：知识抽取与实体识别**

- 采用命名实体识别（NER）提取核心概念、定理、协议
- 关系抽取：前置关系、后续关系、包含关系、关联关系
- AI 辅助抽取：上传文档自动抽取知识点，置信度≥80% 自动入库

**步骤 3：知识存储与可视化**

- Neo4j 图数据库存储节点与关系
- 支持知识图谱拖拽缩放、节点详情查看
- 支持关联路径查询、知识点溯源

#### 2.2.3 知识图谱核心实体设计

| 实体类型 | 核心属性          | 关系类型         |
| ---- | ------------- | ------------ |
| 知识点  | 名称、描述、难度、重要程度 | 前置依赖、后续延伸、包含 |
| 概念   | 定义、公式、示例      | 属于知识点、关联概念   |
| 协议   | RFC 编号、层次、功能  | 属于知识点、关联协议   |
| 题型   | 题干、答案、解析      | 考察知识点、难度等级   |

### 2.3 分层 RAG 检索增强生成框架

#### 2.3.1 分层检索策略（核心技术创新）

**【第一层：知识图谱精准检索】**

- 基于 Cypher 查询语言进行结构化知识检索
- 支持多跳关系推理：A→B→C 链式知识查询
- 知识点依赖路径自动发现

**【第二层：向量语义检索】**

- 使用 BGE-large-zh 嵌入模型进行知识向量化
- Milvus 向量数据库存储，支持 ANN 近似最近邻检索
- 语义相似度匹配，阈值动态可调

**【第三层：全文关键词检索】**

- Elasticsearch 全文检索兜底
- 关键词高亮、片段召回
- 多源数据融合排序

#### 2.3.2 RAG 工作流程

```Plain
用户提问 → 查询理解与重写 → 分层并行检索 → 结果重排序
     ↓
大模型生成 ← Prompt工程组装 ← 知识上下文构建 ← 知识去重融合
```

**关键技术点：**

1. **查询重写**：将学生口语化问题转化为专业查询语句
2. **上下文窗口管理**：智能截断与重要片段保留
3. **引用溯源**：答案标注知识来源，支持点击跳转
4. **幻觉检测**：生成内容与检索知识一致性校验

***

## 3. 详细功能需求与接口对应

### 3.1 用户角色体系

| 角色        | 核心定位 | 功能模块数 | 对应接口数 |
| --------- | ---- | ----- | ----- |
| **学生**    | 学习主体 | 4 大模块 | 18 个  |
| **教师**    | 教学主体 | 5 大模块 | 28 个  |
| **管理员**   | 系统运维 | 3 大模块 | 17 个  |
| **公共**    | 基础服务 | 1 大模块 | 9 个   |
| **AI 功能** | 智能服务 | 1 大模块 | 9 个   |
| **知识图谱**  | 知识服务 | 1 大模块 | 12 个  |

***

## 4. 功能 - 接口完整对应矩阵

### 4.1 公共基础功能 - 接口对应（9 个接口）

| 功能点    | 功能描述                 | 对应接口     | 接口 URL                        |
| ------ | -------------------- | -------- | ----------------------------- |
| 用户登录   | 账号密码登录、JWT 令牌生成      | 用户登录     | POST /api/auth/login          |
| 用户注册   | 手机号 / 邮箱验证码注册、角色选择   | 用户注册     | POST /api/auth/register       |
| 令牌刷新   | JWT 令牌过期自动刷新         | 刷新令牌     | POST /api/auth/refresh        |
| 获取当前用户 | 获取登录用户详细信息与权限        | 获取当前用户信息 | GET /api/auth/me              |
| 修改密码   | 登录用户修改个人密码           | 修改密码     | PUT /api/auth/password        |
| 文件上传   | 单文件上传，支持图片 / 文档 / 视频 | 文件上传     | POST /api/common/upload       |
| 批量文件上传 | 多文件同时上传              | 批量文件上传   | POST /api/common/upload/batch |
| 发送验证码  | 注册 / 重置密码验证码发送       | 发送验证码    | POST /api/common/verify-code  |
| 重置密码   | 通过验证码找回密码            | 重置密码     | POST /api/auth/reset-password |

### 4.2 教师端 - 课程管理功能 - 接口对应（8 个接口）

| 功能点  | 功能描述          | 对应接口   | 接口 URL                                                      |
| ---- | ------------- | ------ | ----------------------------------------------------------- |
| 创建课程 | 新建课程，填写基本信息   | 创建课程   | POST /api/teacher/courses                                   |
| 课程列表 | 分页查询教师创建的所有课程 | 获取课程列表 | GET /api/teacher/courses                                    |
| 课程详情 | 查看课程完整信息与章节结构 | 获取课程详情 | GET /api/teacher/courses/{courseId}                         |
| 更新课程 | 修改课程基本信息与状态   | 更新课程   | PUT /api/teacher/courses/{courseId}                         |
| 删除课程 | 删除未使用的课程      | 删除课程   | DELETE /api/teacher/courses/{courseId}                      |
| 添加章节 | 创建课程章节，支持多级结构 | 添加课程章节 | POST /api/teacher/courses/{courseId}/chapters               |
| 更新章节 | 修改章节名称与内容     | 更新章节   | PUT /api/teacher/courses/{courseId}/chapters/{chapterId}    |
| 删除章节 | 删除课程章节        | 删除章节   | DELETE /api/teacher/courses/{courseId}/chapters/{chapterId} |

### 4.3 教师端 - 作业管理功能 - 接口对应（8 个接口）

| 功能点  | 功能描述           | 对应接口     | 接口 URL                                                                 |
| ---- | -------------- | -------- | ---------------------------------------------------------------------- |
| 创建作业 | 新建作业，配置题目与截止时间 | 创建作业     | POST /api/teacher/assignments                                          |
| 作业列表 | 分页查询所有作业及状态    | 获取作业列表   | GET /api/teacher/assignments                                           |
| 作业详情 | 查看作业题目、提交情况统计  | 获取作业详情   | GET /api/teacher/assignments/{assignmentId}                            |
| 更新作业 | 修改作业信息与截止时间    | 更新作业     | PUT /api/teacher/assignments/{assignmentId}                            |
| 删除作业 | 删除未发布的作业       | 删除作业     | DELETE /api/teacher/assignments/{assignmentId}                         |
| 提交列表 | 查看所有学生作业提交情况   | 获取作业提交列表 | GET /api/teacher/assignments/{assignmentId}/submissions                |
| 批改作业 | 给学生作业打分、写评语    | 批改作业     | PUT /api/teacher/assignments/{assignmentId}/submissions/{submissionId} |
| 发布作业 | 正式发布作业给学生      | 发布作业     | PUT /api/teacher/assignments/{assignmentId}/publish                    |

### 4.4 教师端 - 题库管理功能 - 接口对应（7 个接口）

| 功能点  | 功能描述                   | 对应接口   | 接口 URL                                     |
| ---- | ---------------------- | ------ | ------------------------------------------ |
| 创建题目 | 手动创建单选 / 多选 / 判断 / 简答题 | 创建题目   | POST /api/teacher/questions                |
| 题目列表 | 按题型 / 难度 / 知识点筛选题目     | 获取题目列表 | GET /api/teacher/questions                 |
| 题目详情 | 查看题目完整信息与解析            | 获取题目详情 | GET /api/teacher/questions/{questionId}    |
| 更新题目 | 修改题目内容、选项、答案           | 更新题目   | PUT /api/teacher/questions/{questionId}    |
| 删除题目 | 删除未使用的题目               | 删除题目   | DELETE /api/teacher/questions/{questionId} |
| 批量导入 | Excel/JSON 批量导入题库      | 批量导入题目 | POST /api/teacher/questions/import         |
| 批量导出 | 导出题目为 Excel 文件         | 批量导出题目 | GET /api/teacher/questions/export          |

### 4.5 教师端 - 学情分析功能 - 接口对应（5 个接口）

| 功能点    | 功能描述              | 对应接口      | 接口 URL                                                |
| ------ | ----------------- | --------- | ----------------------------------------------------- |
| 班级学情概览 | 班级整体成绩、完成率、知识点掌握  | 获取班级学情概览  | GET /api/teacher/analytics/class/{courseId}           |
| 学生学习详情 | 单个学生学习进度、分数趋势、薄弱点 | 获取学生学习详情  | GET /api/teacher/analytics/students/{studentId}       |
| 作业统计   | 作业提交率、平均分、分数分布    | 获取作业统计    | GET /api/teacher/analytics/assignments/{assignmentId} |
| 知识点掌握  | 各知识点掌握率与错误统计      | 获取知识点掌握情况 | GET /api/teacher/analytics/knowledge-points           |
| 导出报告   | 学情分析报告 PDF 导出     | 导出学情报告    | GET /api/teacher/analytics/report/export              |

### 4.6 学生端 - 学习中心功能 - 接口对应（5 个接口）

| 功能点    | 功能描述         | 对应接口     | 接口 URL                                        |
| ------ | ------------ | -------- | --------------------------------------------- |
| 我的课程   | 已加入课程列表与学习进度 | 获取我的课程列表 | GET /api/student/courses                      |
| 课程学习详情 | 课程章节、资源、学习进度 | 获取课程学习详情 | GET /api/student/courses/{courseId}           |
| 更新学习进度 | 记录学习时长与完成状态  | 更新学习进度   | POST /api/student/courses/{courseId}/progress |
| 加入课程   | 自由加入或申请加入课程  | 加入课程     | POST /api/student/courses/{courseId}/enroll   |
| 退出课程   | 退出已加入的课程     | 退出课程     | DELETE /api/student/courses/{courseId}/enroll |

### 4.7 学生端 - 作业管理功能 - 接口对应（5 个接口）

| 功能点   | 功能描述         | 对应接口     | 接口 URL                                              |
| ----- | ------------ | -------- | --------------------------------------------------- |
| 待完成作业 | 待提交作业列表与倒计时  | 获取待完成作业  | GET /api/student/assignments/pending                |
| 已完成作业 | 已提交作业与批改结果   | 获取已完成作业  | GET /api/student/assignments/completed              |
| 作业详情  | 作业题目与我的提交记录  | 获取作业详情   | GET /api/student/assignments/{assignmentId}         |
| 提交作业  | 提交作业答案与附件    | 提交作业     | POST /api/student/assignments/{assignmentId}/submit |
| 批改结果  | 查看分数、评语、正确答案 | 获取作业批改结果 | GET /api/student/assignments/{assignmentId}/result  |

### 4.8 学生端 - 错题本功能 - 接口对应（5 个接口）

| 功能点  | 功能描述          | 对应接口   | 接口 URL                                         |
| ---- | ------------- | ------ | ---------------------------------------------- |
| 错题列表 | 按课程 / 知识点筛选错题 | 获取错题列表 | GET /api/student/wrong-questions               |
| 添加错题 | 手动添加错题到错题本    | 添加到错题本 | POST /api/student/wrong-questions              |
| 标记掌握 | 标记错题已掌握       | 标记已掌握  | PUT /api/student/wrong-questions/{id}/mastered |
| 移除错题 | 从错题本删除题目      | 移除错题   | DELETE /api/student/wrong-questions/{id}       |
| 错题练习 | 生成错题专项练习      | 生成错题练习 | POST /api/student/wrong-questions/practice     |

### 4.9 学生端 - 学习进度功能 - 接口对应（3 个接口）

| 功能点  | 功能描述          | 对应接口   | 接口 URL                               |
| ---- | ------------- | ------ | ------------------------------------ |
| 学习概览 | 总学习时长、课程数、平均分 | 获取学习概览 | GET /api/student/progress/overview   |
| 学习日历 | 每日学习时长日历展示    | 获取学习日历 | GET /api/student/progress/calendar   |
| 学习统计 | 学习趋势、知识点掌握情况  | 获取学习统计 | GET /api/student/progress/statistics |

### 4.10 管理员端 - 用户管理功能 - 接口对应（7 个接口）

| 功能点  | 功能描述               | 对应接口   | 接口 URL                                       |
| ---- | ------------------ | ------ | -------------------------------------------- |
| 用户列表 | 按角色 / 状态 / 关键词筛选用户 | 获取用户列表 | GET /api/admin/users                         |
| 用户详情 | 查看用户详细信息           | 获取用户详情 | GET /api/admin/users/{userId}                |
| 创建用户 | 后台创建用户账号           | 创建用户   | POST /api/admin/users                        |
| 更新用户 | 修改用户信息与状态          | 更新用户   | PUT /api/admin/users/{userId}                |
| 删除用户 | 删除用户账号             | 删除用户   | DELETE /api/admin/users/{userId}             |
| 重置密码 | 重置用户登录密码           | 重置用户密码 | PUT /api/admin/users/{userId}/reset-password |
| 批量导入 | Excel 批量导入用户       | 批量导入用户 | POST /api/admin/users/import                 |

### 4.11 管理员端 - 系统配置功能 - 接口对应（4 个接口）

| 功能点  | 功能描述        | 对应接口     | 接口 URL                             |
| ---- | ----------- | -------- | ---------------------------------- |
| 系统配置 | 获取所有系统配置项   | 获取系统配置   | GET /api/admin/configs             |
| 更新配置 | 修改系统配置参数    | 更新系统配置   | PUT /api/admin/configs/{configKey} |
| 系统日志 | 查看操作日志与错误日志 | 获取系统日志   | GET /api/admin/logs                |
| 健康检查 | 系统与各服务健康状态  | 获取系统健康状态 | GET /api/admin/health              |

### 4.12 管理员端 - 数据统计功能 - 接口对应（6 个接口）

| 功能点     | 功能描述         | 对应接口         | 接口 URL                                |
| ------- | ------------ | ------------ | ------------------------------------- |
| 平台概览    | 用户、课程、作业总数统计 | 获取平台数据概览     | GET /api/admin/statistics/overview    |
| 用户增长    | 用户注册增长趋势     | 获取用户增长趋势     | GET /api/admin/statistics/user-growth |
| 课程统计    | 课程学习数据统计     | 获取课程统计       | GET /api/admin/statistics/courses     |
| AI 使用统计 | AI 功能调用次数与费用 | 获取 AI 功能使用统计 | GET /api/admin/statistics/ai-usage    |
| 导出报表    | 数据报表导出       | 导出数据报表       | GET /api/admin/statistics/export      |
| 数据备份    | 数据库全量 / 增量备份 | 数据备份         | POST /api/admin/backup                |

### 4.13 AI 功能模块 - 接口对应（9 个接口）

| 功能点   | 功能描述         | 对应接口     | 接口 URL                                |
| ----- | ------------ | -------- | ------------------------------------- |
| 智能答疑  | RAG 增强专业问题解答 | 智能答疑     | POST /api/ai/qa/ask                   |
| 继续对话  | 多轮上下文连续对话    | 继续对话     | POST /api/ai/qa/continue              |
| 会话历史  | 历史问答记录       | 获取会话历史   | GET /api/ai/qa/sessions               |
| 作文批改  | AI 自动批改作文    | 作文批改     | POST /api/ai/essay/correct            |
| 作文润色  | AI 优化作文表达    | 作文润色     | POST /api/ai/essay/polish             |
| 学习推荐  | 个性化学习内容推荐    | 个性化学习推荐  | GET /api/ai/recommendations/learning  |
| 题目推荐  | 智能推荐作业题目     | 作业题目推荐   | GET /api/ai/recommendations/questions |
| 薄弱项分析 | 知识点薄弱项诊断     | 知识点薄弱项分析 | GET /api/ai/analysis/weak-points      |
| 路径规划  | AI 生成学习路径    | 学习路径规划   | POST /api/ai/learning-path/generate   |

### 4.14 知识图谱模块 - 接口对应（12 个接口）

| 功能点   | 功能描述       | 对应接口     | 接口 URL                                            |
| ----- | ---------- | -------- | ------------------------------------------------- |
| 知识点详情 | 知识点完整信息    | 获取知识点详情  | GET /api/knowledge/points/{pointId}               |
| 知识点列表 | 知识点树形结构    | 获取知识点列表  | GET /api/knowledge/points                         |
| 知识点关联 | 关联知识点图谱数据  | 获取知识点关联  | GET /api/knowledge/points/{pointId}/relations     |
| 前置知识点 | 学习依赖路径     | 获取前置知识点  | GET /api/knowledge/points/{pointId}/prerequisites |
| 后继知识点 | 后续学习建议     | 获取后继知识点  | GET /api/knowledge/points/{pointId}/successors    |
| 学习路径  | 起点到目标的最优路径 | 学习路径规划   | POST /api/knowledge/path/plan                     |
| 知识点搜索 | 全文搜索知识点    | 知识点搜索    | GET /api/knowledge/search                         |
| 图谱概览  | 知识图谱统计信息   | 获取知识图谱概览 | GET /api/knowledge/overview                       |
| 创建知识点 | 新增知识点节点    | 创建知识点    | POST /api/knowledge/points                        |
| 创建关系  | 新增知识点关联    | 创建知识点关系  | POST /api/knowledge/relations                     |
| 更新知识点 | 修改知识点信息    | 更新知识点    | PUT /api/knowledge/points/{pointId}               |
| 删除知识点 | 删除知识点节点    | 删除知识点    | DELETE /api/knowledge/points/{pointId}            |

***

## 5. 完整前后端接口清单

### 5.1 统一响应格式说明

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": { /* 具体响应数据 */ },
  "timestamp": 1620000000000
}
```

#### 错误响应

```json
{
  "code": 400/401/403/404/500,
  "message": "错误描述",
  "data": null,
  "timestamp": 1620000000000
}
```

#### 分页响应统一结构

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "list": [],
    "page": 1,
    "pageSize": 10,
    "totalPages": 10
  },
  "timestamp": 1620000000000
}
```

### 5.2 公共基础模块（9 个接口）

| 接口名称     | 请求方式 | 接口 URL                   | 请求参数                                                      | 响应参数                                   | 权限要求 |
| -------- | ---- | ------------------------ | --------------------------------------------------------- | -------------------------------------- | ---- |
| 用户登录     | POST | /api/auth/login          | 请求体：username, password                                    | token, userInfo, roles                 | 匿名   |
| 用户注册     | POST | /api/auth/register       | 请求体：username, password, email, role, realName, verifyCode | userId, message                        | 匿名   |
| 刷新令牌     | POST | /api/auth/refresh        | 请求体：refreshToken                                          | token, refreshToken                    | 已登录  |
| 获取当前用户信息 | GET  | /api/auth/me             | 无                                                         | userId, username, email, roles, avatar | 已登录  |
| 修改密码     | PUT  | /api/auth/password       | 请求体：oldPassword, newPassword                              | success, message                       | 已登录  |
| 文件上传     | POST | /api/common/upload       | 请求体：file, type                                            | fileId, fileUrl, fileName, fileSize    | 已登录  |
| 批量文件上传   | POST | /api/common/upload/batch | 请求体：files                                                 | files                                  | 已登录  |
| 发送验证码    | POST | /api/common/verify-code  | 请求体：email/phone, type                                     | success, expireTime                    | 匿名   |
| 重置密码     | POST | /api/auth/reset-password | 请求体：email/phone, verifyCode, newPassword                  | success, message                       | 匿名   |

### 5.3 教师端模块（28 个接口）

#### 课程管理（8 个）

| 接口名称   | 请求方式   | 接口 URL                                               | 权限要求 |
| ------ | ------ | ---------------------------------------------------- | ---- |
| 创建课程   | POST   | /api/teacher/courses                                 | 教师   |
| 获取课程列表 | GET    | /api/teacher/courses                                 | 教师   |
| 获取课程详情 | GET    | /api/teacher/courses/{courseId}                      | 教师   |
| 更新课程   | PUT    | /api/teacher/courses/{courseId}                      | 教师   |
| 删除课程   | DELETE | /api/teacher/courses/{courseId}                      | 教师   |
| 添加课程章节 | POST   | /api/teacher/courses/{courseId}/chapters             | 教师   |
| 更新章节   | PUT    | /api/teacher/courses/{courseId}/chapters/{chapterId} | 教师   |
| 删除章节   | DELETE | /api/teacher/courses/{courseId}/chapters/{chapterId} | 教师   |

#### 作业管理（8 个）

| 接口名称     | 请求方式   | 接口 URL                                                             | 权限要求 |
| -------- | ------ | ------------------------------------------------------------------ | ---- |
| 创建作业     | POST   | /api/teacher/assignments                                           | 教师   |
| 获取作业列表   | GET    | /api/teacher/assignments                                           | 教师   |
| 获取作业详情   | GET    | /api/teacher/assignments/{assignmentId}                            | 教师   |
| 更新作业     | PUT    | /api/teacher/assignments/{assignmentId}                            | 教师   |
| 删除作业     | DELETE | /api/teacher/assignments/{assignmentId}                            | 教师   |
| 获取作业提交列表 | GET    | /api/teacher/assignments/{assignmentId}/submissions                | 教师   |
| 批改作业     | PUT    | /api/teacher/assignments/{assignmentId}/submissions/{submissionId} | 教师   |
| 发布作业     | PUT    | /api/teacher/assignments/{assignmentId}/publish                    | 教师   |

#### 题库管理（7 个）

| 接口名称   | 请求方式   | 接口 URL                              | 权限要求 |
| ------ | ------ | ----------------------------------- | ---- |
| 创建题目   | POST   | /api/teacher/questions              | 教师   |
| 获取题目列表 | GET    | /api/teacher/questions              | 教师   |
| 获取题目详情 | GET    | /api/teacher/questions/{questionId} | 教师   |
| 更新题目   | PUT    | /api/teacher/questions/{questionId} | 教师   |
| 删除题目   | DELETE | /api/teacher/questions/{questionId} | 教师   |
| 批量导入题目 | POST   | /api/teacher/questions/import       | 教师   |
| 批量导出题目 | GET    | /api/teacher/questions/export       | 教师   |

#### 学情分析（5 个）

| 接口名称      | 请求方式 | 接口 URL                                            | 权限要求 |
| --------- | ---- | ------------------------------------------------- | ---- |
| 获取班级学情概览  | GET  | /api/teacher/analytics/class/{courseId}           | 教师   |
| 获取学生学习详情  | GET  | /api/teacher/analytics/students/{studentId}       | 教师   |
| 获取作业统计    | GET  | /api/teacher/analytics/assignments/{assignmentId} | 教师   |
| 获取知识点掌握情况 | GET  | /api/teacher/analytics/knowledge-points           | 教师   |
| 导出学情报告    | GET  | /api/teacher/analytics/report/export              | 教师   |

### 5.4 学生端模块（18 个接口）

#### 学习中心（5 个）

| 接口名称     | 请求方式   | 接口 URL                                   | 权限要求 |
| -------- | ------ | ---------------------------------------- | ---- |
| 获取我的课程列表 | GET    | /api/student/courses                     | 学生   |
| 获取课程学习详情 | GET    | /api/student/courses/{courseId}          | 学生   |
| 更新学习进度   | POST   | /api/student/courses/{courseId}/progress | 学生   |
| 加入课程     | POST   | /api/student/courses/{courseId}/enroll   | 学生   |
| 退出课程     | DELETE | /api/student/courses/{courseId}/enroll   | 学生   |

#### 作业管理（5 个）

| 接口名称     | 请求方式 | 接口 URL                                         | 权限要求 |
| -------- | ---- | ---------------------------------------------- | ---- |
| 获取待完成作业  | GET  | /api/student/assignments/pending               | 学生   |
| 获取已完成作业  | GET  | /api/student/assignments/completed             | 学生   |
| 获取作业详情   | GET  | /api/student/assignments/{assignmentId}        | 学生   |
| 提交作业     | POST | /api/student/assignments/{assignmentId}/submit | 学生   |
| 获取作业批改结果 | GET  | /api/student/assignments/{assignmentId}/result | 学生   |

#### 错题本（5 个）

| 接口名称   | 请求方式   | 接口 URL                                     | 权限要求 |
| ------ | ------ | ------------------------------------------ | ---- |
| 获取错题列表 | GET    | /api/student/wrong-questions               | 学生   |
| 添加到错题本 | POST   | /api/student/wrong-questions               | 学生   |
| 标记已掌握  | PUT    | /api/student/wrong-questions/{id}/mastered | 学生   |
| 移除错题   | DELETE | /api/student/wrong-questions/{id}          | 学生   |
| 生成错题练习 | POST   | /api/student/wrong-questions/practice      | 学生   |

#### 学习进度（3 个）

| 接口名称   | 请求方式 | 接口 URL                           | 权限要求 |
| ------ | ---- | -------------------------------- | ---- |
| 获取学习概览 | GET  | /api/student/progress/overview   | 学生   |
| 获取学习日历 | GET  | /api/student/progress/calendar   | 学生   |
| 获取学习统计 | GET  | /api/student/progress/statistics | 学生   |

### 5.5 管理员端模块（17 个接口）

#### 用户管理（7 个）

| 接口名称   | 请求方式   | 接口 URL                                   | 权限要求 |
| ------ | ------ | ---------------------------------------- | ---- |
| 获取用户列表 | GET    | /api/admin/users                         | 管理员  |
| 获取用户详情 | GET    | /api/admin/users/{userId}                | 管理员  |
| 创建用户   | POST   | /api/admin/users                         | 管理员  |
| 更新用户   | PUT    | /api/admin/users/{userId}                | 管理员  |
| 删除用户   | DELETE | /api/admin/users/{userId}                | 管理员  |
| 重置用户密码 | PUT    | /api/admin/users/{userId}/reset-password | 管理员  |
| 批量导入用户 | POST   | /api/admin/users/import                  | 管理员  |

#### 系统配置（4 个）

| 接口名称     | 请求方式 | 接口 URL                         | 权限要求 |
| -------- | ---- | ------------------------------ | ---- |
| 获取系统配置   | GET  | /api/admin/configs             | 管理员  |
| 更新系统配置   | PUT  | /api/admin/configs/{configKey} | 管理员  |
| 获取系统日志   | GET  | /api/admin/logs                | 管理员  |
| 获取系统健康状态 | GET  | /api/admin/health              | 管理员  |

#### 数据统计（6 个）

| 接口名称         | 请求方式 | 接口 URL                            | 权限要求 |
| ------------ | ---- | --------------------------------- | ---- |
| 获取平台数据概览     | GET  | /api/admin/statistics/overview    | 管理员  |
| 获取用户增长趋势     | GET  | /api/admin/statistics/user-growth | 管理员  |
| 获取课程统计       | GET  | /api/admin/statistics/courses     | 管理员  |
| 获取 AI 功能使用统计 | GET  | /api/admin/statistics/ai-usage    | 管理员  |
| 导出数据报表       | GET  | /api/admin/statistics/export      | 管理员  |
| 数据备份         | POST | /api/admin/backup                 | 管理员  |

### 5.6 AI 功能模块（9 个接口）

| 接口名称     | 请求方式 | 接口 URL                            | 权限要求    |
| -------- | ---- | --------------------------------- | ------- |
| 智能答疑     | POST | /api/ai/qa/ask                    | 已登录     |
| 继续对话     | POST | /api/ai/qa/continue               | 已登录     |
| 获取会话历史   | GET  | /api/ai/qa/sessions               | 已登录     |
| 作文批改     | POST | /api/ai/essay/correct             | 已登录     |
| 作文润色     | POST | /api/ai/essay/polish              | 已登录     |
| 个性化学习推荐  | GET  | /api/ai/recommendations/learning  | 学生      |
| 作业题目推荐   | GET  | /api/ai/recommendations/questions | 教师      |
| 知识点薄弱项分析 | GET  | /api/ai/analysis/weak-points      | 学生 / 教师 |
| 学习路径规划   | POST | /api/ai/learning-path/generate    | 学生      |

### 5.7 知识图谱模块（12 个接口）

| 接口名称     | 请求方式   | 接口 URL                                        | 权限要求     |
| -------- | ------ | --------------------------------------------- | -------- |
| 获取知识点详情  | GET    | /api/knowledge/points/{pointId}               | 已登录      |
| 获取知识点列表  | GET    | /api/knowledge/points                         | 已登录      |
| 获取知识点关联  | GET    | /api/knowledge/points/{pointId}/relations     | 已登录      |
| 获取前置知识点  | GET    | /api/knowledge/points/{pointId}/prerequisites | 已登录      |
| 获取后继知识点  | GET    | /api/knowledge/points/{pointId}/successors    | 已登录      |
| 学习路径规划   | POST   | /api/knowledge/path/plan                      | 已登录      |
| 知识点搜索    | GET    | /api/knowledge/search                         | 已登录      |
| 获取知识图谱概览 | GET    | /api/knowledge/overview                       | 已登录      |
| 创建知识点    | POST   | /api/knowledge/points                         | 教师 / 管理员 |
| 创建知识点关系  | POST   | /api/knowledge/relations                      | 教师 / 管理员 |
| 更新知识点    | PUT    | /api/knowledge/points/{pointId}               | 教师 / 管理员 |
| 删除知识点    | DELETE | /api/knowledge/points/{pointId}               | 教师 / 管理员 |

***

## 6. 核心数据表设计

### 6.1 用户与权限

| 表名                   | 核心字段                                                                                                                                                       |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **user**             | id, username, password, name, role, status, phone, email, phone\_verified, email\_verified, avatar, last\_login\_time, login\_ip, created\_at, updated\_at |
| **system\_config**   | id, config\_key, config\_value, config\_group, description, created\_at, updated\_at                                                                       |
| **role\_permission** | id, role, permission\_key, permission\_name, created\_at                                                                                                   |
| **login\_log**       | id, user\_id, login\_time, ip, device, status, created\_at                                                                                                 |

### 6.2 课程与选课

| 表名                  | 核心字段                                                                                                                                                                |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **course**          | id, course\_code, course\_name, teacher\_id, status, start\_time, end\_time, enrollment\_type, description, cover\_image, course\_outline, created\_at, updated\_at |
| **course\_student** | id, course\_id, student\_id, join\_time, approval\_status, approval\_time, progress, created\_at                                                                    |
| **course\_chapter** | id, course\_id, parent\_id, chapter\_name, content, sort, created\_at, updated\_at                                                                                  |

### 6.3 知识图谱

| 表名                      | 核心字段                                                                                                    |
| ----------------------- | ------------------------------------------------------------------------------------------------------- |
| **knowledge\_point**    | id, name, course\_id, parent\_id, description, difficulty, extraction\_method, created\_at, updated\_at |
| **knowledge\_relation** | id, knowledge\_id, related\_id, relation\_type, confidence, created\_at                                 |

### 6.4 资源与作业

| 表名                      | 核心字段                                                                                                                                                                              |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **learning\_resource**  | id, resource\_name, resource\_type, category, course\_id, chapter\_id, knowledge\_ids, file\_path, video\_url, file\_size, version, upload\_time, comment\_count, favorite\_count |
| **course\_assignment**  | id, assignment\_name, course\_id, question\_ids, total\_score, publish\_time, deadline, target\_scope, created\_at                                                                |
| **student\_submission** | id, assignment\_id, student\_id, submit\_content, submit\_time, status, score, teacher\_comment, graded\_at, resubmit\_count, similarity                                          |

### 6.5 题库与考试

| 表名                  | 核心字段                                                                                                                                               |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| **question\_bank**  | id, question\_text, question\_type, options, correct\_answer, explanation, difficulty, score, course\_id, knowledge\_ids, created\_by, created\_at |
| **mock\_exam**      | id, exam\_name, course\_id, student\_id, question\_ids, duration\_minutes, total\_score, created\_at                                               |
| **exam\_record**    | id, exam\_id, student\_id, answers, score, start\_time, end\_time, status, created\_at                                                             |
| **wrong\_question** | id, student\_id, question\_id, wrong\_answer, reason, mastered, created\_at, updated\_at                                                           |

### 6.6 AI 业务相关

| 表名                     | 核心字段                                                                                                                            |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **learning\_path**     | id, student\_id, course\_id, path\_name, stage, plan\_detail, daily\_tasks, created\_at, updated\_at                            |
| **qa\_record**         | id, student\_id, question, question\_type, answer, related\_knowledge, related\_resources, session\_id, asked\_at, answered\_at |
| **ai\_usage\_log**     | id, user\_id, function\_type, call\_time, cost, status, response\_time, created\_at                                             |
| **student\_analytics** | id, student\_id, course\_id, study\_time, completion\_rate, avg\_score, weak\_points, created\_at, updated\_at                  |

***

## 7. AI 功能技术实现方案

### 7.1 AI 服务架构

采用 DeepSeek 大语言模型 + RAG 检索增强 + Neo4j 图数据库 + Milvus 向量数据库四层架构：

- **大模型层**：提供自然语言理解、内容生成、逻辑推理核心能力
- **RAG 层**：检索私有知识库、教学资源、历史问答数据，解决大模型幻觉问题
- **图数据库层**：存储知识图谱，支撑知识点关联查询、薄弱点溯源、学习路径推演
- **向量数据库层**：实现教学资源向量化存储与快速语义检索

### 7.2 核心 AI 功能实现

1. **AI 智能出题**：后端构造结构化 Prompt，传入知识点、题型、难度等参数，调用 DeepSeek 生成 JSON 格式试题，解析后入库，支持教师手动修正。
2. **知识点关系抽取**：对上传的课程大纲 / 课件进行文本分割与向量化，调用大模型抽取知识点及关系，计算置信度，置信度≥80% 自动入库，低于阈值提示教师审核。
3. **双视角学情分析**：聚合学生作业、考试数据，统计各知识点掌握率，通过 Prompt 传入结构化数据，AI 生成班级 / 个人分析报告与优化建议，前端通过 ECharts 可视化展示。
4. **个性化学习路径规划**：结合知识图谱的知识点依赖关系与学生学情数据，使用拓扑排序算法确定学习顺序，AI 生成每日任务与阶段目标，每完成一个阶段自动更新路径。
5. **智能问答**：学生提问后，先检索 Neo4j 知识图谱与 Milvus 向量数据库中的关联数据，拼接上下文 Prompt 调用大模型，生成精准答疑内容，支持代码块高亮与公式渲染。
6. **作业查重**：对学生提交的作业内容进行向量化，与已有作业库进行相似度计算，超过阈值提示重复，支持教师查看重复内容。

***

## 8. 系统主要业务流程

### 8.1 教师端核心业务流程

1. **账号准备**：教师完成注册→管理员审核通过（可选）→登录系统
2. **课程搭建**：创建课程→填写课程基本信息→配置选课模式→上传课程大纲
3. **知识体系构建**：创建知识点→配置知识点关系→生成课程知识图谱→AI 辅助完善关系
4. **教学资源准备**：按章节上传课件、视频等教学资源→关联对应知识点
5. **教学实施**：AI 智能出题→发布作业→查看学生提交情况→批改作业→生成班级学情报告
6. **教学优化**：根据 AI 学情分析报告与教学建议→调整教学内容与进度→更新教学资源

### 8.2 学生端核心业务流程

1. **账号准备**：学生完成注册→登录系统
2. **课程选择**：浏览课程列表→查看课程详情→加入课程（或提交选课申请）
3. **自主学习**：进入课程→按学习路径学习资源→完成章节练习
4. **作业与考试**：完成教师发布的作业→参加模拟考试→查看成绩与批改结果→错题自动收录
5. **学情诊断与提升**：查看个人 AI 学情报告→获取薄弱点分析→跟随 AI 推荐学习路径学习
6. **疑问解决**：遇到学习问题→使用智能问答功能→查看答疑记录→巩固知识点

### 8.3 管理员端核心业务流程

1. **系统初始化**：使用初始化账号登录→配置系统基础信息→设置全局参数
2. **用户管理**：审核教师注册申请→管理学生账号→分配用户角色与权限
3. **系统运维**：维护基础字典数据→监控系统运行状态→查看系统日志
4. **AI 服务管理**：配置 AI API 密钥→监控 AI 服务调用情况→处理 AI 服务异常
5. **内容审核**：审核违规课程与资源→处理用户举报→保障系统内容合规
6. **数据管理**：定期备份数据库→处理数据异常→保障数据安全

***

## 9. 非功能性需求

### 9.1 性能要求

- 页面平均响应时间 ≤ 2 秒，静态资源加载时间 ≤ 1 秒
- AI 接口调用超时 ≤ 15 秒，支持异步处理，长耗时任务后台执行
- 系统支持并发用户数 ≥ 200，单接口 QPS ≥ 50
- 大文件上传速度 ≥ 1MB/s，支持同时上传 3 个文件
- 知识图谱渲染节点数 ≤ 1000 时，加载时间 ≤ 3 秒

### 9.2 安全性要求

- 用户密码采用 BCrypt 加密存储，不可解密
- 防护 SQL 注入、XSS、CSRF、文件上传漏洞等常见网络攻击
- 文件上传设置格式白名单与 100MB 大小限制，禁止上传可执行文件
- AI API 密钥加密存储在配置文件中，禁止明文传输
- 所有接口进行权限校验，敏感操作记录操作日志
- 支持 HTTPS 协议，数据传输加密
- 敏感数据脱敏展示，如手机号、邮箱中间位隐藏

### 9.3 可用性要求

- 系统全年可用性 ≥ 99%，计划内停机提前 24 小时通知
- 提供标准化错误提示与操作引导，关键操作有二次确认
- 支持主流浏览器（Chrome、Edge、Firefox）最新两个版本
- 移动端适配：支持手机、平板等设备访问，保证核心功能可用
- 系统异常时提供友好提示，支持用户反馈问题
- 支持离线缓存学习资源，弱网环境下可查看已缓存内容

### 9.4 可维护性要求

- 采用前后端分离架构，统一 RESTful API 接口规范
- 配套 Swagger/Knife4j 接口文档，接口注释覆盖率 ≥ 90%
- 系统关键操作、异常日志全程记录，日志级别可配置
- 代码模块化设计，低耦合高内聚，便于后续功能迭代
- 支持灰度发布，新功能可逐步上线
- 提供完整的部署文档与运维手册

### 9.5 质量要求

- 专业问答准确率 ≥ 90%
- 知识幻觉发生率 ≤ 5%
- 接口文档覆盖率 ≥ 95%
- 单元测试覆盖率 ≥ 80%
- 关键功能 BUG 率 = 0

***

## 10. 预期项目成果

按照项目申报书要求，项目预期产出：

1. **AI 助教系统一套**：基于知识图谱 + RAG 的《计算机网络》智能教学辅助系统
2. **项目研究报告一份**：完整的技术研究与实施报告
3. **《计算机网络》专业知识图谱**：覆盖全部 7 大章节、200 + 核心知识点
4. **可推广技术方案**：可复用至其他专业课程的标准化实施方案

***

**文档统计**

- 总接口数：128 个
- 功能点：68 个
- 数据表：18 张
- 功能 - 接口对应覆盖率：100%

