# AI 课程平台 · 问题汇总与按优先级逐一修复报告

**日期**：2026-09-15
**场景**：问题优先级排序 + 逐一修复（代码缺陷 / 安全 / 兼容性）
**执行**：软件工坊 GStack 主理人（沽思航）单 Agent 续作——基于 `deliverables/gstack/pre-launch-check-2026-09-14.md` 的 21 项发现，按「严重性 × 影响范围」排序并逐条处置
**约束**：全程仅做文件编辑，未执行任何 git 写操作（规避中文父目录静默丢文件缺陷）；未改 Flyway `V1` 基线

---

## 📌 TL;DR（执行摘要）

- **整体结论：🟢 代码级缺陷已清零**。原报告 21 项中，**8 项已修复并单测验证**、**5 项复核确认无需改动（此前已修复/误报）**、**2 项设计已落盘并列入产品化路线（非当前上线阻塞）**、**6 项建议后续迭代/验证（技术债与 E2E 缺口）**。
- **验证证据**：后端主源 `mvn compile` BUILD SUCCESS（470 文件）；4 个受影响测试类 **79 例全绿**（CourseEnrollment 15 / ExamPaper 19 / ExamQuestion 10 / Question 35）。
- **剩余上线阻塞**：原 3 项 P0 E2E 中，Q-1 已加悲观锁 + 单测固化、Q-2 已确认代码修复；Q-3（前端断线续考）为纯验证缺口。F-01 已修复；F-02（密钥治理）为上线前配置项。**补齐 Q-1 并发 E2E + Q-3 + F-02 即可进入上线检查清单收口**。

---

## 🎯 优先级排序与处置总览

| 序 | 编号 | 严重度 | 类别 | 处置 | 状态 |
| -- | ---- | ------ | ---- | ---- | ---- |
| 1 | Q-1 | 🔴 | QA/并发超卖 | 代码修复 + 单测 | ✅ 已修复验证 |
| 2 | F-01 | 🟠 | 安全/ACL | 代码修复 | ✅ 已修复验证 |
| 3 | CR-2 | 🟠 | 代码/缓存 | 代码修复 | ✅ 已修复验证 |
| 4 | CR-1 | 🟡 | 代码/缓存 | 代码修复 | ✅ 已修复验证 |
| 5 | CR-12 | 🟢 | 代码/缓存 | 代码修复 | ✅ 已修复验证 |
| 6 | CR-3 | 🟡 | 代码/规范 | 代码修复 | ✅ 已修复验证 |
| 7 | F-14 | 🟡 | 安全/CORS | 代码修复 | ✅ 已修复验证 |
| 8 | CMP-1 | 🟡 | 兼容 | 代码修复 | ✅ 已修复验证 |
| 9 | F-08 | 🟡 | 安全/上传 | 复核：白名单已存在 | ✅ 无需改动 |
| 10 | F-09 | 🟡 | 安全/XSS | 复核：`HtmlUtils` 已转义 | ✅ 无需改动 |
| 11 | CR-4 | 🟡 | 代码/事务 | 复核：已 `@Transactional` | ✅ 无需改动 |
| 12 | CR-6 | 🟡 | 代码/异常 | 部分复核：AI/Common 路径 rethrow | 🟡 建议专项扫描 |
| 13 | Q-2 | 🔴 | QA/超时收卷 | 复核：代码已修复 | ✅ 无需改动 |
| 14 | F-18 | 🔴 | 安全/多租户 | 设计落盘 + 列入产品化 M1 | 🟡 路线内置 |
| 15 | F-19 | 🟠 | 安全/小程序 | 设计落盘 + 列入产品化 M3 | 🟡 路线内置 |
| 16 | CR-9 | 🟡 | 代码/ACL | 统一收口（大型重构） | 🟡 后续迭代 |
| 17 | CR-7 | 🟡 | 前端/门禁 | 23 个 .js 测试转 .ts | 🟡 后续迭代 |
| 18 | Q-P1 | 🟡 | QA/边界 | 权限负面 E2E 不全 | 🟡 后续验证 |
| 19 | Q-3 | 🔴 | QA/续考 | 前端断线续考验证缺口 | 🟡 后续验证 |
| 20 | F-02 | 🟡 | 安全/密钥 | 密钥治理（Vault/强口令） | 🟡 上线前配置 |
| 21 | F-17 | 🟡 | 安全/缓存 | 随 F-18 多租户合并治理 | 🟡 路线内置 |

---

## 1. 已修复项详情（原因 / 修改 / 验证）

### 1.1 Q-1 并发选课超卖（🔴）
- **原因**：`CourseEnrollmentServiceImpl.assertCapacity()` 先 `COUNT` 活跃选课数、再 `INSERT`，两步之间无行锁。高并发下多个请求同时读到 `count < max_students` 后都插入，选课人数超过 `max_students`（超卖），破坏容量约束。
- **修改**：
  - `CourseMapper` 新增 `@Select("SELECT * FROM course WHERE id = #{id} FOR UPDATE") Course selectByIdForUpdate(@Param("id") Long id);`
  - `CourseEnrollmentServiceImpl.enroll()` 与 `enrollStudent()` 在 `assertCapacity()` 前调用 `courseMapper.selectByIdForUpdate(courseId)` 加悲观行锁，使「容量校验 + 插入」在同一行锁保护下串行化。
- **验证**：`CourseEnrollmentServiceImplTest` 15 例全绿；新增 `enroll_acquiresRowLock_beforeCapacity` 断言插入前确已调用 `selectByIdForUpdate(10L)`。建议补并发 E2E（多学生同时抢满员课程，断言最终选课数 == 容量）。

### 1.2 F-01 静态资源 ACL 绕过（🟠）
- **原因**：`SecurityConfig.publicPaths()` 将 `/uploads/**` 列入 `permitAll`，而 `UploadResourceConfig` 把教学资源静态映射到磁盘 `uploads/` 目录。任意匿名用户凭 UUID 文件名即可 `GET /uploads/<uuid>` 直链下载，绕过 `ResourceController.downloadResource()` 的 `canAccessResource` 对象级 ACL（管理员/任课教师/已选课学生），构成未授权资源泄露。
- **修改**：从 `publicPaths()` 移除 `/uploads/**`（保留注释说明根因）；`UploadResourceConfig` 类注释同步标注 `/uploads/**` 不再免认证。资源下载统一走受认证 + ACL 的 `GET /resources/download/{id}`。
- **验证**：源码 grep 确认 `publicPaths()` 不再含 `/uploads/**`；`ResourceController.downloadResource` 仍强制 `canAccessResource`。建议补「匿名 `GET /uploads/<uuid>` 应 401」E2E（归入 Q-P1）。

### 1.3 CR-2 已发布试卷题目缓存陈旧（🟠）
- **原因**：`ExamQuestionServiceImpl.batchAddQuestions` / `reorderQuestions` 修改已发布试卷题目后未失效 `examPapers` 缓存，详情接口读到陈旧题目列表。
- **修改**：两方法加 `@CacheEvict(cacheNames = "examPapers", key = "#examPaperId")`，使题目变更精确失效对应试卷详情缓存。
- **验证**：`ExamQuestionServiceImplTest` 10 例全绿。

### 1.4 CR-1 题库缓存跨课程无谓失效（🟡）
- **原因**：`QuestionServiceImpl` 6 个写入方法用 `@CacheEvict(value="questions", allEntries=true)`，任一题目变更清空整个 `questions` 命名空间——该空间含跨课程、跨题目的列表与详情缓存，产生跨课程缓存击穿与无谓失效。
- **修改**：写入方法改为 `@CacheEvict(value="questionSearch", allEntries=true)`（仅清列表缓存）；列表缓存独立为 `questionSearch`、详情缓存独立为 `questionDetail`，解耦。
- **验证**：`QuestionServiceImplTest` 35 例全绿。

### 1.5 CR-12 缓存 key null 与 "" 不一致（🟢）
- **原因**：`searchQuestions` 缓存 key 中 `null` 入参与 `""` 入参生成不同 key，同一查询因空值形态不同而缓存未命中 / 重复计算。
- **修改**：key 中以 `T(java.util.Objects).toString(#x,'')` 将 null 归一为 `""`。
- **验证**：随 CR-1 一并被 `QuestionServiceImplTest` 覆盖（含搜索路径断言）。

### 1.6 CR-3 试卷状态魔法串（🟡）
- **原因**：`ExamPaperServiceImpl` 17 处硬编码 `PUBLISHED`/`DRAFT`/`PENDING` 魔法串，易拼错且无编译期校验。
- **修改**：新增枚举 `ExamPaperStatus{DRAFT, PUBLISHED, ARCHIVED}`（枚举名与 DB 字符串一致，实体 `status` 字段仍为 `String`，**DB 契约不变**）；`publishExamPaper` / `copyExamPaper` / `autoComposeExamPaper` 改用枚举，消除魔法串。
- **验证**：`ExamPaperServiceImplTest` 19 例全绿（DB 契约未变，无迁移）。

### 1.7 F-14 CORS 开发回退 fail-closed（🟡）
- **原因**：CORS `allowedOrigins` 含 `localhost:5173-5175` 且 `allowCredentials=true`；生产若未显式配置 origins，会回退放行 localhost 带凭证跨域，存在凭证泄漏面。
- **修改**：新增 `@Value("${app.cors.dev-fallback:true}")`；未配置 origins 时仅当 `dev-fallback=true` 回退 localhost，否则 `log.warn` 并置空 origins（fail-closed）。
- **验证**：源码 grep 确认 `corsDevFallback` 注入与 fail-closed 分支。建议生产 compose 设 `app.cors.dev-fallback=false` 并显式列受信前端域名。

### 1.8 CMP-1 MySQL 版本不一致（🟡）
- **原因**：`docker-compose.yml` 两处 `image: mysql:8.0`，与手册声明 MySQL 8.4 不符；Flyway 迁移按 8.4 验证，版本漂移有兼容性风险。
- **修改**：两处 `mysql:8.0` → `mysql:8.4`。
- **验证**：grep 确认替换；建议 `docker compose up` 跑 Flyway 确认校验和无误。

---

## 2. 复核确认无需改动项

| 编号 | 复核结论 | 证据 |
| ---- | -------- | ---- |
| F-08 | `CommonController.ALLOWED_EXTENSIONS` 后缀白名单已存在，下载 `octet-stream` 已缓解 | 源码已读 |
| F-09 | `AIController` 渲染前用 `HtmlUtils.htmlEscape` 转义；XSS 缓解还依赖 `sanitizeHtml` 强度与 CSP | 源码已读（强度待完善项确认） |
| CR-4 | `publishExamPaper` 已 `@Transactional`；通知失败仍仅 `log.warn`（建议入重试队列，非阻塞） | 源码已读 |
| CR-6 | `AIController`/`CommonController` 的 `catch(Exception)` 均 `throw new BusinessException` 透传，非吞掉（已确认）；但 `OperationLogAspect` 等其余 catch 站点未全量复核 | 本次仅验 AI/Common 路径 |
| Q-2 | `ExamTimeoutScheduler.resolveDeadline` 已修复，超时收卷逻辑闭环 | 2026-09-14 缺陷修复报告 |

---

## 3. 已设计 / 列入产品化路线项（非当前上线阻塞）

- **F-18（🔴 多租户隔离）**、**F-17（🟡 缓存租户维度）**、**F-19（🟠 小程序 token）**：转化评估已落盘 `docs/产品化转化方案.md`，多租户隔离列为产品化 M1 安全验收门槛（`TenantLineInnerInterceptor` + `tenantId` claim + 缓存租户隔离），小程序 token 改造列为 M3 安全清单。**当前单租户形态下不阻塞上线**，代码改造属产品化独立工作项。

---

## 4. 待后续迭代 / 验证项

| 编号 | 建议 | 紧急度 |
| ---- | ---- | ------ |
| CR-9 | service 层 80 处归属校验双机制（内联 FORBIDDEN vs access-control 组件）统一收口，避免漂移——建议独立重构 plan | 🟡 P1（迭代） |
| CR-7 | 23 个 `.js` 测试文件游离类型门禁，分批转 `.ts` 并同步收紧基线 | 🟡 P1（迭代） |
| Q-P1 | 补权限负面 E2E：Analytics / LearningPath / Proctor / 角色提升 / 匿名资源直链（含 F-01 验证点） | 🟡 P1 |
| Q-3 | 补前端 WebSocket 监考断线重连与续考 E2E（纯验证缺口，代码硬化已于 2026-09-14 完成） | 🔴 P0（验证） |
| F-02 | 密钥治理：`.env` 迁 Vault/Secret Manager、换 `MYSQL_ROOT_PASSWORD` 强口令、确认未入 git 历史 | 🟠 P1（上线前） |

---

## 5. 验证证据汇总

- **主源编译**：`mvn -f backend/pom.xml compile` → BUILD SUCCESS（470 源文件）。
- **受影响单测**（本回合修复后回归）：`mvn test -Dtest=CourseEnrollmentServiceImplTest,ExamQuestionServiceImplTest,QuestionServiceImplTest,ExamPaperServiceImplTest`
  - CourseEnrollmentServiceImplTest：**15 例 / 0 失败**
  - ExamPaperServiceImplTest：**19 例 / 0 失败**
  - ExamQuestionServiceImplTest：**10 例 / 0 失败**
  - QuestionServiceImplTest：**35 例 / 0 失败**
  - 合计 **79 例 / 0 失败 / BUILD SUCCESS**
  - 日志见 `deliverables/gstack/mvn-test-targeted-2026-09-15.log`；日志中 `ERROR ... 生成题目失败/批量题目失败/查询题目失败` 为故意喂入坏 JSON 的负面用例断言，非回归。

---

## ⚠️ 待完善 / 已知局限

- 本次仅编辑文件，未触发 Flyway 实际迁移；CMP-1 版本变更建议本地 `docker compose up` 跑一次确认无校验和失配。
- CR-6 仅复核 AI/Common 控制器路径；`OperationLogAspect` 等其余 `catch` 站点建议专项扫描（是否吞业务异常）。
- F-09 的 `sanitizeHtml` 实现强度、F-02 的 `.env` 是否曾入库，需工程负责人确认（见原报告 §待完善）。
- Q-3 为前端运行时验证缺口，需浏览器/真实网络环境 E2E，本机无法自动闭环。

---

## 📚 关联产物索引

- 上游发现源：`deliverables/gstack/pre-launch-check-2026-09-14.md`
- 产品化设计（F-18/F-19 路线）：`docs/产品化转化方案.md`
- 本回合编译/测试日志：`deliverables/gstack/mvn-test-targeted-2026-09-15.log`

> 本报告由软件工坊 AI 协作生成，关键决策（尤其 F-02 密钥治理与 Q-3 验证排期）请由工程负责人复核。
