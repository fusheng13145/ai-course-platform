# E2E 验证迭代报告 · Q-1 并发无超卖 + F-01 静态 ACL + Q-P1 越权负向

**日期**：2026-09-14
**场景**：上线前检查 / 端到端验证（E2E 收口）
**参与成员**：排障手（调试与根因）+ 质量门神（QA 测试与发布）
**协作模式**：单 Agent 直调（主理人沽思航以排障手 + 质量门神双视角执行，均由本人完成，未建团队）

---

## 📌 TL;DR（执行摘要）

- 整体结论：🟢 **通过**（8/8 E2E 全绿）
- 本轮新发现并修复 **2 个 Q-1 源码缺陷**（此前仅停留在「接口对齐」层面，真正并发 + 真实表名下方暴露）：
  1. `CourseMapper.selectByIdForUpdate` 表名写错（`course` → 应为 `courses`），每次选课必 500；
  2. MySQL REPEATABLE READ 快照超卖：`assertCapacity` 原用普通 `count()` 读事务快照，并发事务彼此看不见对方已提交的新增选课 → 容量上限被突破（max=3 放行 4 人）。
- F-01（静态资源直链免认证）与 Q-P1（三例越权负向）均已在源码层面修复并实机验证：
  - F-01：匿名直链 `/uploads/<file>` → **401**（修复前 permitAll 可直链下载）；已登录教师经 ACL 接口 → **200**（证明 401 是认证拦截而非文件缺失）。
  - Q-P1：学生 POST `/teacher/courses` → **403**；匿名下载资源 → **401**；未选课学生下载本课资源（对象级 ACL）→ **403**。
- 阻塞项：**0**。下一步：将本轮新增/修改纳入既有回归基线（见「待完善」）。

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟢 Go（8/8 通过，无阻塞） |
| 严重度分布 | 🔴 0 / 🟠 0 / 🟡 2（Q-1 两源码缺陷，均已修复并验证）/ 🟢 8 |
| 关键行动项 | 2 条（已固化回归护栏，待并入基线） |
| 建议负责人 | 主理人 / 后端 Owner（阁下） |
| 验证口径 | 真实后端（jar）+ MySQL 8.4 + 并发线程屏障，非单测 mock |

---

## 1. 各成员核心结论

### 🔧 排障手（调试与根因）
- **核心判断**：Q-1 并发超卖并非「没加锁」，而是「锁了错误的对象 + 读了错误的视图」。课程行已 `FOR UPDATE` 串行化，但容量计数走 MyBatis-Plus 普通 `count()`，在 REPEATABLE READ 下读各事务自己的 MVCC 快照（均看到 0 个有效选课），于是 4 个事务全部判定「未满员」并各自插入 → 超卖。另一处是 `SELECT ... FROM course` 表名拼错（实体 `@TableName("courses")`），导致锁查询本身抛 `BadSqlGrammarException` 500，掩盖了超卖。
- **关键建议**：容量校验的计数必须与课程行锁处于同一「当前读」视图，即计数也要 `FOR UPDATE`；任何手写 `@Select` 必须核对实体真实表名。

### ✅ 质量门神（QA 测试与发布）
- **核心判断**：8 例用例覆盖「并发正反向 + 匿名/越权负向 + 对照可达性」，两两组合（max=3 与 max=1）验证了锁的边界有效性，F-01 用「401 + 200 对照」排除了「文件缺失」误判。结论可复现。
- **关键建议**：将本脚本（`e2e_verify_iteration_q1_qp1.py`）并入门级回归三件套，并补一条单测直接断言 `countActiveForUpdate` 在并发事务下返回当前值（单元测试层固化 REPEATABLE READ 陷阱）。

> 本轮回合仅上述两位成员实际上场（排障 + QA），产品官 / 安全卫士 / 设计师未单独调度。

---

## 2. 综合审查发现（按严重度排序）

| # | 严重度 | 类别 | 位置 | 问题描述 | 修改 | 来源成员 |
|---|--------|------|------|---------|------|---------|
| 1 | 🟡 | 缺陷（并发/SQL） | `backend/.../mapper/CourseMapper.java:21` | `selectByIdForUpdate` 写 `SELECT * FROM course ... FOR UPDATE`，表名错误（真实表为 `courses`），每次选课抛 `BadSqlGrammarException` → HTTP 500 | 改为 `FROM courses` | 排障手 |
| 2 | 🟡 | 缺陷（并发超卖） | `CourseEnrollmentServiceImpl.assertCapacity` + `CourseEnrollmentMapper` | 容量计数用普通 `count()` 读 REPEATABLE READ 快照，并发事务彼此不可见对方已提交选课，突破 `max_students` | 新增 `countActiveForUpdate(... FOR UPDATE)` 当前读计数；`assertCapacity` 改用 `baseMapper.countActiveForUpdate(courseId)` | 排障手 |
| 3 | 🟢 | 安全（ACL） | `SecurityConfig.publicPaths()` | 修复前 `/uploads/**` 在 publicPaths 中 `permitAll`，匿名凭 UUID 直链即可下载教学资源，绕过 `ResourceController.canAccessResource` 对象级 ACL（F-01） | 自 `publicPaths()` 移除 `/uploads/**`；下载统一走需认证 + ACL 的 `/resources/download/{id}` | 质量门神 |
| 4 | 🟢 | 安全（越权负向） | `TeacherController` / `ResourceController` | 学生越权建课、匿名下载、未选课学生下载本课资源等负向路径需实机确认返回码 | 经 E2E 确认：学生 POST `/teacher/courses`→403；匿名 GET `/resources/download/{id}`→401；未选课学生下载本课→403 | 质量门神 |

### 用例处置明细（8/8）

| 用例 | 标题 | 期望 | 实际 | 判定 |
|------|------|------|------|------|
| Q1-A | 并发选课(max=3, 4学生)无超卖 | 成功=3 / 满员拒=1 / DB有效=3 | HTTP200=3 / HTTP400=1 / DB(is_active=1)=3 | ✅ PASS |
| Q1-B | 并发选课(max=1, 4学生)无超卖 | 成功=1 / 满员拒=3 / DB有效=1 | HTTP200=1 / HTTP400=3 / DB(is_active=1)=1 | ✅ PASS |
| F01-0 | 教师上传教学资源 | HTTP 200 + /uploads/<uuid> | HTTP 200 url=/uploads/d7d2cde7-...png id=4 | ✅ PASS |
| F01-1 | 匿名直链 /uploads/<file>（F-01 验收） | 401 | HTTP 401 | ✅ PASS |
| F01-2 | 已登录教师经 ACL 下载接口可达 | 200 | HTTP 200 | ✅ PASS |
| QP1-1 | 学生越权 POST /teacher/courses | 403 | HTTP 403 | ✅ PASS |
| QP1-2 | 匿名 GET /resources/download/{id} | 401 | HTTP 401 | ✅ PASS |
| QP1-3 | 未选课学生下载本课资源（对象级 ACL） | 403 | HTTP 403 | ✅ PASS |

---

## ✅ 行动清单

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
|---|------|--------|--------|---------|
| 1 | 将 `e2e_verify_iteration_q1_qp1.py`（8 例）并入门级回归三件套，纳入「改后端后必跑」基线 | 主理人 | P1 | 本周 |
| 2 | 补单测：并发事务下断言 `countActiveForUpdate` 返回当前值，固化 REPEATABLE READ 陷阱（防回退） | 后端 Owner | P2 | 本周 |
| 3 | 全仓扫描其他手写 `@Select`/`@Update` 是否同样存在表名/视图错配（本次 `course`→`courses` 为同类隐患） | 主理人 | P2 | 评审时 |

---

## ⚠️ 待完善 / 已知局限

- 本轮仅闭合 **Q-1 并发无超卖、F-01 静态 ACL、Q-P1 越权负向** 三块；以下为更早轮次遗留、本轮未纳入：
  - **CR-9**：统一 80 处访问控制站点（权限模型归一）；
  - **CR-7**：23 个 `.js` 测试迁移为 `.ts`；
  - **Q-3**：前端断线重连 / 续传 E2E；
  - **F-02**：密钥保险库 / 强口令策略。
- 后端当前以 `java -jar` 本地实机运行（背景任务），未注入 AI/邮件凭据时 `/actuator/health` 返 503 属探针环境所致，非缺陷；业务接口不受影响。
- Git 铁律：本仓位于中文父目录，全程仅做 Edit/Write，未执行任何 git 操作。

---

## 📚 成员产出索引

- 排障手（根因 + 源码修复）原始产出：`backend/src/main/java/com/aicourse/mapper/CourseMapper.java`、`CourseEnrollmentMapper.java`、`service/impl/CourseEnrollmentServiceImpl.java`；运行日志 `backend/target/e2e-backend-run.log`（含 `BadSqlGrammarException: Table 'ai_course_platform.course' doesn't exist` 栈）。
- 质量门神（E2E 验证）原始产出：`deliverables/gstack/e2e_verify_iteration_q1_qp1.py`、证据 `deliverables/gstack/e2e_iteration_result.json`。

---

> 本报告由软件工坊 AI 协作生成，关键决策（尤其 Q-1 源码改动）请由工程负责人复核。
