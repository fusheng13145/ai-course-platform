# AI 课程平台 · 上线前检查报告（代码审查 + 安全审计 + QA 测试）

**日期**：2026-09-14
**场景**：上线前检查（代码审查 / 安全审计 / QA 测试与发布就绪）
**参与成员**：产品评审员（代码审查 + 转化评估）+ 安全官（OWASP + STRIDE）+ 质量门神（QA 测试与发布）
**协作**：软件工坊 GStack 团队（主理人沽思航）
**方法**：三位成员后台并行只读分析（后端 Spring Boot 3.5.15 / 前端 Vue3+TS），均以真实源码/配置/实测 E2E 为依据，未执行任何 git 写操作（规避中文父目录静默丢文件）。

---

## 📌 TL;DR（执行摘要）

- **整体结论：🟡 有条件通过（条件 Go）**——代码质量与安全基线高，3 项阻塞均为「功能/边界验证缺口」而非已确认代码缺陷。
- **阻塞项数量：3**（均为 QA P0，待补 E2E 验证）。
- **安全基线：高**。认证/JWT/ACL/防爆破/脱敏/依赖治理均达标；无 SQL/命令注入、无 SSRF；唯一中危为静态资源 ACL 绕过（F-01）。
- **代码质量：高**。2026-09-14 六个已修复缺陷真实闭环且有注释留痕；实体层 mapper 全 `#{}`、无空 catch、无 TODO/FIXME；中危为 ExamPaper 缓存陈旧（CR-2）与若干一致性加固项。
- **下一步**：补齐 3 项 P0 E2E + 修复 F-01 + 密钥治理后，可上线。

---

## 🎯 核心结论卡片

| 项目 | 内容 |
| --- | --- |
| Go / No-Go | 🟡 条件 Go |
| 严重度分布 | 🔴 4（3 项 QA 待验证阻塞 + 1 项多租户隔离缺口 F-18）/ 🟠 3 / 🟡 11 / 🟢 多项通过 |
| 关键行动项 | 9 条 |
| 建议负责人 | 本端工程 + 安全官 |

---

## 1. 各成员核心结论（每位 1 段）

### 🔍 产品评审员（代码审查 + 转化评估）
- **代码审查**：2026-09-14 六个已修复缺陷真实闭环且有注释留痕（Resource Lombok getter 歧义、ExamTimeoutScheduler 时间比较、判分 JSON 引号归一、选课校验、publishExamPaper 归属、授权错误码语义）。重点隐患为「缓存成对一致性」（#1/#2）与「归属校验双机制漂移」（#9）；状态魔法串硬编码、catch 吞异常、前端 23 个 `.js` 测试游离类型门禁为技术债。
- **转化评估**：Web 多租户 SaaS 为最优主形态（后端逻辑复用 80%+，flowcontrol 可扩为租户配额）；微信小程序为轻量 C 端入口；桌面软件仅内网一体机可选。已给出「多租户数据隔离 + 租户态 JWT + K8s 部署」架构与 M1–M4 路线（详见 `docs/产品化转化方案.md`）。

### 🛡️ 安全官（OWASP + STRIDE）
- 认证/JWT/ACL/防爆破/脱敏/依赖治理均达标，无 SQL/命令注入、无 SSRF。中危 1 项：静态资源 `/uploads/**` 配置 `permitAll`，可绕过 `ResourceController` 的可见性 ACL 直链下载；低危：`.env` 明文密钥 + `MYSQL_ROOT_PASSWORD=1234` 弱口令、XSS 取决于 `sanitizeHtml` 强度、CORS 含 `localhost`。**当前单租户无 🔴 高危漏洞**；产品化引入多租户时，F-18 跨租户隔离缺口升级为 🔴，列为改造安全验收门槛（见第 5 节与 `docs/产品化转化方案.md`）。

### ✅ 质量门神（QA 测试与发布）
- 基线：后端单测 2277 全绿；E2E **130 例全 PASS**（主链路 75 + 深度 49 + 安全 6）。缺口为 3 项 P0（并发选课超卖、考试超时自动收卷、断线续考未验证）与多项 P1（权限负面不全、依赖降级、超大文件分片）。兼容性：JDK21 / Redis7 ✓；浏览器现代常青基线（无 IE）；**MySQL compose 8.0 与手册声明 8.4 不符 ⚠**。回滚预案：Flyway 仅前向不可自动 down → 发布前必做 `mysqldump` 全量快照。

---

## 2. 综合审查发现（去重合并，按严重度排序）

| # | 严重度 | 类别 | 位置 | 问题描述 | 建议 | 来源 |
| --- | --- | --- | --- | --- | --- | --- |
| Q-1 | 🔴 | QA/功能 | 待验证 P0 | 并发选课容量竞争：仅测幂等，缺 N 学生抢满员超卖竞争验证 | 补并发 E2E（容量上限 + 竞争） | 质量门神 |
| Q-2 | 🔴 | QA/功能 | 待验证 P0 | 考试超时 + 到点自动收卷 + 过期续考未验证（仅测 max_attempts） | 补考试超时边界 E2E | 质量门神 |
| Q-3 | 🔴 | QA/功能 | 待验证 P0 | 网络中断 / WebSocket 监考断线重连与续考未验证 | 补前端断线续考 E2E | 质量门神 |
| F-18 | 🔴 | 安全/多租户 | JwtUtils + 各 @Cacheable Service | 现状单租户（全仓 `grep tenant` 零命中）；产品化多租户时缺租户边界 = 跨租户越权（F-17 缓存 key 无租户维度同此），任一遗漏=高危 | 列为多租户改造安全验收门槛：TenantLineInnerInterceptor + tenantId claim + 缓存租户隔离（F-17/F-18 合并为单一工作项） | 安全官 |
| CR-2 | 🟠 | 代码/缓存 | ExamPaperServiceImpl:96-117 | 编辑已发布试卷题目未触发 `updateById` → 详情读陈旧；publish 非事务 + 通知失败仅 `log.warn` 吞掉 | 题目变更纳入统一失效 + 单测固化；通知入重试队列 | 产品评审员 |
| F-01 | 🟠 | 安全/ACL | SecurityConfig:310 + ResourceController:261 | `/uploads/**` permitAll 可绕过控制器可见性 ACL 直链下载 | 取消免认证，或按 visibility 在静态层做 ACL | 安全官 |
| F-19 | 🟠 | 安全/小程序 | code2session + Bearer | 小程序 cookie→token 改造安全清单：session_key 绝不下发/入日志、自有 Bearer 短 TTL 绑 openid、微信 access_token 仅服务端持有 | 任一遗漏 = 中-高伪登录/越权，按清单逐条验收 | 安全官 |
| F-02 | 🟡 | 安全/密钥 | .env / docker-compose | JWT_SECRET / API Key / 口令明文；`MYSQL_ROOT_PASSWORD=1234` 弱 | 生产迁 Vault/Secret Manager；换强口令；确认未入 git 历史 | 安全官 |
| F-17(rev) | 🟡 | 安全/缓存 | 各 @Cacheable | 当前单租户安全；多租户下缓存 key 无租户维度，不改则升级 🔴（与 F-18 合并治理） | 多租户改造须 key 加租户前缀 + 按租户隔离命名空间 | 安全官 |
| F-09 | 🟡 | 安全/XSS | useAIAssistant.ts:304 | renderMarkdown 依赖 sanitizeHtml 强度（CSP 缓解） | 确认/升级至 DOMPurify 级；持续 CSP | 安全官 |
| F-14 | 🟡 | 安全/CORS | SecurityConfig:336 | allowed-origins 含 localhost:5173-5175 且 allowCredentials | 生产严格白名单，不带 localhost | 安全官 |
| F-08 | 🟡 | 安全/上传 | ResourceController:234 | 后缀取自原名未约束（下载 octet-stream 已缓解） | 约束允许后缀白名单 | 安全官 |
| CR-1 | 🟡 | 代码/缓存 | QuestionServiceImpl:53-295 | 题库缓存 `allEntries=true` 跨课程失效 | 按 courseId 前缀精确失效 | 产品评审员 |
| CR-3 | 🟡 | 代码/规范 | ExamPaperServiceImpl 17 处 | PUBLISHED/DRAFT/PENDING 魔法串硬编码 | 抽 ExamPaperStatus/CourseStatus 枚举（§19） | 产品评审员 |
| CR-4 | 🟡 | 代码/事务 | publishExamPaper:114-116 | 非事务 + 通知失败仅 log.warn 吞掉 | 入可观测 + 重试队列 | 产品评审员 |
| CR-6 | 🟡 | 代码/异常 | OperationLogAspect:99 等多处 | catch(Exception/Throwable) 不区分业务/系统，多被吞 | 业务透传、系统分级告警 | 产品评审员 |
| CR-7 | 🟡 | 前端/门禁 | 项目手册:1154 | 23 个 .js 测试文件不在 tsconfig include，游离门禁 | 分批转 .ts 并同步收紧基线 | 产品评审员 |
| CR-9 | 🟡 | 代码/ACL | service 层 80 处 | 归属校验双机制（内联 FORBIDDEN vs access-control 组件）可能漂移 | 统一收口到组件 | 产品评审员 |
| CR-12 | 🟢 | 代码/缓存 | QuestionServiceImpl:278 | search 缓存 key null 与 "" 生成不同 key | 入参归一 | 产品评审员 |
| Q-P1 | 🟡 | QA/边界 | 多模块 | 权限负面不全（Analytics/LearningPath/Proctor/角色提升）、依赖降级、超大文件分片未 E2E | 补 P1 E2E | 质量门神 |
| CMP-1 | 🟡 | 兼容 | docker-compose vs 手册 | MySQL compose 8.0 与声明 8.4 不符 | 统一版本并验 Flyway 迁移 | 质量门神 |
| ✅ 合规 | 🟢 | 多类 | — | BCrypt / SQL 注入无 / 命令注入无 / SSRF 无 / 响应脱敏 / CSRF 开 / 防爆破 / 依赖治理 / JWT 强 | 保持，详见成员产出 | 三成员 |

---

## ✅ 行动清单（具体可执行项）

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
| --- | --- | --- | --- | --- |
| 1 | 补 3 项 P0 E2E：并发选课竞争、考试超时自动收卷、断线续考 | 本端工程 | 🔴 P0 | 上线前 |
| 2 | 修复 F-01：`/uploads` 取消免认证或按 visibility 做静态 ACL | 安全官 + 本端工程 | 🟠 P1 | 上线前 |
| 3 | 密钥治理：.env 迁 Vault、换 `MYSQL_ROOT_PASSWORD` 强口令、确认未入 git 历史 | 安全官 | 🟠 P1 | 上线前 |
| 4 | 缓存一致性：ExamPaper 题目变更统一失效 + 单测；题库按 courseId 精确失效 | 本端工程 | 🟡 P1 | 迭代 1 |
| 5 | 规范：抽状态枚举、统一归属校验收口、catch 分级告警 | 本端工程 | 🟡 P1 | 迭代 1 |
| 6 | 生产就绪：统一 MySQL 版本、确认浏览器 build.target/移动适配、Alertmanager webhook 填真实地址 | 质量门神 | 🟡 P1 | 上线前 |
| 7 | 发布前 `mysqldump` 全量快照 + 保留上版 jar/dist 回滚预案演练 | 质量门神 | 🟡 P1 | 上线前 |
| 8 | 多租户隔离安全门槛（产品化 M1，最高优先级）：TenantLineInnerInterceptor 全局注入 + tenantId claim + 缓存租户隔离（F-17/F-18 合并） | 安全官 + 本端工程 | 🔴 P0（产品化） | M1 |
| 9 | 小程序 token 改造安全清单（产品化 M3）：session_key 不下发/不日志、Bearer 短 TTL 绑 openid、微信 access_token 仅服务端 | 安全官 + 本端工程 | 🟠 P1（产品化） | M3 |

---

## ⚠️ 待完善 / 已知局限

- 三成员均只读分析，未执行任何 git 写操作（规避中文父目录静默丢文件）。
- **待确认项**：`sanitizeHtml` 实现强度；`.env` 是否曾入库；`resolveClientIp` 是否严格用 `remoteAddr`；docker-compose 是否含消息队列（影响通知重试/异步判分落地方式）；多租户隔离采用行级 vs schema。
- 本报告建立在 `docs/上线前审查报告-2026-09-08.md`（部署安全/弱口令）与 `docs/结构与代码规范审查报告-2026-09-13.md`（结构/规范）之上，聚焦代码/安全/QA 三维增量；部署安全清单（手册 §29）仍须逐条落实。
- **交叉增补（安全官据 product-reviewer 转化评估输入）**：多租户隔离缺口 **F-18（🔴）** 与小程序 token 改造 **F-19（🟠）** 已列为产品化改造的高优先安全验收门槛，并同步纳入 `docs/产品化转化方案.md` 安全验收章节。

---

## 📚 成员产出索引

- **产品评审员**：代码审查 12 项发现 + 产品化转化评估（目标形态/核心模块/整体架构/路线）→ 已据其转化设计落盘 `docs/产品化转化方案.md`
- **安全官**：19 条 STRIDE + OWASP 发现（F-01 ~ F-19，含产品化交叉增补 F-18 多租户隔离 / F-19 小程序 token）
- **质量门神**：QA 覆盖缺口表 + 兼容性评估 + 上线检查清单与回滚预案

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
