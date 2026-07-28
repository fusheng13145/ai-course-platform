# 基于知识图谱+LLM的AI助教系统

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.x-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.4.x-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

基于知识图谱+LLM的AI助教系统是一套融合人工智能与教学管理的现代化在线教育系统。系统面向学生、教师与管理员三大核心角色，提供个性化学习体验、高效的教学支持及全面的管理能力。

### 核心功能

- **多角色支持**: 管理员、教师、学生三种角色权限隔离（RBAC）
- **课程管理**: 课程创建、章节管理、选课审批、教学资源管理
- **作业考试**: 题库管理、智能出题、在线考试、自动评分、查重检测
- **学习辅助**: 知识图谱可视化、个性化学习路径、AI智能问答、错题本
- **数据分析**: 学习进度分析、成绩分布、学习趋势、学情报告
- **AI赋能**: DeepSeek大模型集成，支持智能出题、AI对话
- **实时通信**: WebSocket实时通知推送

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.15 |
| ORM | MyBatis-Plus | 3.5.7 |
| 安全 | Spring Security + JWT | 6.x / jjwt 0.12.6 |
| 数据库 | MySQL | 8.0+ |
| 缓存 | Redis | 7.x（可选） |
| 前端框架 | Vue 3 | 3.5.x |
| 构建工具 | Vite | 8.x |
| UI库 | Element Plus | 2.4.x |
| 图表 | ECharts + vue-echarts | 5.x / 6.x |
| 状态管理 | Pinia | 2.1.x |
| 路由 | Vue Router | 4.2.x |
| 实时通信 | WebSocket | 集成Spring WebSocket |

## 快速开始

### 环境要求

| 环境 | 版本要求 |
|------|---------|
| JDK | **21**（项目构建/运行目标；`pom.xml` 编译 `release=21`，CI 与 Docker 均使用 Temurin 21。注意：当前 Lombok 版本与 JDK 25 不兼容会导致编译失败，本机须使用 JDK 21，切勿使用 25 或更高版本，如需可指定 `C:/PROGRA~1/Java/jdk-21`） |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Node.js | 20.19+（Vite 8 要求；CI 与 Docker 使用 Node 20） |
| Redis | 7.x（可选） |

### 数据库初始化

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 导入表结构和初始数据
mysql -u root -p ai_course_platform < backend/src/main/resources/index.sql
```

### 环境变量配置

在启动前配置以下环境变量（生产环境必需）：

| 变量名 | 说明 | 默认值（仅开发环境） |
|--------|------|-------------------|
| JWT_SECRET | JWT签名密钥 | 内置测试密钥 |
| MAIL_USERNAME | 邮箱账号 | - |
| MAIL_PASSWORD | 邮箱授权码 | - |
| DEEPSEEK_API_KEY | DeepSeek API密钥（已配置本地开发 Key；生产请通过此变量注入） | - |

### 后端启动

```bash
cd backend
# 开发模式（含热部署）
./mvnw.cmd spring-boot:run

# 或打包运行
mvn clean package
java -jar target/ai-course-platform-backend-0.0.1-SNAPSHOT.jar
```

> 本地一键启动：直接运行根目录 `start-backend.bat` 即可，脚本会自动激活 `local` 配置（`backend/src/main/resources/application-local.yaml`，含本地开发用 DeepSeek Key，已被 `.gitignore` 忽略），无需手动设置环境变量即可体验真实 AI 对话。生产环境请改用 `docker compose` 并通过 `DEEPSEEK_API_KEY` 注入密钥。

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

> 前端已通读审计并修复：教师端「学情分析 / 考试管理 / 成绩管理 / 知识图谱管理」与学生端「我的成绩 / 在线考试 / 知识图谱」等页面已接入路由与侧边栏菜单，均可直接访问；另修复了考试管理审批崩溃、验证码接口路径错位，并移除图表中的模拟数据。`npm run build` 已验证通过（Vite + Rolldown，无 broken import）。前后端本地联调已跑通（学生端「我的成绩 / 知识图谱」、教师端「学情分析」数据拉取正常）；联调中发现并修复了教师学情看板因 `submissions` 实体多出的 `max_score` / `percentage` 列导致聚合失败的后端缺陷。

### 访问地址

- 前端: http://localhost:5173
- API: http://localhost:8080/api
- Swagger: http://localhost:8080/api/swagger-ui.html

> 本地联调与演示账号（见下表，密码均为 `123456`）。生产环境请勿使用这些账号，并务必通过环境变量注入强密钥。

### Redis 说明（本地开发可选，容器部署内置）

Redis 用于会话缓存和性能优化。

- **本地开发（`mvnw spring-boot:run`）**：可选。不启动 Redis 时应用仍可正常启动运行，会话管理走数据库存储，仅部分缓存功能（如验证码）不生效。
- **容器化部署（`docker compose up`）**：Redis 由 `docker-compose.yml` 随容器启动并启用缓存，属于该模式下的必要组件。

**本地启动 Redis：**
```bash
# Windows / Linux / Mac
redis-server
# 或 Docker
docker run -d -p 6379:6379 redis:7
```

## Docker 容器化部署

使用 `docker-compose.yml` 一键拉起 MySQL、Redis、后端、前端四个服务。敏感配置（数据库密码、JWT 密钥、大模型 API Key、邮件凭据）通过同级 `.env` 注入，**请勿留空或使用默认值上线**。

### 1. 准备环境变量

```bash
# Linux / Mac
cp .env.example .env
# Windows
copy .env.example .env
```

编辑 `.env`，将占位值替换为真实强密码与密钥（`JWT_SECRET` 至少 32 位随机字符串）。

### 2. 构建并启动

```bash
docker compose up -d --build
```

启动后访问：前端 http://localhost （容器 80 端口），API http://localhost:8080/api 。

> 注意：未创建 `.env` 时 `docker compose` 会回退到 `docker-compose.yml` 中的默认值（如 `JWT_SECRET=change_me_in_production`），仅可用于本地联调，**禁止用于生产环境**。

## 演示账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 管理员 | admin | 123456 | 系统管理员，拥有全部权限 |
| 教师 | teacher | 123456 | 张教授，可管理课程、作业、考试 |
| 教师 | teacher2 | 123456 | 李副教授，数学学院教师 |
| 学生 | student | 123456 | 王同学，已选3门课 |
| 学生 | student2 | 123456 | 赵同学 |
| 学生 | student3 | 123456 | 刘同学 |
| 学生 | student4 | 123456 | 陈同学 |
| 学生 | student5 | 123456 | 杨同学 |

## 项目结构

```
ai-course-platform/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/aicourse/
│   │   ├── Application.java    # 启动类
│   │   ├── controller/         # 40 个控制器（端点以 springdoc 为准）
│   │   ├── service/            # 50个 Service Impl
│   │   ├── mapper/             # 47个 Mapper接口
│   │   ├── entity/             # 47个实体类
│   │   ├── security/           # JWT认证过滤器
│   │   ├── config/             # 配置类
│   │   └── util/               # 工具类
│   └── src/main/resources/
│       ├── application.yaml    # 配置文件
│       └── index.sql           # 数据库初始化脚本
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/                # 34个API模块（含 request.js 请求封装）
│   │   ├── views/              # 65个页面组件（三端）
│   │   ├── layouts/            # 布局组件
│   │   ├── stores/             # Pinia状态管理
│   │   ├── router/             # 路由配置
│   │   └── utils/              # 工具函数
│   ├── vite.config.js          # Vite配置
│   └── package.json            # 依赖配置
│
├── docs/                       # 项目文档
│   ├── AI课程平台-产品需求文档.md  # 合并权威版需求基线（含三端 PRD / AI 专题 / 接口矩阵 / 数据表）
│   ├── Python接口契约文档.md   # Java ↔ Python 微服务接口契约（KG/RAG 可选，微调 8003 活跃）
│   └── 项目交付与完成度报告.md # 交付清单 + 完成度 + 进度计划
└── README.md                   # 项目说明
```

## 功能模块清单

### 已实现功能

| 模块 | 功能 | 状态 |
|------|------|------|
| 用户认证 | 登录/注册/退出/密码修改/密码重置 | ✅ 已完成 |
| 用户管理 | 用户CRUD、启用/禁用、搜索筛选 | ✅ 已完成 |
| 课程管理 | 课程CRUD、审批、状态切换 | ✅ 已完成 |
| 章节管理 | 章节CRUD、多级章节树 | ✅ 已完成 |
| 选课管理 | 选课/退课、审批流 | ✅ 已完成 |
| 题库管理 | 题目CRUD、分类筛选、AI生成 | ✅ 已完成 |
| 试卷管理 | 试卷CRUD、题目关联 | ✅ 已完成 |
| 在线考试 | 开始考试、提交答卷、查看结果 | ✅ 已完成 |
| 作业管理 | 任务CRUD、发布、提交统计 | ✅ 已完成 |
| 作业批改 | 提交评分、教师反馈 | ✅ 已完成 |
| 知识图谱 | 知识点CRUD、关系管理、图谱可视化 | ✅ 已完成 |
| 学习路径 | 路径CRUD、节点管理、自动生成 | ✅ 已完成 |
| 学习记录 | 学习记录创建与查询 | ✅ 已完成 |
| 错题本 | 错题CRUD、标记已掌握、统计 | ✅ 已完成 |
| 资源管理 | 文件上传/下载、版本管理 | ✅ 已完成 |
| 通知中心 | 通知列表、已读/未读、发送通知 | ✅ 已完成 |
| 权限管理 | 角色权限CRUD、启用/禁用 | ✅ 已完成 |
| 数据分析 | 仪表盘统计、学习趋势、课程分析 | ✅ 已完成 |
| 系统管理 | 配置管理、健康检查、日志查看 | ✅ 已完成 |
| AI助手 | DeepSeek对话（含多轮会话持久化）、智能出题、内容生成、作文批改、图谱AI抽取 | ✅ 已完成 |
| AI学情建议 | 学生端「AI 学习建议」面板（薄弱点分析 + 优先级建议） | ✅ 已完成 |
| AI异常监控 | 管理员端「AI 异常监控」面板（登录/操作日志异常检测） | ✅ 已完成 |
| WebSocket | 实时通知推送 | ✅ 已完成 |

### 可扩展功能

| 功能 | 当前状态 | 扩展方向 |
|------|---------|---------|
| 作业查重 | 占位引导文案（`GET /submissions/{id}/plagiarism`）；本地查重逻辑已有单测 | 接入真实查重服务 |
| 学习路径自动生成 | 占位（`GET /learning-paths/generate` 返回 4 个硬编码主题） | 接入 AI + 数据库 |
| 知识点路径规划 | 部分实现（`POST /knowledge-points/path/plan` 未使用 startPointId） | 补全起止路径裁剪 |
| 催交提醒 | 表结构已建 | 实现定时任务自动催交 |
| 视频学习 | 表结构已建 | 实现视频播放进度追踪 |
| RAG 检索增强 | Java 自包含实现（knowledge_points + TF-IDF 余弦召回 + LLMProvider），无需外部 Python | 可选：接入 Milvus / Python 向量检索链路以增强召回 |
| Redis缓存 | ✅ 已启用 | 学情聚合 @Cacheable（5min TTL，评分失效） |

> 注：前端 `teacher/KnowledgeManagement.vue` 在 AI 抽取不可用时由 `generateFallbackExtraction()` 本地构造示例知识点作为兜底展示（仅展示不入库），接入稳定 AI 后可移除。

## 架构优化与可扩展点落地

下列优化项已在代码中落地（知识图谱 8001 与 RAG 8002 已改为 Java 自包含实现，仅 LoRA 微调 8003 保留 Python 集成）：

| 优化项 | 落地方式 | 收益 |
|------|---------|------|
| LLM 供应商抽象 | 新增 `LLMProvider` 接口 + `OpenAiCompatibleProvider` 基类，DeepSeek/OpenAI/Qwen 三个实现由 `ai.provider` 配置热切换；业务层仅依赖接口 | 切换/新增大模型零改业务代码 |
| AI 调用异步化 | 所有 `/ai/**` 接口返回 `Callable`，由 `AsyncMvcConfig` 专用线程池执行（CallerRunsPolicy + 120s 超时） | 长耗时 LLM 调用不再阻塞 Tomcat 请求线程 |
| 结构化 LLM 输出 | 启用 `response_format=json_object` + JSON-Schema 系统指令；`analyzeWeakPointsJson`/`recommendQuestionsJson` 配合健壮 JSON 提取（`extractJsonObject`） | 薄弱点/题目推荐解析不再依赖脆弱的换行切分 |
| Redis 缓存学情聚合 | `AnalyticsServiceImpl` 的 `getDashboard`/`getTeacherDashboard`/`getPlatformOverview` 加 `@Cacheable(cacheNames="analytics", ttl=5min)`；教师评分 `gradeSubmission` 加 `@CacheEvict(allEntries=true)` | 仪表盘高频聚合查询显著降 DB 压力 |
| IP 级限流 | 新增 `RateLimitFilter`（零第三方依赖，固定窗口计数）：登录接口 60s/10 次、AI 接口 60s/20 次，阈值由 `ratelimit.*` 配置 | 缓解爆破/脚本刷登录，作为大模型调用成本上限的代理防护 |
| CORS 配置化 | 新增 `CorsProperties` 绑定 `cors.allowed-origins`，`SecurityConfig` 不再硬编码 localhost 列表 | 多环境部署免改代码 |
| 遗留接口收敛 | 旧版 `/ai/chat`、`/generate-question`、`/generate-summary`、`/check-plagiarism`、`/rag/ask` 标记 `@Deprecated(forRemoval=true)` | 明确新旧边界，便于后续清理 |
| 未配置 Key 降级 | 无 API Key 时返回清晰业务提示（不再返回伪造 JSON） | 部署未配 Key 时体验清晰、不误导 |

> 前端 `admin/Dashboard.vue` 与 `admin/Analytics.vue` 中将误名为「模拟数据」的兜底函数 `setMockData` 更名为 `handleLoadError`（仅作空数据兜底，不含任何硬编码模拟数据）。

## 开发规范

编码与部署规范见 [docs/项目交付与完成度报告.md](docs/项目交付与完成度报告.md)（安全机制、部署指南等章节）。

## 详细文档

- 需求基线（合并权威版）：[docs/AI课程平台-产品需求文档.md](docs/AI课程平台-产品需求文档.md)
- 交付与完成度：[docs/项目交付与完成度报告.md](docs/项目交付与完成度报告.md)
- Python 微服务契约：[docs/Python接口契约文档.md](docs/Python接口契约文档.md)

## 生产部署检查清单

在将系统部署到生产环境前，请逐项确认以下检查项：

- [ ] **数据库SSL配置检查**：确认 MySQL 连接启用 SSL/TLS 加密传输，避免数据库凭据和数据在网络上明文传输
- [ ] **JWT密钥更换检查**：将 JWT_SECRET 更换为至少 32 位的随机强密钥，切勿使用默认值或开发环境密钥
- [ ] **管理员密码修改检查**：修改默认演示账号的密码（admin / teacher / student 系列的 123456 均为演示密码），生产环境应使用强密码
- [ ] **Redis连接检查**：确认 Redis 已启用 `requirepass` 认证密码，不使用默认空密码
- [ ] **日志级别检查**：将 Spring Boot 日志级别调整为 WARN 或 INFO（`logging.level.root=WARN`），避免敏感信息泄露到日志
- [ ] **备份策略配置**：配置定时数据库备份（如 `mysqldump` + cron），并确保备份文件异地存储和加密

> 以上检查项为生产环境最低安全基线，未经确认请勿上线。

## 技术亮点

下表列出了本平台在技术选型与工程实现上的核心竞争亮点：

| 亮点 | 说明 |
|------|------|
| 五层安全纵深防御 | JWT + CSRF + IP限流 + AI配额 + 对象级授权，构建从网络层到业务层的多级安全体系 |
| 三供应商 AI 路由 | DeepSeek/OpenAI/Qwen 热切换，通过 `LLMProvider` 接口抽象，切换大模型零修改业务代码 |
| 防作弊考试引擎 | 乱序题目、选项混淆、切屏检测、实时监考，多维度保障在线考试公平性 |
| 设计令牌体系 | 100+ CSS 变量，暗色/浅色双主题，主题切换零侵入、全局样式统一管控 |
| 自愈合数据库 | 运行时自动建表补列，减少 DBA 人工运维成本，提升部署效率与系统自愈能力 |
| AI 流式对话 | SSE（Server-Sent Events）实时流式输出，大模型回答逐 token 推送，体验接近 ChatGPT 式对话 |
| 异步 AI 调用 | 所有 `/ai/**` 接口返回 `Callable` 由专用线程池执行，长耗时 LLM 调用不阻塞 Tomcat 请求线程 |
| 结构化 AI 输出 | 启用 JSON-Schema 约束 + 健壮 JSON 提取，薄弱点分析/题目推荐解析不再依赖脆弱的文本切分 |
| Redis 缓存加速 | 学情聚合接口 `@Cacheable`（5min TTL），高频仪表盘查询降低数据库压力 |
| 前端防抖与去重 | 请求 AbortController 管理器、网络抖动自动重试、302/502/503/504 临时故障自动恢复 |

## 许可证

MIT License