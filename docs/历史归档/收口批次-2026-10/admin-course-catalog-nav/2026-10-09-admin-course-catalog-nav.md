# 管理员端导航重构（六治理维度 + 动态课程目录）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理员端侧边栏按「平台运维管控层」的六个治理维度重排，并为 ADMIN 增加动态课程目录（8 个治理页签），复用教师端课程壳与页面，后端零改动。

**Architecture:** 三端共用 `AppShell.vue → components/AppSidebar.vue` 手风琴四级结构，导航形状完全由 `layouts/menus.ts` 的配置 + `AppSidebar.vue` 的组装规则决定，因此本轮改动集中在配置层（menus.ts）、组装层（AppSidebar.vue）、路由层（router/index.ts）与一处组件参数化（CourseCockpit.vue 按角色推导前缀）。管理端课程目录列出全部 38 门课（`GET /courses`），每门课下挂 8 个治理页签，其路由复用 `views/teacher/cockpit/*` 既有视图。

**Tech Stack:** Vue 3.5 + Vite 8 + vue-router 4.6（SPA，无 SSR）、Vitest 3.2 + @vue/test-utils 2.4（jsdom）、ESLint 9 flat config、vue-tsc 2.2、Element Plus；后端 Spring Boot 3（本轮不改）。

**Spec:** `docs/superpowers/specs/2026-10-09-admin-course-catalog-nav-design.md`（执行时两份都要读；§5 末的"写控件对管理员可用"是项目主已知情接受的定案，不要当 bug 修）

## Global Constraints

- **后端零改动**：不改任何 `@PreAuthorize`、`SecurityConfig`、`SubmissionAccessControl`、AI 埋点。本轮只动 `frontend/src` 与 `docs/`。
- **不新增授权面**：页签清单只能包含 Spec §5 已逐条取证"ADMIN 可读"的 8 个 suffix；`submissions` / `proctor` / `interaction` / `tasks` / `knowledge-management` / `discussion` **一律不挂**。
- **不发明死链**：手册 §18.5 相邻的导航纪律禁止"先放入口再补页面"。矩阵中四项无落点能力（创建学期、归档过期学期、内容审核、限制教师出题权限）**不进导航**，只写进 D.12.4。
- **不搬文件、不用 `git mv` / `git rm`**：本仓库有中文路径，这两个命令曾静默清空整棵树；需要移动时一律 `cp` + `rm` + `git add`，并 md5 核对。本轮**不需要移动任何文件**。
- **课程列表数据源**：ADMIN 用 `courseApi.getCourseList({ page: 1, size: 200 })`；**绝不能用 `getMyCourses`**（`CourseController.java:178` 是 `hasRole('TEACHER')`，管理员必 403）。
- **筛选口径必须有真实数据依据**：本机 38 门课 `approvalStatus` 全为 APPROVED，故目录列全量，不做"待审批"筛选。
- **页签文案单一来源**：侧边栏三级页签与壳内页签栏都从 `COURSE_TABS[role]` 取，禁止任何一处再写第二份文案（DR-47 的教训）。
- **空子路径重定向必须写成函数式绝对路径**并带上 `:id`（DR-43 口径）；`redirect: 'overview'` 这类相对串是禁止写法。
- **图标只用 `layouts/icons.ts` 现有 51 个键**，不新增 SVG。
- **提交纪律**：每个 Task 结尾一次提交；提交信息用中文、按 `feat(nav):` / `test(nav):` / `docs:` 前缀；**不 push、不开 PR**（推送与合并需项目主确认）。
- **门禁口径**：`cd frontend && npm run lint && npm run typecheck && npx vitest run`（`typecheck` 是 `vue-tsc --noEmit`；类型基线由 `npm run typecheck:gate` 把守）。
- **真跑验证不可省略**：项目主要求"必须真跑验证"，SPA 跳转缺陷用 `router.resolve()` 观察不到（DR-43 即因此隐身），故 Task 5 必须在真实浏览器里点击。

---

## 前置条件：工作区基线（开工前必须由项目主解决）

计划写成后核对 `git status` 发现：**工作区有一批未提交改动（16 文件 / +329 −99），与本计划要改的三个文件重叠**。

- 重叠文件：`frontend/src/layouts/menus.ts`、`frontend/src/router/index.ts`、`views/teacher/cockpit/CourseCockpit.vue`
- 内容经核为 **DR-45 / DR-46 / DR-47** 这批在途工作（`router/index.ts` 守卫消费 `fetchPermissions` 回传结论；`CourseCockpit.vue` 头部改真实读数 + 页签取 `COURSE_TABS.TEACHER`；`menus.ts` 给 `COURSE_TABS.TEACHER` 补 `proctor`；另含后端 `JwtUtils`/`UserSessionServiceImpl` 与 `AppShell.vue`、`stores/auth.ts`、`api/request.ts` 等），另有 **2 个未跟踪测试文件/目录**（`router/__tests__/nav-reachability.test.ts`、`cockpit/__tests__/`）。
- 本计划里所有 `file:line` 引用、"教师端 14 页签"、以及"待改写的既有测试用例编号"（如 `AppSidebar.test.ts:101`、`menus.test.ts:93-99`）**都是按这棵脏树取的**——它是实际运行的状态，因此对实现是对的；但一旦这批改动被回退或重排，行号与用例名会一起漂移。

**执行方式必须先二选一**（否则 Task 1–4 的 `git add <文件>` 会把别人的在途 hunk 一并提交，提交信息就成了替他人工作背书）：

- **口径 A（推荐）**：项目主确认这批改动归属后，**先由项目主或经其授权、以独立提交落盘**（提交信息写 DR-45/46/47 的事实，不混入本轮内容），本计划再在其之上执行。
- **口径 B**：从 HEAD 另开隔离 worktree 执行本计划。代价：`COURSE_TABS.TEACHER` 在 HEAD 是 **13 项**（无 `proctor`）、`CourseCockpit.vue` 头部仍是伪造读数、`nav-reachability.test.ts` 与 `cockpit/__tests__/` **不存在**，Task 3/4/5 的多处"改写既有用例"要改成"新增用例"，且真跑验证需另起一套端口。

在口径确定前，**不要开始 Task 1**。

---

### Task 1: 管理端课程壳路由

**Files:**
- Modify: `frontend/src/router/index.ts`（在 `/admin.children` 内、`courses` 路由之后插入，当前 `courses` 记录位于 `:68-72`）
- Test: `frontend/src/router/__tests__/course-shell-redirect.test.ts`

**Interfaces:**
- Consumes: `views/teacher/cockpit/CourseCockpit.vue` 等既有视图（组件路径见下表，全部已存在）
- Produces: 路由记录 `/admin/courses/:id`（含子记录 `/admin/courses/:id/<suffix>`，8 个 suffix）与 `/admin/courses` 列表页并存；Task 3 的 `COURSE_TABS.ADMIN`、Task 4 的侧边栏跳转都依赖这些路径真实存在

- [ ] **Step 1: 先写失败测试（管理端裸课程 URL 应落概览，而非 404）**

在 `frontend/src/router/__tests__/course-shell-redirect.test.ts` 的 `describe('课程壳空路径重定向', …)` 块**末尾**（现在最后一个 `it` 结束于 `:78`，`})` 在 `:79`）追加：

```ts
  it('管理端从治理大盘点裸课程 URL 落在课程概览', async () => {
    expect(await landedFrom('/admin/dashboard', '/admin/courses/4007')).toBe(
      '/admin/courses/4007/overview',
    )
  })

  it('管理端裸课程 URL 保留课程 id，匹配链落到课程壳而非兜底 404', async () => {
    const r = probe()
    await r.push('/admin/users')
    await r.push('/admin/courses/4023')
    expect(r.currentRoute.value.params.id).toBe('4023')
    expect(r.currentRoute.value.matched.map((m) => m.path)).toEqual([
      '/admin',
      '/admin/courses/:id',
      '/admin/courses/:id/overview',
    ])
  })

  it('管理端裸课程 URL 冷启动直达（无历史起点）也不落兜底 404', async () => {
    expect(await landedFrom('/', '/admin/courses/4007')).toBe('/admin/courses/4007/overview')
  })

  it('课程列表页 /admin/courses 不被 courses/:id 壳吞掉', async () => {
    expect(await landedFrom('/admin/users', '/admin/courses')).toBe('/admin/courses')
    expect(await landedFrom('/admin/dashboard', '/admin/courses/4007/bank')).toBe(
      '/admin/courses/4007/bank',
    )
  })
```

- [ ] **Step 2: 运行测试，确认因"路由不存在"而失败**

```
cd frontend && npx vitest run src/router/__tests__/course-shell-redirect.test.ts
```

预期：新增 4 例中前 3 例 FAIL（落点是 `/:pathMatch(.*)*` 兜底，`currentRoute.fullPath` 仍为 `/admin/courses/4007` 且 matched 链只有根兜底记录）；第 4 例的 `/admin/courses` 半段 PASS，`/admin/courses/4007/bank` 半段 FAIL。若 4 例全绿说明路由已存在，停下核对分支。

- [ ] **Step 3: 加管理端课程壳路由**

在 `frontend/src/router/index.ts` 里 `path: 'courses'`（`:68-72`，指向 `views/admin/teaching/CourseManagement.vue`）这条记录**之后**插入。8 条子路由与教师端 `:221-293` 的映射逐一照抄（组件路径不得改写、不得搬文件）：

```ts
      // 课程治理驾驶舱：复用 views/teacher/cockpit/* 视图（壳按 route 首段推导角色，见 CourseCockpit.vue）
      // 页签面是「查看/治理」子集，写动作沿用后端既有 ADMIN 姿态（spec §5 末，项目主已接受）
      {
        path: 'courses/:id',
        component: () => import('@/views/teacher/cockpit/CourseCockpit.vue'),
        meta: { title: '课程治理驾驶舱', breadcrumb: '课程治理' },
        children: [
          { path: '', redirect: (to) => `/admin/courses/${to.params.id}/overview` },
          {
            path: 'overview',
            component: () => import('@/views/teacher/cockpit/CourseOverview.vue'),
            meta: { title: '课程概览' },
          },
          {
            path: 'chapters',
            component: () => import('@/views/teacher/cockpit/CourseChapters.vue'),
            meta: { title: '章节与任务点' },
          },
          {
            path: 'bank',
            component: () => import('@/views/teacher/cockpit/CourseQuestionBank.vue'),
            meta: { title: '课程题库' },
          },
          {
            path: 'exams',
            component: () => import('@/views/teacher/cockpit/CourseExams.vue'),
            meta: { title: '试卷与考试' },
          },
          {
            path: 'grades',
            component: () => import('@/views/teacher/cockpit/CourseGrades.vue'),
            meta: { title: '成绩统计' },
          },
          {
            path: 'analytics',
            component: () => import('@/views/teacher/cockpit/CourseAnalytics.vue'),
            meta: { title: '学情统计' },
          },
          {
            path: 'knowledge',
            component: () => import('@/views/teacher/tools/KnowledgeGraph.vue'),
            meta: { title: '课程知识图谱' },
          },
          {
            path: 'ai',
            component: () => import('@/views/teacher/tools/AICenter.vue'),
            meta: { title: '课程 AI 教学助手' },
          },
        ],
      },
```

- [ ] **Step 4: 重跑测试确认全绿**

```
cd frontend && npx vitest run src/router/__tests__/course-shell-redirect.test.ts
```

预期：该文件 10 例全 PASS（原 6 例 + 新 4 例）。

- [ ] **Step 5: 跑可达性套件，确认没有把死链带进来**

```
cd frontend && npx vitest run src/router/__tests__/nav-reachability.test.ts
```

预期：4 例全 PASS（`COURSE_TABS` 尚无 ADMIN 键，此步只验证新路由本身合法且源码写死目标未变）。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/router/index.ts frontend/src/router/__tests__/course-shell-redirect.test.ts
git commit -m "feat(nav): 管理端新增课程治理驾驶舱壳路由，复用教师端课程视图"
```

---

### Task 2: CourseCockpit 壳组件按角色推导

**Files:**
- Modify: `frontend/src/views/teacher/cockpit/CourseCockpit.vue`（三处：模板 `:41`、脚本 `:165`、`:170`）
- Test: `frontend/src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts`

**Interfaces:**
- Consumes: `COURSE_TABS.TEACHER` / `COURSE_TABS.ADMIN`（后者 Task 3 才加入；本 Task 的测试只断言 TEACHER 路径未被改坏，ADMIN 断言在 Task 3 补）
- Produces: 壳内 `role`（由 `route.path` 首段推导，不新增 prop）、`backPath`、`cockpitTabs`；Task 3/4 依赖"同一组件在 `/admin/...` 前缀下不串回教师端"

- [ ] **Step 1: 先写失败测试（教师端返回链接与页签栏在 admin 前缀下的形状）**

在 `frontend/src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts` 里，`mountCockpit()`（`:50-54`）**之后**新增一个可断言 `router-link` 的 `to` 的挂载器，并在文件末尾 `describe` 闭合前追加用例。之所以要自定义 stub，是因为 `router-link: true` 生成的自动桩不保证把 `to` 暴露成可读属性：

```ts
/** 需要断言返回链接 to 时用的挂载器：把 router-link 渲染成带 data-to 的 a */
function mountCockpitAt(path: string, id: string) {
  mocks.route.path = path
  mocks.route.params = { id }
  return mount(CourseCockpit, {
    global: {
      stubs: {
        'router-view': true,
        'router-link': { props: ['to'], template: '<a class="rl" :data-to="to"><slot /></a>' },
      },
    },
  })
}

describe('CourseCockpit 角色前缀推导（DR-48：同一壳服务 TEACHER / ADMIN）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseById.mockResolvedValue(COURSE_DETAIL)
    mocks.getStats.mockResolvedValue({ code: 200, data: { totalStudents: 15 } })
    mocks.getCourseClasses.mockResolvedValue([])
  })

  it('教师端前缀下返回链接指向 /teacher/courses', async () => {
    const wrapper = await mountCockpitAt('/teacher/courses/4001/overview', '4001')
    await flushPromises()
    expect(wrapper.get('a.rl').attributes('data-to')).toBe('/teacher/courses')
  })

  it('管理端前缀下返回链接与页签跳转都走 /admin，不串回教师端', async () => {
    const wrapper = await mountCockpitAt('/admin/courses/4007/chapters', '4007')
    await flushPromises()

    expect(wrapper.get('a.rl').attributes('data-to')).toBe('/admin/courses')

    const pills = wrapper.findAll('.tabs-pill .tab-i')
    expect(pills.map((n) => n.text())).toEqual(COURSE_TABS.TEACHER.map((t) => t.text))
    await pills[0].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/admin/courses/4007/overview')
  })
})
```

> 说明：此步**故意**断言"管理端页签集合仍是 `COURSE_TABS.TEACHER`"——那是本 Task 完成后的真实中间态（Task 3 才引入 `COURSE_TABS.ADMIN`），Task 3 Step 1 会把这条断言改为 ADMIN。分两步是为了每步都能独立跑红/跑绿。

- [ ] **Step 2: 运行测试确认失败**

```
cd frontend && npx vitest run src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts
```

预期：新 describe 的两例 FAIL —— 管理端那例 `data-to` 实际为 `/teacher/courses`（返回链接写死），且 `push` 收到 `/teacher/courses/4007/overview`。教师端那例 PASS（说明挂载器可用）。

- [ ] **Step 3: 推导角色并替换三处写死点**

`CourseCockpit.vue` 脚本里，在已有的 `const courseId = computed(() => route.params.id)`（`:124`）**之后**加两行推导：

```js
// 壳同时挂在 /teacher/courses/:id 与 /admin/courses/:id 下，角色由路径首段推导：
// 不新增 prop 是为了避免调用方（路由表）漏传，两处声明漂移。
const role = computed(() => (route.path.split('/')[1] === 'admin' ? 'ADMIN' : 'TEACHER'))
const rolePrefix = computed(() => `/${role.value.toLowerCase()}`)
```

模板 `:41` 的返回链接改为绑定：

```html
            <router-link :to="`${rolePrefix}/courses`" class="btn btn-ghost btn-sm" style="margin-left: auto"
              >← 返回课程列表</router-link
            >
```

脚本 `:165` 的页签来源与 `:169-171` 的跳转改为：

```js
const cockpitTabs = computed(() =>
  (COURSE_TABS[role.value] || COURSE_TABS.TEACHER).map((t) => ({ path: t.suffix, label: t.text })),
)
const activeCockpit = ref('chapters')

// Navigate to selected cockpit tab
watch(activeCockpit, (tab) => {
  router.push(`${rolePrefix.value}/courses/${courseId.value}/${tab}`)
})
```

`cockpitTabs` 变成 `computed` 后，其余三处消费点要同步加 `.value`（模板里的 `v-for="tab in cockpitTabs"` 由 Vue 自动解包，**不用改**）：

- `:178` 的 `if (cockpitTabs.some((t) => t.path === last))` → `if (cockpitTabs.value.some((t) => t.path === last))`
- `:187` 的 `const match = cockpitTabs.find((t) => t.path === last)` → `cockpitTabs.value.find(...)`

模板头部注释（文件第一行附近的说明块）补一句：`课程壳页：TEACHER / ADMIN 双角色消费，角色按 route 首段推导`。

- [ ] **Step 4: 运行测试确认通过**

```
cd frontend && npx vitest run src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts
```

预期：该套件全 PASS（原 10 例 + 新 2 例）。原「模块页签（DR-47）」两例仍应绿——它们跑的是默认 `mocks.route.path`（`/teacher/courses/4001/overview`），教师端行为未变。

- [ ] **Step 5: 全量前端测试，抓 `.value` 漏改**

```
cd frontend && npx vitest run
```

预期：全绿。若有 `cockpitTabs.some is not a function` 之类报错，说明某处 `.value` 漏改，回到 Step 3 补齐，不要改用非 computed 写法绕过。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/views/teacher/cockpit/CourseCockpit.vue frontend/src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts
git commit -m "refactor(nav): 课程壳页角色按路由首段推导，返回链接与页签跳转不再写死教师端前缀"
```

---

### Task 3: menus.ts 配置层（六治理维度 + ADMIN 页签 + 孤儿收编）

**Files:**
- Modify: `frontend/src/layouts/menus.ts:35-114`（`ADMIN_SECTIONS` 全量替换）、`:119-144`（`COURSE_TABS` 加 `ADMIN`）、`:147-150`（`COURSE_CATALOG_ENTRY` 加 `ADMIN`）
- Test: `frontend/src/layouts/__tests__/menus.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `/admin/courses/:id/<suffix>` 路由（8 个 suffix 必须与之逐字一致）
- Produces: `COURSE_TABS.ADMIN`（8 项）、`COURSE_CATALOG_ENTRY.ADMIN`、`ADMIN_SECTIONS`（6 分区 22 条路径）；Task 4 的 `AppSidebar` 组装与 Task 4 的组件测试都读这些

- [ ] **Step 1: 先把 ADMIN 加入页签一致性用例，并改写壳用例断言**

`frontend/src/layouts/__tests__/menus.test.ts` 的 `navPathsOf(role)`（`:34-42`）里删除 ADMIN 短路，让三端统一为"动态目录 + 静态区"：

```ts
function navPathsOf(role) {
  const staticSections =
    role === 'ADMIN' ? ADMIN_SECTIONS : role === 'TEACHER' ? TEACHER_SECTIONS : STUDENT_SECTIONS
  return [
    ...collectPaths(buildCourseCatalogSection(role, DEMO_COURSES)),
    ...collectPaths(staticSections),
  ]
}
```

`:58-64` 的页签用例把角色循环扩到 ADMIN：

```ts
    for (const role of ['TEACHER', 'STUDENT', 'ADMIN']) {
```

`Tasks 2 Step 1` 里那条"管理端页签仍是 COURSE_TABS.TEACHER"的中间态断言，本步改为终态断言（`CourseCockpit.test.ts` 同一提交内一起改，因为 `COURSE_TABS.ADMIN` 就是本 Task 引入的）：

```ts
    expect(pills.map((n) => n.text())).toEqual(COURSE_TABS.ADMIN.map((t) => t.text))
```

- [ ] **Step 2: 反转"管理员端维持扁平"断言，并补落点/孤儿/双写三条守护**

`menus.test.ts:93-99` 那条 `it('管理员端维持扁平治理视图，不引入课程维度', …)` **整条替换**为下面四条（保留文件末尾的 `})`）：

```ts
  it('管理员端治理大盘为静态区首节，课程维度经动态目录进入', () => {
    // 配置层不锁组装顺序（那由 AppSidebar 决定，见 AppSidebar.test 的分区标签顺序断言），
    // 这里只锁：静态区第一节必须是落点，且课程目录能为 ADMIN 构建出真实页签路径。
    expect(ADMIN_SECTIONS[0].items[0].path).toBe('/admin/dashboard')
    const paths = navPathsOf('ADMIN')
    expect(paths).toContain('/admin/dashboard')
    expect(paths).toContain('/admin/courses/1/overview')
    expect(paths).toContain('/admin/courses/1/ai')
    expect(paths).toContain('/admin/courses')
  })

  it('管理员端课程页签集合是治理子集，不含教师运营页签', () => {
    const suffixes = COURSE_TABS.ADMIN.map((t) => t.suffix)
    expect(suffixes).toEqual([
      'overview',
      'chapters',
      'bank',
      'exams',
      'grades',
      'analytics',
      'knowledge',
      'ai',
    ])
    for (const excluded of ['submissions', 'proctor', 'interaction', 'tasks', 'discussion']) {
      expect(suffixes).not.toContain(excluded)
    }
  })

  it('静态 ADMIN_SECTIONS 不出现「课程目录」，禁止静态/动态双写', () => {
    expect(ADMIN_SECTIONS.some((sec) => sec.label === '课程目录')).toBe(false)
    expect(ADMIN_SECTIONS).toHaveLength(6)
  })

  it('管理端每条治理叶子路由都有导航入口（孤儿收编后不得回退）', () => {
    const covered = new Set([
      ...navPathsOf('ADMIN'),
      '/admin/profile',
      '/admin/message-center',
      '/admin',
      '/admin/courses/:id',
    ])
    const adminLeaves = router
      .getRoutes()
      .filter((r) => r.path.startsWith('/admin/'))
      .filter((r) => !r.children.length)
      .map((r) => r.path)
    const orphans = adminLeaves.filter((p) => !covered.has(p) && !/^\/admin\/courses\/:id\//.test(p))
    expect(orphans, `侧边栏无入口的管理端路由：${orphans.join(', ')}`).toEqual([])
  })
```

- [ ] **Step 3: 跑测试确认失败（RED 必须来自配置未改，而非用例写错）**

```
cd frontend && npx vitest run src/layouts/__tests__/menus.test.ts src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts
```

预期：menus 里 4 条新用例 FAIL（`COURSE_TABS.ADMIN` 为 undefined / `ADMIN_SECTIONS` 仍是 4 分区 / `/admin/exams`+`/admin/grades` 被判孤儿）；`CourseCockpit.test.ts` 的 ADMIN 例 FAIL（`COURSE_TABS.ADMIN` undefined）。若出现 `is not iterable` 之类 TypeError，说明用例引用了不存在的导出，先核对 `menus.ts` 的导出名再往下走。

- [ ] **Step 4: 阴性对照（防止恒真断言）**

临时把下面两处各改一次、各跑一次，确认测试会点红，然后**改回**：

```
# ① 把 menus.ts 里 /admin/exams 那条 children 的 path 改成 /admin/exams-tmp
cd frontend && npx vitest run src/layouts/__tests__/menus.test.ts -t '管理端每条治理叶子路由'
# 预期 FAIL：孤儿清单同时出现 /admin/exams 与死链断言失败
# ② 把 AppSidebar 无关；改 menus.test.ts 的 covered 集合，手工加入 '/admin/exams' 字符串后重跑
# 预期 PASS —— 若仍 FAIL 说明用例判定逻辑无效，须修用例本身
```

两条都验完再进 Step 5，并在提交信息里带上"孤儿检查已做阴性对照"。

- [ ] **Step 5: 替换 `ADMIN_SECTIONS`（`menus.ts:35-114` 整段）**

按 Spec §4 的表逐条落。图标键名 `dashboard / users / lock / building / doc / clipboard / sliders / award / archive / eye / settings / layers / shield` 均已核实在 `icons.ts` 内存在且不重复：

```js
/* ============================== 管理员端分区（静态治理维度；课程目录见 buildCourseCatalogSection） ============================== */
// 角色边界（项目主 2026-10-09 给定矩阵）：管理员端是平台运维管控层，按治理维度组织，
// 不按"我教的课"组织。矩阵六维度中的「课程管理」由动态「课程目录」分区承担（本表不重复声明），
// 故静态区恰好 6 节；治理大盘必须排第一节，它是 /admin 空路径重定向的落点（router/index.ts:43）。
// 一级项每节 ≤3，超出者收进二级 children——与教师/学生端同口径。
export const ADMIN_SECTIONS = [
  {
    label: '治理总览',
    items: [{ key: 'dashboard', text: '治理大盘', icon: 'dashboard', path: '/admin/dashboard' }],
  },
  {
    label: '用户与班级',
    items: [
      { key: 'users', text: '用户与账号', icon: 'users', path: '/admin/users' },
      { key: 'roles', text: '角色与权限', icon: 'lock', path: '/admin/permissions' },
      { key: 'classes', text: '组织与班级', icon: 'building', path: '/admin/classes' },
    ],
  },
  {
    label: '题库与试卷',
    items: [
      { key: 'questions', text: '公共题库', icon: 'doc', path: '/admin/questions' },
      // /admin/exams 与 /admin/grades 是 2026-10-08 收敛时漏掉入口的孤儿路由，本轮收编
      { key: 'exams', text: '试卷与考试', icon: 'clipboard', path: '/admin/exams' },
    ],
  },
  {
    label: '数据统计',
    items: [
      { key: 'analytics', text: '跨课程分析', icon: 'sliders', path: '/admin/analytics' },
      { key: 'grades', text: '全校成绩汇总', icon: 'award', path: '/admin/grades' },
    ],
  },
  {
    label: '内容与资源治理',
    items: [
      {
        key: 'resources',
        text: '资源治理',
        icon: 'archive',
        children: [
          { key: 'resources-all', text: '资源库', path: '/admin/resources' },
          { key: 'resource-versions', text: '资源版本', path: '/admin/resource-versions' },
          { key: 'file-upload', text: '上传管理', path: '/admin/file-upload' },
        ],
      },
      {
        key: 'governance',
        text: '运行监管',
        icon: 'eye',
        children: [
          { key: 'gov-supervision', text: '教学监督', path: '/admin/supervision' },
          { key: 'gov-exam-gov', text: '考试治理', path: '/admin/exam-governance' },
          { key: 'gov-plagiarism', text: '查重管理', path: '/admin/plagiarism' },
        ],
      },
    ],
  },
  {
    label: '权限·系统·审计',
    items: [
      {
        key: 'system',
        text: '系统参数与公告',
        icon: 'settings',
        children: [
          { key: 'system-settings', text: '系统设置', path: '/admin/system' },
          { key: 'notices', text: '通知公告', path: '/admin/notifications' },
          { key: 'reminders', text: '提醒管理', path: '/admin/reminders' },
        ],
      },
      {
        key: 'ai-kb',
        text: 'AI 与知识库',
        icon: 'layers',
        children: [
          { key: 'ai-cfg', text: 'AI 供应商配置', path: '/admin/ai-supplier' },
          { key: 'kb', text: '知识库管理', path: '/admin/knowledge-base' },
          { key: 'rag', text: 'RAG 文档索引', path: '/admin/rag-docs' },
        ],
      },
      {
        key: 'audit',
        text: '安全与审计',
        icon: 'shield',
        children: [
          { key: 'logs', text: '操作日志', path: '/admin/logs' },
          { key: 'login-logs', text: '登录日志', path: '/admin/login-logs' },
          { key: 'user-sessions', text: '会话管理', path: '/admin/user-sessions' },
        ],
      },
    ],
  },
]
```

- [ ] **Step 6: 加 `COURSE_TABS.ADMIN` 与 `COURSE_CATALOG_ENTRY.ADMIN`**

在 `COURSE_TABS`（`:119-144`）的 `STUDENT` 数组**之后**加 `ADMIN` 键（顺序放在 STUDENT 后即可，键名大写）：

```js
  // 管理端每门课下的治理页签：均为「查看平台全部课程」语义，读路径已逐条核实 ADMIN 可访
  // （spec §5 取证表）。刻意不含 tasks/submissions/proctor/interaction/knowledge-management/discussion。
  ADMIN: [
    { suffix: 'overview', text: '课程概览' },
    { suffix: 'chapters', text: '章节与任务点' },
    { suffix: 'bank', text: '课程题库' },
    { suffix: 'exams', text: '试卷与考试' },
    { suffix: 'grades', text: '成绩统计' },
    { suffix: 'analytics', text: '学情统计' },
    { suffix: 'knowledge', text: '知识图谱' },
    { suffix: 'ai', text: 'AI 教学助手' },
  ],
```

在 `COURSE_CATALOG_ENTRY`（`:147-150`）内加：

```js
  ADMIN: { allText: '课程治理总览', allPath: '/admin/courses' },
```

同时把 `buildCourseCatalogSection` 的 JSDoc（`:152-156`）`@param role` 从 `TEACHER / STUDENT` 改为 `TEACHER / STUDENT / ADMIN`。

- [ ] **Step 7: 跑测试确认通过**

```
cd frontend && npx vitest run src/layouts/__tests__/menus.test.ts src/layouts/__tests__/../../../router/__tests__/nav-reachability.test.ts src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts
```

预期：三个套件全绿。`nav-reachability.test.ts` 的「课程目录三级页签全部命中真实路由」会自动把 ADMIN 纳入检查（它遍历 `Object.entries(COURSE_TABS)`，`:82`），无需改该文件。

- [ ] **Step 8: 提交**

```bash
git add frontend/src/layouts/menus.ts frontend/src/layouts/__tests__/menus.test.ts frontend/src/views/teacher/cockpit/__tests__/CourseCockpit.test.ts
git commit -m "feat(nav): 管理端导航按六治理维度重排并补 ADMIN 课程页签，收编 exams/grades 孤儿路由（孤儿检查含阴性对照）"
```

---

### Task 4: AppSidebar 放开 ADMIN 动态课程目录

**Files:**
- Modify: `frontend/src/layouts/components/AppSidebar.vue:125-137`（`loadCourses`）、`:141-145`（`navSections`）、`:186`（自动展开正则）
- Test: `frontend/src/layouts/components/__tests__/AppSidebar.test.ts`

**Interfaces:**
- Consumes: Task 3 的 `ADMIN_SECTIONS`（6 分区）+ `COURSE_TABS.ADMIN` + `COURSE_CATALOG_ENTRY.ADMIN`；`courseApi.getCourseList`
- Produces: 管理端渲染出的四级结构（分区标签「治理总览」→「课程目录」→各课程→8 页签），以及 `router.push('/admin/courses/<id>/<suffix>')` 的真实跳转行为

- [ ] **Step 1: 改写既有 ADMIN 用例并补三条新用例**

`AppSidebar.test.ts` 顶部 mock（`:21-23`）加 `getCourseList`，并在 `mocks` 里登记：

```ts
const mocks = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockRoute: { path: '/' },
  teacherCourses: vi.fn(),
  studentCourses: vi.fn(),
  adminCourses: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  default: { getMyCourses: mocks.teacherCourses, getCourseList: mocks.adminCourses },
}))
```

`beforeEach`（`:56-60`）里补一行——**用 `list` 而不是 `records`**，与后端实测信封一致（本机 `GET /api/courses` 返回 `data.list`）：

```ts
    mocks.adminCourses.mockResolvedValue({ data: { list: [COURSE] } })
```

把 `:101-109` 那条「管理员端：扁平治理视图，不构建课程目录也不拉取课程列表」**整条替换**：

```ts
  it('管理员端：拉全库课程列表构建课程目录，治理大盘仍是第一分区', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/dashboard')

    expect(mocks.adminCourses).toHaveBeenCalledWith({ page: 1, size: 200 })
    expect(mocks.teacherCourses).not.toHaveBeenCalled()
    expect(mocks.studentCourses).not.toHaveBeenCalled()

    const labels = wrapper.findAll('.nav-section-label').map((n) => n.text())
    expect(labels[0]).toBe('治理总览')
    expect(labels[1]).toBe('课程目录')

    // 静态区一级项按新文案渲染，点击真实跳转
    await navItem(wrapper, '用户与账号').trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledWith('/admin/users')
  })

  it('管理员端：课程目录三级页签跳到 /admin 前缀的子路由', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/courses/1/grades')
    await navLeaf(wrapper, '成绩统计').trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledWith('/admin/courses/1/grades')
  })

  it('管理员端：位于课程子页时课程目录与课程节点自动展开', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/courses/1/bank')
    const catalog = navItem(wrapper, '课程目录')
    expect(catalog.classes()).toContain('active')
    expect(catalog.classes()).toContain('expanded')
    // 三级页签已渲染，说明课程节点也处于展开态
    expect(navLeaf(wrapper, '章节与任务点').exists()).toBe(true)
  })

  it('管理员端：课程治理总览入口精确高亮，不被课程子页误亮', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/courses/1/overview')
    const all = wrapper
      .findAll('.nav-sub-item')
      .find((n) => n.find('.nav-sub-txt').text().trim() === '课程治理总览')
    expect(all, '应渲染「课程治理总览」入口').toBeTruthy()
    expect(all!.classes()).not.toContain('active')
  })
```

- [ ] **Step 2: 运行测试确认失败**

```
cd frontend && npx vitest run src/layouts/components/__tests__/AppSidebar.test.ts
```

预期：4 条新用例 FAIL —— `adminCourses` 未被调用（短路仍在）、`.nav-section-label` 首位是「治理总览」但次位不是「课程目录」、`navLeaf` 找不到页签、自动展开未触发（正则没有 `admin`）。

- [ ] **Step 3: 放开 ADMIN 的三处短路**

`loadCourses`（`:125-137`）改为按角色选 API：

```js
async function loadCourses() {
  try {
    const res =
      props.role === 'ADMIN'
        ? // 管理员治理的是全库课程，取 /courses 而非 /courses/my（后者 hasRole('TEACHER')，ADMIN 必 403）
          await courseApi.getCourseList({ page: 1, size: 200 })
        : props.role === 'TEACHER'
          ? await courseApi.getMyCourses({ page: 1, size: 200 })
          : await studentApi.getMyCourses({ page: 1, size: 200 })
    const body = unwrap(res) || {}
    courses.value = body.records || body.list || (Array.isArray(body) ? body : [])
  } catch (e) {
    console.warn('[sidebar] 课程列表加载失败', e)
  }
}
```

（`catch (e)` 与日志参数保持原样，本 Task 不改错误处理行为；只动"取哪个 API"和"怎么拼分区"。）

`navSections`（`:141-145`）改为三段组装：

```js
const navSections = computed(() => {
  const base = STATIC_MAP[props.role] || STUDENT_SECTIONS
  if (props.role === 'ADMIN') {
    // 治理大盘是 /admin 空路径的落点，必须保持第一分区；课程目录（动态）紧随其后
    return [
      base[0],
      ...buildCourseCatalogSection('ADMIN', courses.value),
      ...base.slice(1),
    ]
  }
  return [...buildCourseCatalogSection(props.role, courses.value), ...base]
})
```

自动展开正则（`:186`）加 `admin`：

```js
    const m = p.match(/^\/(?:admin|teacher|student)\/courses\/(\d+)/)
```

组件头部 JSDoc（`:100-105`）里"按当前登录者的授课/选课列表动态构建"改为"按当前登录者的授课/选课/全库课程列表动态构建（ADMIN 取全库）"。

- [ ] **Step 4: 运行测试确认通过**

```
cd frontend && npx vitest run src/layouts/components/__tests__/AppSidebar.test.ts
```

预期：该套件全 PASS。若「课程治理总览」精确高亮那条失败，检查 `isActive` 的 `course-catalog-all` 分支（`:154`）是否被误改——本轮不该动它。

- [ ] **Step 5: 全量门禁（lint / typecheck / vitest）**

```
cd frontend && npm run lint && npm run typecheck && npx vitest run
```

预期：lint `0 error / 0 warning`、exit 0；`vue-tsc --noEmit` 不新增类型错误（基线核对：`npm run typecheck:gate`）；vitest 全绿。任何一项失败都要在本 Task 内修掉再提交，不留"已知失败"。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/layouts/components/AppSidebar.vue frontend/src/layouts/components/__tests__/AppSidebar.test.ts
git commit -m "feat(nav): 管理端侧边栏放开动态课程目录，治理大盘置顶并修自动展开正则"
```

---

### Task 5: 真跑验证 + 手册回写（收口）

**Files:**
- Modify: `docs/项目手册.md`（§4 导航信息架构 `:85-92`、D.12.3 表尾 `:2332` 之后、D.12.4 首条 `:2336`）
- Modify（仅在真跑发现问题时）: Task 1–4 涉及的前端文件

**Interfaces:**
- Consumes: Task 1–4 全部产物 + 本机在跑的 MySQL/Redis/后端 8080/引擎 8001·8002/Vite 5174
- Produces: 手册 §4/D.12.3/D.12.4 的定稿文字与本轮最终门禁读数；收尾报告所需证据清单

- [ ] **Step 1: 核对服务在跑（不新装任何东西）**

```bash
# Vite 只绑 [::1]，必须用 localhost 而不是 127.0.0.1（本机实测 127.0.0.1 返回 000）
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:5174/
# 后端探针用 /probe，不用综合 /health（DR-28：缺 AI Key 时综合 health 恒 503 属正常）
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/actuator/health/probe
```

预期：分别 `200`、`200`。任一非 200 → 按手册 §18.5 的顺序拉起（Vite 用 PowerShell `Start-Process` 脱离，**不要用 Qoder 后台 Bash 任务**，会连带杀掉前端/引擎进程）。若需要重启后端（项目主此前未授权杀 PID 39608），先问，不要自行杀进程。

- [ ] **Step 2: 浏览器真跑管理端（外部 Chrome，5174）**

用 browser-use 连接项目主的 Chrome，`admin / 123456` 登录（**连打约 7~9 次登录会触发 429**，每次尝试之间留间隔；429 不是密码错）。逐步取证，每步截图并读 `list_console_messages`：

1. 落地 `/admin/dashboard`，侧边栏分区顺序应为：治理总览 → 课程目录 → 用户与班级 → 题库与试卷 → 数据统计 → 内容与资源治理 → 权限·系统·审计（共 7 节）。
2. 展开「课程目录」→「课程治理总览」跳 `/admin/courses`，页面是既有 `CourseManagement.vue`（含待审核卡与 `approveCourse` 行内动作），未被课程壳吞掉。
3. 目录里课程节点应有 38 条（本机实测全量）。展开课程 4007，逐个点 **8 个页签**，每个都要求：URL 落 `/admin/courses/4007/<suffix>`、页面非空白、console 无 error、`list_network_requests` 无 4xx 信封。
4. 再挑一门 `status=INACTIVE` 的课，同样点 `overview`/`chapters`/`grades` 三页，确认停用课程不炸。
5. **裸 URL 冷启动**：地址栏直接输入 `http://localhost:5174/admin/courses/4007` → 必须落 `/admin/courses/4007/overview`（DR-43 同型验证）。
6. 头部返回链接点击后落 `/admin/courses`，不是 `/teacher/courses`。
7. **写控件知情验证（只读操作，不提交）**：进入 `/admin/courses/4007/chapters`，确认「新建/编辑章节」按钮对管理员可见（Spec §5 末口径），**不要真的点保存**——本轮只登记可见性事实。

预期：以上 7 条全部符合。任一条不符 → 就地修（改哪个文件回到对应 Task 的步骤口径），修完从 Step 3 重跑，并把最终读数写进 Step 5 的 DR-48。

- [ ] **Step 3: 教师端与学生端回归（壳参数化的主要风险）**

教师端 `ts04 / 123456` 登录：从 `/teacher/dashboard` 点课程卡片 → 落 `/teacher/courses/4001/overview`；壳内页签栏仍是 14 项且点击逐条跳 `/teacher/courses/4001/<suffix>`；头部返回链接落 `/teacher/courses`。学生端 `st001 / 123456` 登录：课程目录三级页签与三个跨课程入口均可点，无新增 console error。

预期：两端行为与改版前一致（本轮未动教师/学生配置，异常即回归）。

- [ ] **Step 4: 六门禁复跑（改动后必须重跑，不沿用 Step 前的读数）**

```
cd frontend && npm run lint && npm run typecheck && npx vitest run && npm run build
```

记录：lint 告警数（应 0/0）、vitest 文件数/用例数/全绿、build exit 0。后端本轮无改动，**不跑 `mvn verify`**，收尾报告须如实写"后端未验证是因为未改动"。

- [ ] **Step 5: 手册回写 §4（`:85-92`）**

把 `:91` 整条（现文为"**管理员端是扁平治理视图，与师生端的课程目录取向有意不同**……跳过课程拉取。"）替换为：

```markdown
- **管理员端是「治理大盘 + 动态课程目录 + 六治理维度」结构（2026-10-09 DR-48 改版）**：角色边界按项目主给定的矩阵定为**平台运维管控层**（管整个平台、所有课程、全部用户），与教师端的**课程业务操作层**（只管自己这门课）区分。静态区 6 节对应矩阵余下五维度（用户&班级、题库&试卷、数据统计、内容&资源治理、权限·系统·审计），其中矩阵的「课程管理」维度由**动态「课程目录」分区**承担——`AppSidebar.vue` 对 `ADMIN` 用 `courseApi.getCourseList({page:1,size:200})` 取全库课程（本机 38 门）后经 `buildCourseCatalogSection('ADMIN', courses)` 构建，每门课下挂 8 个治理页签（概览/章节任务点/课程题库/试卷与考试/成绩统计/学情统计/知识图谱/AI 教学助手）。**刻意不用 `getMyCourses`**（`CourseController.java:178` 为 `hasRole('TEACHER')`，管理员必 403）。治理大盘排在第一分区，因为它是 `/admin` 空路径重定向的落点（`router/index.ts:43`）。课程页签复用 `views/teacher/cockpit/*` 与 `tools/{KnowledgeGraph,AICenter}.vue`，壳按 `route.path` 首段推导角色，**后端零改动**。⚠️ 复用即连带复用页面的写控件：`CourseChapterController.java:83/98/114/128` 等写端点是 `hasAnyRole('TEACHER','ADMIN')` 且无课程归属断言，管理员经页签可真实写入他人课程——该口径经项目主 2026-10-09 知情接受，本轮不做前端只读态，取证与三条退路见 spec §5 末。矩阵中四项能力（创建学期、归档过期学期、内容审核、限制教师出题权限）在全仓**无实现落点**，按"不发明死链"纪律不进导航，登记在 D.12.4。
```

同段 `:87` 有两处需一并更正（**仅在"口径 A"即 DR-45/46/47 那批 WIP 已落盘时成立**：`proctor` 页签是该批未提交新增，HEAD 里教师端仍是 13 项，手册并未写错）：教师端页签数 **13 → 14**（原文漏列「在线监考」），并在句尾补"管理员 8 个"。若按"口径 B"从 HEAD 起做，则**只补"管理员 8 个"，不动 13 这个数字**。`:92` 的测试守护计数按 Step 4 的 vitest 真实读数更新。

- [ ] **Step 6: D.12.3 加 DR-48 行，并修正编号说明**

`:2303` 的编号说明段末尾追加：

```markdown
> ⚠️ 2026-10-09 补充：代码里已存在 **DR-44 / DR-45 / DR-46 / DR-47** 的引用（如 `JwtUtils.java:75`、`request.ts:387`、`CourseCockpit.vue:163-164` 的 DR-47 注释），但本表当时止于 DR-43，即这四条**只进了代码与提交信息、未登记进本表**。为避免撞号，本轮新项**从 DR-48 起编**；DR-44–47 的补登记留待单独一轮文档批次，不在本轮夹带。
```

在 `:2332`（DR-43 行）之后追加一行表格（读数用 Step 4 的真实值填，**不得沿用**本计划里的预估数）：

```markdown
| DR-48 | **管理端导航与角色边界不符 + 两条治理路由无入口**：`ADMIN_SECTIONS` 曾是 4 分区扁平视图，① 「数据看板」被埋在第三个分区，而它是 `/admin` 空路径重定向的落点（`router/index.ts:43`），登录后第一眼看不到落点；② `/admin/exams`、`/admin/grades` 页面与路由俱在却**侧边栏无入口**（D.12.4 当时记为 13 条孤儿里的管理端 2 条），既有 `menus.test.ts` 只断言"导航里的路径可达"，**不检查"路由是否有入口"**，故这类缺口结构上不可能被抓到；③ 「系统管理」与「系统配置」近义分区并存（后者平铺 8 项，与教师/学生端"每组 ≤3 一级项"口径冲突），`shield` 图标重复用于两个不同一级项 | 🟡 | ✅ **已闭合（2026-10-09）**：按项目主给定的角色边界矩阵（管理员端=平台运维管控层）重构为「治理大盘 + 动态课程目录 + 六治理维度」7 分区；`COURSE_TABS.ADMIN` 定 8 个治理页签（`ai` 按项目主要求保留），`/admin/courses/:id/*` 壳路由复用教师端视图且**后端零改动**（逐页签核到方法级/`SecurityConfig` 级 ADMIN 可达性）；收编 2 条孤儿；图标去重（14 个键全部取自 `icons.ts` 现有 51 键）。反转 `menus.test.ts` 里"管理员端维持扁平"的旧定案断言（352549b 钉住），新增**反向覆盖检查**（每条 `/admin/**` 叶子路由必须有入口）并做**阴性对照**；`AppSidebar.vue` 三处 ADMIN 短路放开（`:126` 取数、`:143` 三段组装保治理大盘第一、`:186` 自动展开正则补 `admin`）；`CourseCockpit.vue` 的角色前缀改按 `route.path` 首段推导。⚠️ 两条边界：① 复用教师端页面带来写控件可点击面，`CourseChapterController` 等写端点无课程归属断言，项目主知情接受，本轮不做只读态；② 跨课程 AI 统计**未做**（无数据源，取证见 spec §9），且现成 `GET /admin/statistics/ai-usage` 返回的是 `todayLogins*3` 编造值（`SysMonitorComponent.java:136/139/143`），**任何新 AI 统计页不得接它** | 设计件 `docs/superpowers/specs/2026-10-09-admin-course-catalog-nav-design.md`；门禁读数：ESLint 0 error/0 warning、`vue-tsc` 无新增错误、vitest **__N_FILES__ 文件 / __N_CASES__ 例全绿**（以 Step 4 实际输出填写）、`npm run build` exit 0；真跑取证（外部 Chrome `:5174`，`admin/123456`）：7 分区顺序、38 门课程入目录、课程 4007 八页签逐条点击无 4xx/无空白页、裸 URL 冷启动落 `overview`、教师端 14 页签与学生端跨课入口回归无退化（截图 + console/network 读数为当日实测，非静态检查） |
```

- [ ] **Step 7: D.12.4 首条孤儿计数更正**

`:2336` 那条"共 13 条"改为 **11 条**，并把其中"管理员端 2 条（`/admin/exams` 考试管理、`/admin/grades` 成绩管理，视图均在，仅侧边栏无项）"整段替换为：

```markdown
管理员端 2 条已于 **2026-10-09（DR-48）** 收编进「题库与试卷 / 数据统计」分区，孤儿检查已由 `menus.test.ts` 的反向覆盖用例钉住（含阴性对照），剩余缺口为学生端 1 条与教师端 10 条
```

同条末尾追加本轮新增的未闭合项：

```markdown
- **角色矩阵中四项管理能力无实现落点（2026-10-09 取证，刻意不进导航防死链）**：① 创建学期、② 归档过期学期、③ 内容审核（讨论区评论 / 平台全部资源审核 + 违规处理）、④ 限制教师出题权限。取证：`grep -iE "semester|archive|moderat|comment" frontend/src/router/index.ts` 零命中；§18.5 相邻的导航纪律禁止先放入口后补页面。**跨课程 AI 统计**同属此类——`ai_chat_sessions` 无 `course_id` 列且 `AIController.java:99` 在入口丢弃已收到的 `courseId`，全仓无 `AiCallLog`/`prompt_tokens` 落库，五步前置与"历史不可回填"的代价见 spec §9。
```

- [ ] **Step 8: 核对文档内所有行号引用未失效**

```bash
grep -n "CourseCockpit.vue:150" docs/项目手册.md
grep -n "router/index.ts:43" docs/项目手册.md
```

D.12.4 里"由 `CourseCockpit.vue:150` 的课程内页签栏承载"这条引用会因 Task 2 增删行而漂移。用 `grep -n "tabs-pill" frontend/src/views/teacher/cockpit/CourseCockpit.vue` 取真实新行号后改写该处数字；`router/index.ts:43`（`/admin` 空路径重定向）因 Task 1 在 43 行之后插入代码而未漂移，确认即可。

- [ ] **Step 9: 提交（手册 + 验证期间的任何修正同一提交）**

```bash
git add docs/项目手册.md
git status --short
git commit -m "docs(手册): §4 改写管理端导航为六治理维度+动态课程目录，登记 DR-48 并更正孤儿计数"
```

`git status --short` 必须只列出已预期的文件；若队友文件出现在改动列表里，停下核对，不要提交他人改动、不要删除。

- [ ] **Step 10: 收尾报告（只报事实，不做无证据声明）**

按顺序给出：七分区最终形状与 8 页签清单、真跑取证结论（哪些步骤看过、哪些没看）、四项门禁读数（贴命令输出关键行）、`mvn verify` **未跑及原因**（后端零改动）、仍未知项（写控件可见性只验到"按钮存在"，未验"点击真写入"——按口径不验，如项目主需要须另行授权且会产生真实数据变更）、跨课程 AI 统计仍为未做。

---

## 自检对账（计划写完立刻核过）

- Spec §4 分区表 → Task 3 Step 5；§5 目录与页签 → Task 3 Step 6 + Task 4 Step 3；§6 路由与壳参数化 → Task 1 Step 3 + Task 2 Step 3；§7 测试计划 6 条 → Task 1 Step 1（壳重定向/列表页并存）、Task 2 Step 1 + Task 3 Step 1（壳双角色）、Task 3 Step 2–4（反转断言/子集/双写/孤儿+阴性对照）、Task 4 Step 1（组件层四条）；§7 真跑 → Task 5 Step 1–4；§8 文档回写 → Task 5 Step 5–8；§9 AI 统计（本轮不做）→ Task 5 Step 7 落进 D.12.4；§10 三条风险 → Task 2（教师端回归 = Task 5 Step 3）、Task 5 Step 2.3（38 门课观感）、Task 1 Step 1（列表页并存用例）。
- 计划内无 TBD/占位；唯一待填的是 Step 4 才能得到的测试计数，已显式写成 `__N_FILES__ / __N_CASES__` 并要求用实际输出替换，禁止预估数。
- 名称一致性：`COURSE_TABS.ADMIN`（8 suffix）↔ Task 1 路由的 8 条子路径 ↔ Task 3 Step 2 用例数组，三处逐字相同；`getCourseList` ↔ mock 的 `adminCourses`；`rolePrefix`/`backPath` 统一为 `rolePrefix`（计划中不存在第二个名字）。
