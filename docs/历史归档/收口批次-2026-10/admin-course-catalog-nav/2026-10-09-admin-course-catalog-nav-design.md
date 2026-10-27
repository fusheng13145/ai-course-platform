# 管理员端导航重构（按治理维度收敛 + 动态课程目录）· 设计定稿

- 日期：2026-10-09
- 批次登记：附录 **DR-48**（`docs/项目手册.md` D.12.3）
- 分支：`leyon`
- 状态：**设计定稿**（项目主逐节确认 + 自审修正 + §5 末越权面已知情接受），转入实施计划。

## 1. 需求来源与决策链

项目主要求"管理员端导航条参考教师端重构"。过程中项目主追加一份角色边界矩阵（大创文档）作为产品定义，
核心口径：**教师端是课程业务操作层（只管自己这门课）；管理员端是平台运维管控层（整个平台、所有课程、
全部用户）**。该矩阵推翻了我最初"把教师端骨架照搬到管理端"的方案，也决定了本设计的骨架顺序。

已确认的四个决策：

1. 管理端**要**动态课程目录（项目主选择"再加动态课程目录"，非仅结构收敛）。
2. 每门课下的页签是**治理视图子集**（不挂教师端的运营页签），不是教师端 14 项；`ai` 页签按项目主要求保留。
3. "跨课程 AI 统计"**本轮不做**，转入 §9 独立轮（取证见该节：无数据源）。
4. **本轮不实现前端只读态**：目录页签复用教师端在用页面，其写控件（新建/编辑/删除）对管理员
   **真实可用**（取证见 §5 末）。2026-10-09 项目主明确接受该口径（"接受，继续写实施计划"），
   因此 8 个页签全部保留、不加 `readOnly`，仅作为 §10 知情风险与 §8 台账登记，不再待定。

## 2. 现状与取证

三端共用同一侧边栏组件，管理端与教师端的差异全在配置层：

| 位置 | 现状 |
| --- | --- |
| `frontend/src/layouts/AdminLayout.vue` | 仅 `<AppShell role="ADMIN">` 薄壳，无独立导航实现 |
| `frontend/src/layouts/menus.ts:35-114` | `ADMIN_SECTIONS`：4 分区、**22** 条路径的扁平配置 |
| `menus.ts:119-144` | `COURSE_TABS` 只有 `TEACHER`（14）/ `STUDENT`（6）两套 |
| `menus.ts:147-150` | `COURSE_CATALOG_ENTRY` 只有 TEACHER / STUDENT |
| `menus.ts:157-179` | `buildCourseCatalogSection` 返回**自带分区标签**「课程目录」的整节，路径按 `role.toLowerCase()` 拼接 |
| `components/AppSidebar.vue:126` | `if (props.role === 'ADMIN') return`——管理端不取课程列表 |
| `AppSidebar.vue:143` | `if (props.role === 'ADMIN') return base`——管理端不拼课程目录 |
| `AppSidebar.vue:151` | `isActive('course-catalog')` 已按 `props.role` 构造正则，ADMIN 天然可用 |
| `AppSidebar.vue:186` | 自动展开祖先的正则写死 `^/(?:teacher\|student)/courses/(\d+)` |
| `router/index.ts:38-186` | `/admin` 下 **24 条治理叶子路由**（另 `profile`/`message-center` 两条个人页 + 空路径重定向），**无任何 `courses/:id` 课程壳** |
| `views/teacher/cockpit/CourseCockpit.vue` | 壳页三处写死角色：`:41` 返回链接、`:165` `COURSE_TABS.TEACHER`、`:170` `router.push('/teacher/courses/…')` |
| `layouts/__tests__/menus.test.ts:93-99` | 显式断言"管理员端维持扁平治理视图，不引入课程维度"（352549b 钉住的定案） |

管理端当前三处漂移（本轮一并修）：

- **落点被埋**：`router/index.ts:43` 空路径重定向到 `/admin/dashboard`，但「数据看板」排在第三个分区。
- **两条路由无入口**：`/admin/exams`（考试管理）、`/admin/grades`（成绩管理）有页面、路由存在，侧边栏进不去。
  `menus.test.ts` 只断言"导航里的路径可达"，不检查"路由是否有入口"，故未抓到。
- **分组口径乱**：「系统管理」与「系统配置」近义并存（后者平铺 8 项，教师/学生端每组 ≤3）；
  `shield` 图标重复用于「角色管理」与「监管与考试」。

数据面取证（本机 8080 实跑，2026-10-09）：

- `GET /api/courses?page=1&size=200` → 38 门课，分页信封字段是 **`list`** 而非 `records`
  （`AppSidebar.vue:133` 的 `body.records || body.list` 已兼容，无需改）。
- 38 门课 `approvalStatus` **全为 APPROVED**（`status`：ACTIVE 19 / INACTIVE 19）。
  ⇒ 曾考虑的"目录只挂待审批课程"在本机是空集，该筛选口径作废；目录改为**全量课程**，与教师端同一实现。
- `/admin/courses`（`views/admin/teaching/CourseManagement.vue:5` 副标题"审核、启停与维护平台全部课程"）
  **已是课程治理动作的落点**：`:21` 待审核统计卡、`:69-70` 审核状态筛选、`:282-286` `approveCourse` 行内动作。
  ⇒ 审核/启停/删除不需要新页面，课程目录只承担"进某门课看内容"的读视图。

## 3. 范围

**做**：`menus.ts` 配置重排与 `COURSE_TABS.ADMIN`；`AppSidebar.vue` 放开 ADMIN 动态目录；
`router/index.ts` 新增 `/admin/courses/:id/*` 壳路由；`CourseCockpit.vue` 按角色参数化；
测试与手册回写。

**不做**（明确排除）：

- **后端零改动**。不改 `SubmissionAccessControl`、不加 AI 埋点、不动任何 `@PreAuthorize`。
- 不挂 `submissions` / `proctor` / `interaction` 页签。`submissions` 有硬证据：
  `security/SubmissionAccessControl.java:103-117` 的 `assertOwnsCourse` 是全仓**唯一没有 ADMIN 豁免分支**
  的对象级归属断言（比对 `Course.teacherId`），管理员进作业批改必 403。管理员批改他人课程作业属权限语义
  变更，项目主未授权。
- 不发明死链：矩阵中四项无落点的能力不进导航（见 §8）。
- **不做前端只读态**（`readOnly` prop 之类的新抽象）：取舍与其后果见 §5 末，需项目主确认。
- 不搬动 `views/teacher/cockpit/` 文件位置（避免无谓 churn），就地复用并在头注释登记双角色消费方。

## 4. 设计一：分区骨架按矩阵六维度重排

最终侧边栏共 **7 个分区** = 1 个落点分区 + 矩阵六维度。其中**维度 2「课程管理」由动态「课程目录」
分区承担**（`buildCourseCatalogSection` 自带 `label: '课程目录'`，§5），因此 `ADMIN_SECTIONS` 静态配置
只写余下 6 节——不在静态表里再放一个"课程治理"空分区，避免同名双写。

顺序规则（`AppSidebar.vue:141-145` 的组装，ADMIN 分支）：

```js
// 落点必须第一（教师端 TEACHER_SECTIONS 只有「教学概览」是天然第一，管理端需显式插入）
if (props.role === 'ADMIN') {
  return [ADMIN_SECTIONS[0], ...buildCourseCatalogSection('ADMIN', courses.value), ...ADMIN_SECTIONS.slice(1)]
}
```

24 条治理路由**一条不丢、一条不多**（22 条已在导航 + 2 条孤儿收编），一级项图标全不重复
（图标名均取自 `layouts/icons.ts` 现有 **51** 个键，已逐个核对）：

| 顺序 | 分区 | 一级项（icon） | 二级 children → 路径 |
| --- | --- | --- | --- |
| 1 | 治理总览 | 治理大盘（`dashboard`） | — → `/admin/dashboard` |
| 2 | **课程目录**（动态，§5） | 课程目录（`book`） | 「课程治理总览」→ `/admin/courses`；下挂各课程的三级页签 |
| 3 | 用户与班级 | 用户与账号（`users`） | — → `/admin/users` |
| | 角色与权限（`lock`） | — → `/admin/permissions` |
| | 组织与班级（`building`） | — → `/admin/classes` |
| 4 | 题库与试卷 | 公共题库（`doc`） | — → `/admin/questions` |
| | 试卷与考试（`clipboard`） | — → `/admin/exams`（原孤儿，本轮收编） |
| 5 | 数据统计 | 跨课程分析（`sliders`） | — → `/admin/analytics` |
| | 全校成绩汇总（`award`） | — → `/admin/grades`（原孤儿，本轮收编） |
| 6 | 内容与资源治理 | 资源治理（`archive`） | 资源库 `/admin/resources`、资源版本 `/admin/resource-versions`、上传管理 `/admin/file-upload` |
| | 运行监管（`eye`） | 教学监督 `/admin/supervision`、考试治理 `/admin/exam-governance`、查重管理 `/admin/plagiarism` |
| 7 | 权限·系统·审计 | 系统参数与公告（`settings`） | 系统设置 `/admin/system`、通知公告 `/admin/notifications`、提醒管理 `/admin/reminders` |
| | AI 与知识库（`layers`） | AI 供应商配置 `/admin/ai-supplier`、知识库管理 `/admin/knowledge-base`、RAG 文档索引 `/admin/rag-docs` |
| | 安全与审计（`shield`） | 操作日志 `/admin/logs`、登录日志 `/admin/login-logs`、会话管理 `/admin/user-sessions` |

要点：

- 落点独立置顶，与教师端「教学概览」同构（`TEACHER_SECTIONS:186-191` 的既有口径）。
- 每个分区的**一级项 ≤3**，超出者用二级 children 聚合——这是教师/学生端已确立的口径，不是新发明。
- `/admin/profile`、`/admin/message-center` 仍由顶栏与用户菜单承担（`AppShell.vue:81` 的 `profilePath`
  回退按角色拼 `/admin/profile`），不进侧边栏，与三端一致。

## 5. 设计二：动态课程目录与 8 个治理页签

在 `COURSE_TABS` / `COURSE_CATALOG_ENTRY` 两个对象字面量内各新增一个 `ADMIN` 键（不是运行时赋值）：

```js
// menus.ts —— COURSE_TABS 对象内
ADMIN: [
  { suffix: 'overview',  text: '课程概览' },
  { suffix: 'chapters',  text: '章节与任务点' },
  { suffix: 'bank',      text: '课程题库' },
  { suffix: 'exams',     text: '试卷与考试' },
  { suffix: 'grades',    text: '成绩统计' },
  { suffix: 'analytics', text: '学情统计' },
  { suffix: 'knowledge', text: '知识图谱' },
  { suffix: 'ai',        text: 'AI 教学助手' },
]
// COURSE_CATALOG_ENTRY 对象内
ADMIN: { allText: '课程治理总览', allPath: '/admin/courses' }
```

- 目录数据源：`courseApi.getCourseList({ page: 1, size: 200 })`（`api/course.ts:3` → `GET /courses`，
  管理员取全库 38 门）。**不能用 `getMyCourses`**——`CourseController.java:178` 是
  `@PreAuthorize("hasRole('TEACHER')")`，ADMIN 必 403。
- `AppSidebar.vue:126` 去掉 ADMIN 短路，改为按角色选 API；`:143` 换成 §4 的三段组装式。
  `buildCourseCatalogSection` 本身已按 `role.toLowerCase()` 拼路径、`:151` 的 `isActive` 已按 `props.role`
  构造正则，**两处签名都不用改**。
- **`:186` 自动展开的正则须补 `admin`**，否则管理员进课程页签不展开祖先。
- 页签取舍依据矩阵：管理员拿到的是"**查看**平台全部课程、所有试卷、跨课程对比"，不是运营单课。
  故 `tasks`（作业配置）、`knowledge-management`（知识点编辑）、`discussion` 不挂——它们是教师动作；
  `submissions`/`proctor`/`interaction` 见 §3 排除理由。

### 页签的 ADMIN 可达性取证（读路径，逐条，后端不改）

| 页签 | 主要端点 | ADMIN 放行证据 |
| --- | --- | --- |
| overview | `GET /courses/{id}`、`/courses/{id}/stats` | 前者无 `@PreAuthorize`（`CourseController.java:67`）；后者 `hasAnyRole('TEACHER','ADMIN')` + `assertCourseOwner` L248 `if (isAdmin) return` |
| chapters | `GET /chapters/course/{courseId}` | `CourseChapterController.java:50` 无 `@PreAuthorize`，仅 `SecurityConfig:332` `anyRequest().authenticated()` |
| bank | `QuestionController` 课程题库读路径 | 类/方法 `hasAnyRole('TEACHER','ADMIN')`（L66-394 区间），文件内无课程归属断言 |
| exams | `ExamPaperController` 读 + `ExamSessionAccessControl.assertReadable` | 注解含 ADMIN；`ExamSessionAccessControl.java:100-110` 非学生即放行 |
| grades | `GradeController` `/policy/course/{id}`、`/report/course/{id}` | `hasAnyRole('TEACHER','ADMIN')` + `assertCourseOwner` L102-116 显式 ADMIN 放行 |
| analytics | `AnalyticsController` 14 端点 | 全部 `hasAnyRole('TEACHER','ADMIN')`（L69-225），无归属断言 |
| knowledge | `KnowledgeController` | `hasAnyRole('TEACHER','ADMIN')`（L126-524），无归属断言 |
| ai | `POST /ai/qa/ask` 等 | `SecurityConfig:308-311` 对任意已登录角色放行；`AIController` 类级 `isAuthenticated()` |

### ⚠️ 这些页签不是"只读"——写控件对管理员真实可用

复用教师端在用页面意味着**连同其写控件一起复用**。自审时逐层核到方法级，确认这不是理论风险：

- `CourseChapterController.java:83`（新建章节）、`:98`（更新）、`:114`（删除）、`:128`（批量排序）
  全部是 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`，**且方法体内没有课程归属断言**。
  ⇒ 管理员在 `/admin/courses/4007/chapters` 点「新建/删除章节」会**真的写入他人课程**。
- 该模式是仓库既有行为（统一路由 `/teacher/bank` 等本就对 ADMIN 开放），本轮**未新增**授权面，
  但把入口从"没有导航"变成"三级页签常驻侧边栏"，**可发现性显著提升**。

处置决定（**2026-10-09 项目主已拍板：接受，本轮不做只读态**）：

- 8 个页签全部保留，不加 `readOnly` prop，不动任何后端注解——按 §4/§5/§6 原样实现。
- 已评估但**未采用**的退路（留档，若日后项目主改变口径按代价从小到大取用）：
  ① 从目录页签去掉 `chapters`（写面最大者）；② 追加 `readOnly` prop，仅对
  `chapters`/`bank`/`grades` 三页隐藏写控件；③ 后端为写路径补 ADMIN 归属判断
  （**违背本轮"后端零改动"承诺，须另立轮次**）。
- 该越权面**不是本轮引入的**（统一路由 `/teacher/bank` 等本就对 ADMIN 开放），本轮只是让它有了一级入口；
  已作为 §10 知情风险登记，收尾报告须如实复述一次，不得静默。

## 6. 设计三：路由与壳组件参数化

**路由**（`router/index.ts`，插在 `/admin.children` 内，与 `courses` 平级）：

```
{ path: 'courses/:id', component: CourseCockpit, meta: { title: '课程治理驾驶舱' }, children: [
    { path: '', redirect: (to) => `/admin/courses/${to.params.id}/overview` },
    …8 条，component 与教师端同名页签逐一对应：overview/chapters/bank/exams/grades/analytics
    取 views/teacher/cockpit/*，knowledge 取 views/teacher/tools/KnowledgeGraph.vue，
    ai 取 views/teacher/tools/AICenter.vue（router/index.ts:221-293 现有映射，照抄即可）
] }
```

- 空子路径的 redirect **必须写成函数式绝对路径**，与 80e8f9d（DR-43）对教师端课程壳的修法同口径，
  否则裸 `/admin/courses/4007` 会落 404。
- 父级 `/admin` 已带 `meta: { requiresAuth: true, role: 'ADMIN' }`（`router/index.ts:41`），子路由自动继承，
  守卫不需要新增分支。
- `views/teacher/tools/AICenter.vue` 经核**无硬编码角色路径**（`:96` 那处 `/teacher/courses/:id/ai` 只是注释，
  课程上下文读 `route.params.id`），可直接复用。

**壳组件**（`CourseCockpit.vue` 三处硬编码改为按角色推导）：

| 行 | 现状 | 改为 |
| --- | --- | --- |
| `:41` | `to="/teacher/courses"` | 计算属性 `backPath = /${role}/courses` |
| `:165` | `COURSE_TABS.TEACHER.map(…)` | `COURSE_TABS[role]` |
| `:170` | `router.push('/teacher/courses/…')` | 用 `role` 拼前缀 |

`role` 从 `route.path` 首段推导（`admin` / `teacher`），不新增 prop，避免调用方漏传。
文件头注释登记"TEACHER / ADMIN 双角色消费"。`cockpit/__tests__/CourseCockpit.test.ts` 已有
DR-46/DR-47 用例，须补一例 admin 前缀下返回链接与页签集合不串回教师端。

## 7. 测试与验证

**单元/组件**：

1. `menus.test.ts:93-99` 的"管理端维持扁平、不引入课程维度"断言**反转**为：
   `COURSE_TABS.ADMIN` 每条 suffix 必须命中真实 `/admin/courses/1/<suffix>` 路由；
   同时保留"静态 `ADMIN_SECTIONS` 内不出现 `课程目录` 标签"这一半（课程目录只由
   `buildCourseCatalogSection` 动态生成，禁止静态/动态双写）。
2. 新增**分区组装口径**断言：`navPathsOf('ADMIN')` 的第一条必须是 `/admin/dashboard`（落点置顶），
   且动态目录分区紧随其后——防止 §4 的三段切片顺序被后来者改回 `[...catalog, ...sections]`。
3. `menus.test.ts` 新增**反向覆盖检查**（本轮暴露的真缺口）：`/admin` 下每条治理叶子路由都必须被
   侧边栏或顶栏/用户菜单覆盖，`exams`/`grades` 收编后不得再有孤儿；该用例须以"故意注释掉一项→测试变红"
   做阴性对照，否则可能是恒真断言。
4. `layouts/components/__tests__/AppSidebar.test.ts:101`「管理员端：扁平治理视图，不构建课程目录也不拉取
   课程列表」**必须改写**（本轮反转语义），并补：管理端调 `getCourseList` 而非 `getMyCourses`、
   目录渲染出课程节点、页签点击 → `router.push('/admin/courses/<id>/<suffix>')`、
   进课程子路由时 `course-catalog` 与课程节点祖先自动展开（钉住 `:186` 正则补 `admin` 那处改动）。
   ⚠️ 该测试文件当前只 mock 了 `getMyCourses`（`:22/:26`），管理端要新增 `getCourseList` 的 mock。
5. `router/__tests__/nav-reachability.test.ts` 纳入 admin 课程壳路径，并钉住
   `/admin/courses`（列表页）不被 `/admin/courses/:id` 壳吞掉。
6. `cockpit/__tests__/CourseCockpit.test.ts` 补 admin 前缀用例：返回链接落 `/admin/courses`、
   页签集合为 8 项且不串回教师端 14 项。

**真跑验证**（按项目主既定口径，不用 `router.resolve()` 代替跳转）：外部 Chrome 打开 `http://localhost:5174`，
以 `admin / 123456` 登录，逐页签真实点击 8 个页签（至少覆盖课程 4007 与另一门 INACTIVE 课程），
每页截图并检查 console 无报错、无 403 信封、无空白页；教师端与学生端各回归一次登录落点与课程目录，
确认壳组件参数化未把教师端改坏。注意本机登录有频率限制（实测连打约 7～9 次触发 429），
取证脚本须留间隔。

**门禁**：`npm run lint`、`vue-tsc` 类型检查、`vitest run`（前端全量）；后端本轮无改动，不跑 `mvn verify`，
但须在收尾报告如实写明"后端未验证是因为未改动"。

## 8. 文档回写（与实现同一提交）

- `docs/项目手册.md` §4 导航信息架构（`:85-92`）：管理端由"扁平治理视图"改述为
  "六治理维度 + 动态课程目录（只读页签 8 项）"，并说明与矩阵的对应关系。
- D.12.3 新增 **DR-48** 条目登记本轮（⚠️ 台账现止于 DR-43，而代码里已存在 DR-44/45/46/47 的引用
  ——`JwtUtils.java:75`、`request.ts:387`、`CourseCockpit.vue:164` 等，**手册未收录**；本轮编号取
  下一个空位 DR-48，并在条目内注明该台账漂移，避免后续撞号）。
- D.12.4 未闭合清单追加矩阵中**四项无落点的管理能力**（本轮刻意不进导航，防死链）：
  ① 创建学期；② 归档过期学期；③ 内容审核（讨论区评论 / 平台资源审核）；④ 限制教师出题权限。
  取证：`grep -iE "semester|archive|moderat|comment" frontend/src/router/index.ts` 零命中（2026-10-09）。
- 管理员页签写控件可用**不进 D.12.4**（项目主已知情并接受，属定案而非待办）；改在 D.12.3 的 DR-48
  条目内一句话记明"课程目录页签复用教师端页面，写动作对 ADMIN 可用（`CourseChapterController.java:83/98/114/128`
  无归属断言），系项目主 2026-10-09 接受的口径"，避免后来者当成 bug 反复追查。

## 9. 后续独立轮：跨课程 AI 统计（本轮不做）

项目主要求保留 `ai` 页签并加"跨课程 AI 统计"。`ai` 页签本轮已做（§5）。统计部分经取证**无数据源**，
独立成轮，前置如下：

1. 唯一的 AI 落库表 `ai_chat_sessions`（`V1__init_schema.sql:863-886`）**没有 `course_id` 列**；
   且 `AIController.java:99` 写作 `aiChatSessionService.createSession(userId, null)`——`courseId` 在
   `:93/:96` 已收到却在入口处丢弃，只透传给 RAG 引擎（`:105`）。全仓搜 `AiCallLog` / `ai_usage` /
   `prompt_tokens` 在 `entity/`、`db/`、`llm/`、`integration/` 均零命中。
2. **现成的 `GET /admin/statistics/ai-usage`（`SystemAdminController.java:424`）返回的是编造数字**：
   `SysMonitorComponent.java:136` `totalCalls = todayLogins * 3`（注释自承"估算"）、`:139` 成本同理、
   `:143` `supportedCourses` 只是课程总数。它目前前端零消费（`api/admin.ts:67 getAIUsage` 无任何调用方），
   所以尚未在演示中露馅。⇒ **任何新 AI 统计页都不得接该端点**；建议同轮把它改为如实返回"未采集"，
   以免后续有人误接。
3. 降级口径（解析 `operation_logs` 中 `module='AI'` 的 params JSON 捞 courseId）**判定不可用**：
   `OperationLog.java:17-33` 无 `course_id` 列、params 截断 1000 字符、作文批改/润色调用本就不带课程，
   产出会是"看着像真的错数"。
4. 真做的五步：加 Flyway 迁移（新表 `ai_call_logs` 或给 `ai_chat_sessions` 补 `course_id`/provider/tokens/耗时）
   → 约 12 个 AI 入口补埋点写入 → 按课程维度聚合端点（`hasRole('ADMIN')`）→ 前端页 + 导航入口 → 测试与手册。
5. ⚠️ 统计只能从埋点上线当日起算，**历史数据无法回填**；演示若需要曲线数据，须提前排期。

## 10. 风险

- **管理员经页签可写入他人课程**（可发现性放大）：项目主已于 2026-10-09 知情并接受（§5 末），
  本轮不加只读态；风险仍然存在，收尾报告须复述，且日后若有人把写 403/误删当 bug，先看 §5 末留档退路。
- **壳组件参数化影响教师端**：`CourseCockpit.vue` 是教师端在用页面，改 `:165`/`:170` 有回归风险，
  故 §7 要求教师端逐页签回归，不只测管理端。
- **38 门课全量入目录**：侧边栏长度显著增加，靠默认折叠 + 滚动承载。若演示时观感不可接受，
  退路是加"仅 ACTIVE（本机 19 门）"开关式常量——但任何筛选都必须先有真实数据依据，
  不接受凭想象定规则（"待审批"空集即本轮教训）。
- **`/admin/courses/:id` 与既有 `/admin/courses` 列表页并存**：路由匹配顺序上 `courses`（静态）先于
  `courses/:id`，Vue Router 按路径特异性排序不受书写顺序影响，但须有一条用例钉住"列表页不被壳页吞掉"。
