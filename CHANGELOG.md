# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0] - 2026-07-27

### 安全加固
- 修复 mysqldump 备份密码在进程列表暴露风险
- 新增 restoreBackup 路径遍历防护
- 增强关键 API 输入参数校验

### 新增
- 数据库全文搜索索引（user search）
- 高频查询复合索引

### 测试
- 新增 Service 层测试（CourseService, ExamSession）
- 新增 API 契约测试
- 新增前端页面测试

### 文档
- 新增生产部署检查清单

## [0.5.0] - 2026-07-27

### 新增
- 统一数据库初始化脚本 `index.sql`，合并 7 个增量迁移文件
- 添加 `SET NAMES utf8mb4` 修复管道模式下 INSERT 中文编码问题
- AI 学习建议面板：学生端「AI 学习建议」面板（薄弱点分析 + 优先级建议）
- AI 异常监控：管理员端「AI 异常监控」面板（登录/操作日志异常检测）
- 学生端学习中心：章节任务点学习中心（含闯关解锁态）
- 视频任务点续播与心跳上报机制
- 测验任务点即时判分
- 学生成绩总览（按课程聚合作业/考试/总评）
- 课堂互动模块：签到发起/关闭/手动标记、学生签到、投票发起/关闭/投票
- 在线监考模块：监考会话管理、事件上报、风险汇总、事件复核工作流
- 过程性成绩模块：按学科定制的多维加权评分方案（考勤/作业/考试/实验/竞赛）
- 讨论区模块：话题发布/回复/点赞/置顶/精华/关闭
- 学习路径自动生成：基于课程知识库生成有序学习节点
- 自定义学习路径与每日任务清单
- 错题练习卷生成
- 资源版本管理：版本历史查询、新版本创建、版本回滚
- 资源可见性切换（教师/管理员）
- 功能路线图查询接口
- LLM 供应商抽象层：`LLMProvider` 接口 + `OpenAiCompatibleProvider` 基类，支持 DeepSeek/OpenAI/Qwen 热切换
- AI 调用异步化：所有 `/ai/**` 接口返回 `Callable`，由专用线程池执行
- 结构化 LLM 输出：启用 `response_format=json_object` + JSON-Schema 系统指令
- Redis 缓存学情聚合：`@Cacheable` 缓存仪表盘数据（5 分钟 TTL）
- IP 级限流：`RateLimitFilter`（固定窗口计数），登录 60s/10 次、AI 60s/20 次
- CORS 配置化：`CorsProperties` 绑定 `cors.allowed-origins`
- 密码重置：邮箱 + 验证码重置密码
- 个人资料修改：封面头像、个人简介
- 登录日志：用户登录历史、统计、异常检测、趋势分析
- 用户会话管理：在线用户、会话历史、强制下线、活跃统计
- JWT 令牌 HttpOnly Cookie 存储，消除 XSS 窃取面
- 作业提交通道统一（`GET /submissions` 、`POST /submissions`）
- 对象级访问控制 `SubmissionAccessControl` 杜绝 IDOR/BOLA

### 修复
- 修复教师学情看板因 `submissions` 实体多出的 `max_score` / `percentage` 列导致聚合失败的后端缺陷
- 修复考试管理审批页面崩溃问题
- 修复验证码接口路径错位
- 修复前端图表中误用的模拟数据，改用真实后端数据
- 修复 `/api/rag` 接口路径因 `context-path` 重复导致 404
- 修复 `courseChapterController` 权限校验逻辑反转（`canManageCourse` 返回值取反）
- 修复作业提交双轨问题（统一收口到 `SubmissionService`）
- 修复学生可任意修改课程进度的安全漏洞（改为后端基于任务点完成度聚合）
- 修复 JSON 解析脆弱性问题（换行切分改为 `extractJsonObject` 健壮提取）
- 修复未配置 API Key 时返回伪造 JSON 的问题，改为清晰业务提示
- 移除 `handleLoadError` 函数中误用的 `setMockData` 命名

### 重构
- 知识图谱控制器三合一（`KnowledgePointController` / `KnowledgeGraphController` / `KnowledgePointRelationController` 合并为 `KnowledgeController`）
- AI 控制器接口收敛（旧版 `/ai/chat`、`/generate-question`、`/rag/ask` 等标记 `@Deprecated(forRemoval=true)`）
- 前端 `admin/Dashboard.vue` 与 `admin/Analytics.vue` 中 `setMockData` 更名为 `handleLoadError`
- `UserUpdateDTO` 白名单机制，防止越权写入 role/password/id

### 文档
- 新增 API 文档：Swagger/OpenAPI 三端全覆盖（SpringDoc）
- 新增产品需求文档
- 新增 Python 接口契约文档
- 新增项目交付与完成度报告

### 依赖
- Spring Boot 3.4.4
- SpringDoc OpenAPI 2.8.17
- MyBatis-Plus 3.5.7
- JJWT 0.12.6
- Vue 3.5.x + Vite 8.x + Element Plus 2.4.x

## [0.4.0] - 2026-07-13

### 新增
- 在线考试模块：开始考试、自动保存、试卷提交、自动阅卷、成绩报告
- 题库管理：题目 CRUD、分类筛选
- 试卷管理：试卷 CRUD、题目关联
- 作业批改：教师评分、AI 智能评分
- 查重检测接口（占位）
- 学习记录模块：学习统计、趋势分析、月度日历
- 知识图谱 Neo4j 可视化：图谱全量查询、节点详情、关联关系
- 学习路径 CRUD 及路径规划
- 通知中心：通知列表、已读/未读、发送通知
- 数据看板：管理员全局统计、教师学情分析
- 文件上传：单文件/批量上传、文件类型白名单校验
- 课程审批流：提交审批、通过/驳回
- 批量文件上传下载
- 引入 MyBatis-Plus 分页查询
- 集成 Spring Security + JWT 认证
- 引入 Lombok 简化代码

### 修复
- 修复接口返回格式不统一的问题
- 修复分页参数类型不一致
- 修复缓存失效问题

### 重构
- 统一 API 响应格式（`ApiResponse`）
- 统一异常处理（`BusinessException` + 全局异常处理器）

## [0.3.0] - 2026-06-29

### 新增
- AI 智能对话：DeepSeek 大模型集成，支持多轮对话
- 智能出题：基于课程内容的 AI 自动出题
- 作文批改与润色
- 内容总结生成
- 知识点薄弱项分析
- 个性化学习推荐
- RAG 检索增强问答
- AI 抽取知识点与关系（从课程内容调用大模型自动构建知识图谱）
- 课程管理：课程 CRUD、状态管理
- 章节管理：多级章节树
- 选课管理：报名、审核、退课
- 用户管理：用户 CRUD、角色管理
- WebSocket 实时通知推送
- Docker 容器化部署（MySQL + Redis + 后端 + 前端）

### 修复
- 修复 Redis 连接异常问题
- 修复 Docker 构建时前端静态资源路径问题

## [0.2.0] - 2026-06-15

### 新增
- 用户注册与登录
- JWT 令牌认证
- 用户角色管理（管理员/教师/学生）
- Spring Boot 后端项目初始化
- Vue 3 前端项目初始化
- MySQL 数据库表结构设计
- 知识图谱基础框架搭建
- 基础 CRUD 接口

### 修复
- N/A

## [0.1.0] - 2026-06-01

### 新增
- 项目初始化
- 技术选型与架构设计
- 数据库 ER 设计
- 前端原型设计

[0.5.0]: https://github.com/example/ai-course-platform/releases/tag/v0.5.0
[0.4.0]: https://github.com/example/ai-course-platform/releases/tag/v0.4.0
[0.3.0]: https://github.com/example/ai-course-platform/releases/tag/v0.3.0
[0.2.0]: https://github.com/example/ai-course-platform/releases/tag/v0.2.0
[0.1.0]: https://github.com/example/ai-course-platform/releases/tag/v0.1.0
