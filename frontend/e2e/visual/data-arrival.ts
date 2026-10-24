import type { Page } from '@playwright/test'

/* ============================================================================ *
 * 每个锚点自己的"数据确已到位"断言（spec §6.14）
 * ============================================================================ *
 * 【为什么通用守卫不够】Task 4b 记过一次真实事故：后端瞬时失败把 teacher-dashboard 渲染成
 * 空态，而 `.error-boundary` 计数 0、`#app` 非空、URL 没被改写、data-theme 也对——四条全过。
 * 空态是一张"长得很正常"的图，只有看**这页该有的东西在不在**才拦得住。
 * 【为什么它们必须一次读全表】所有判定都基于同一次 evaluate 的快照：
 * 断言之间不能再有等待/推进，否则"到位"的时点与截图的时点不是同一帧，读数会自相矛盾。
 * 【为什么必须是抛错而不是 expect】这套检查同时被 anchors 与 sensitivity 共用（见 assertComparableRender），
 * 探针那边要的是"立刻可归因的失败"，polling 版 expect 会把 20s 超时伪装成量程结论。
 */

interface ArrivalSnapshot {
  path: string
  l1Card: number
  statValues: string[]
  treeRows: number
  tableRows: number
  canvas: number
  formItems: number
  filledInputs: number
  heroStats: string[]
  shellTabs: number
  headings: string[]
  /** admin-profile：/api/courses 供给的课程行（ProfileCourses .course-item） */
  courseItems: number
  /** student-learning-center：/api/student/courses/:id/learning-center 供给的任务点总数 */
  lcTaskTotal: number
}

const hasNonZeroDigit = (s: string): boolean => /[1-9]/.test(s)

async function readArrival(page: Page): Promise<ArrivalSnapshot> {
  return page.evaluate(() => {
    const q = (sel: string) => document.querySelectorAll(sel).length
    const txt = (sel: string) =>
      Array.from(document.querySelectorAll(sel)).map((e) => (e.textContent ?? '').trim())
    const filled = Array.from(document.querySelectorAll('input, textarea')).filter(
      (el) => ((el as HTMLInputElement).value ?? '').trim() !== '',
    ).length
    return {
      path: location.pathname,
      l1Card: q('.l1-card'),
      statValues: txt('.s-value'),
      treeRows: q('.tree-row'),
      tableRows: q('.el-table__row'),
      canvas: q('canvas'),
      formItems: q('.el-form-item'),
      filledInputs: filled,
      heroStats: txt('.hero-stat__value'),
      shellTabs: q('.shell-tab'),
      headings: txt('h1, h2, h3'),
      courseItems: q('.course-item'),
      lcTaskTotal: Number(txt('.lc-progress-num .total')[0] ?? 0) || 0,
    }
  })
}

export interface ArrivalCheck {
  /** 这条断言在验什么——直接进失败消息，别让读红的人去猜选择器 */
  what: string
  run: (page: Page) => Promise<void>
}

/** 至少一个统计值带非零数字（全 0 / 全空 = 后端没给数，正是那次事故的样子） */
function requireSomeStat(s: ArrivalSnapshot, where: string): void {
  if (s.statValues.length === 0) throw new Error(`${where}：一个 .s-value 都没有——统计卡没渲染`)
  if (!s.statValues.some(hasNonZeroDigit)) {
    throw new Error(
      `${where}：.s-value 全部为 0/空/占位 ${JSON.stringify(s.statValues)}——数据没到位，不得当基线`,
    )
  }
}

export const DATA_ARRIVAL_CHECKS: Record<string, ArrivalCheck> = {
  login: {
    what: '登录表单两个字段真渲染（本页无数据接口，钉的是页面身份不是数据）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.formItems < 2)
        throw new Error(`登录页只渲染出 ${s.formItems} 个 .el-form-item（要 ≥2）`)
    },
  },
  'admin-dashboard': {
    what: '管理工作台 ≥1 张 .l1-card 且统计值里有非零数',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.l1Card < 1) throw new Error(`管理工作台 .l1-card = ${s.l1Card}（要 ≥1）`)
      requireSomeStat(s, '管理工作台')
    },
  },
  'student-dashboard': {
    what:
      '学生工作台 ≥1 张 .l1-card、统计值有非零数、且图表 canvas 真存在——三条都是**存在性**断言：' +
      '被钉的分析载荷全 0（R-CF1，见 PIN_PAYLOAD_TRIVIAL_EXCUSED 与 LEDGER P0a-GUARD-02），被钉区域**不是**数据到位的证据',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.l1Card < 1) throw new Error(`学生工作台 .l1-card = ${s.l1Card}（要 ≥1）`)
      requireSomeStat(s, '学生工作台')
      if (s.canvas < 1)
        throw new Error(
          '学生工作台的图表 canvas 没渲染——日期载荷钉死后画面不再来自后端，这一条是唯一还能看出"图没画"的判据（它只证"画了"，不证"画的是有内容的数"）',
        )
    },
  },
  'teacher-dashboard': {
    what: '教师工作台 ≥1 张 .l1-card 且统计值里有非零数（**就是那次事故的那一页**）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.l1Card < 1)
        throw new Error(
          `教师工作台 .l1-card = ${s.l1Card}（要 ≥1）——空态页面正是这样：非空壳、无错误边界、断言全绿`,
        )
      requireSomeStat(s, '教师工作台')
    },
  },
  'admin-courses-table': {
    what: '课程表格真有新行（.el-table__row ≥1）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.tableRows < 1)
        throw new Error(
          `课程表格行数 = ${s.tableRows}（要 ≥1）——空表基线会把"数据没了"判成"改版了"`,
        )
      requireSomeStat(s, '课程管理')
    },
  },
  'admin-system-form': {
    // ⚠️ 这一页的画面**不随接口变**（实测：把 /api/system-configs 五个端点全打空，
    // #app 节点数 1909→1909、有值输入框 26→26 一字不变——表单渲染的是组件内默认值）。
    // 所以这条只能是**结构性**断言（表单确实挂载），它证伪的方式是"拿错页跑"（见 anchors.spec.ts 矩阵 B），
    // 不是"接口打空"。这条限制如实报上去，不假装它是数据到位断言。
    // 阈值取 ≥10 条目 / ≥1 开关 / ≥5 已填输入框：实测本页 35/8/26，而登录页只有 2/0/≤2，
    // 三个数都取在中段，既不会因为表单加减字段就假炸，也不会让别的页面混过去。
    what: '系统设置的表单确实挂载（≥10 个字段、≥1 个开关、≥5 个已回填输入框）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.formItems < 10 || s.filledInputs < 5) {
        throw new Error(
          `系统设置不像系统设置：.el-form-item=${s.formItems}（要 ≥10）、已回填输入框=${s.filledInputs}（要 ≥5）`,
        )
      }
      const switches = await page.evaluate(() => document.querySelectorAll('.el-switch').length)
      if (switches < 1)
        throw new Error(`系统设置没有 .el-switch（实测 ${switches}）——设置面板没挂载`)
    },
  },
  'student-courses-modal': {
    what: '课程大厅真列出课程卡（.tree-row ≥1）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.treeRows < 1) throw new Error(`课程大厅 .tree-row = ${s.treeRows}（要 ≥1）`)
    },
  },
  'student-learning-center': {
    what: '学习中心外壳真挂上（.shell-tab ≥1 且有课程名标题）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.shellTabs < 1)
        throw new Error(`学习中心 .shell-tab = ${s.shellTabs}（要 ≥1）——壳页没挂载就是白屏/空态`)
      if (s.headings.every((h) => h === ''))
        throw new Error('学习中心没有任何非空标题：课程名没到位')
    },
  },
  'student-knowledge-graph': {
    what: '图谱页统计卡非零（真画出内容由 anchors.spec.ts 的 assertGraphPainted 另三道判据把关）',
    run: async (page) => {
      const s = await readArrival(page)
      requireSomeStat(s, '知识图谱')
    },
  },
  'admin-governance-analytics': {
    what:
      '分析页 ≥1 个 canvas 且统计值有非零数——存在性断言；被钉的 growth 载荷全 0 ⇒ 被钉区域不是数据到位证据（R-CF1，' +
      'canvas ≥ 1 这类检查不得再被称作"数据到位"）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.canvas < 1)
        throw new Error(
          '分析页没有 canvas——图表整块没渲染（注意：它只证"画了"，不证"画的是有内容的数"）',
        )
      requireSomeStat(s, '治理分析')
    },
  },
  'admin-profile': {
    what: '个人中心真回填了资料（有值的 input/textarea ≥1 且 hero 统计非空）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.filledInputs < 1) throw new Error('个人中心的资料字段全空——用户数据没到位')
      if (!s.heroStats.some((v) => v !== '' && v !== '0'))
        throw new Error(`个人中心 hero 统计为空/零 ${JSON.stringify(s.heroStats)}`)
    },
  },
  'error-404': {
    what: '错误页真的是 404（钉的是页面身份：换成正则路由它就红）',
    run: async (page) => {
      const s = await readArrival(page)
      if (!s.headings.some((h) => h.includes('404')))
        throw new Error(`404 页标题不是 404：${JSON.stringify(s.headings)}（path=${s.path}）`)
    },
  },
}

export async function assertAnchorArrival(page: Page, slug: string): Promise<void> {
  const check = DATA_ARRIVAL_CHECKS[slug]
  if (!check)
    throw new Error(
      `[数据到位] 锚点 ${slug} 没有专属断言——新锚点必须先加一条，通用守卫拦不住空态（spec §6.14）`,
    )
  try {
    await check.run(page)
  } catch (e) {
    throw new Error(`[数据到位] ${slug} 未通过：${check.what}\n${(e as Error).message}`)
  }
}

/**
 * 每个锚点"这一页自己的数据端点"前缀，供 anchors.spec.ts 的空态可证伪矩阵打桩用。
 * 名单不是猜的：逐个锚点挂 response 监听跑一遍真实导航量出来的（GET /api/** 全集里
 * 去掉外壳类 /api/auth/permissions 与 /api/notifications/unread-count）。
 * 为什么要按锚点收窄而不是一把梭空掉整个 /api：
 * 实测发现把 /api/auth/permissions 之类也打空时，暗色/浅色锚点会连 `data-theme` 都拿不到，
 * 于是红的是**通用守卫**而不是本页的专属断言——那种红证明不了专属断言堵得住这个洞。
 * 'ALL' = 这一页没有可辨识的独立数据端点（学习中心走的是各模块端点），打全部非外壳 GET。
 */
export const DATA_PATHS: Record<string, string[]> = {
  'admin-dashboard': ['/api/admin/statistics', '/api/analytics/dashboard'],
  'student-dashboard': [
    '/api/student/analytics/visualization',
    '/api/student/courses',
    '/api/student/dashboard',
  ],
  'teacher-dashboard': ['/api/courses/my', '/api/teacher/dashboard/v2'],
  'admin-courses-table': ['/api/courses'],
  'admin-system-form': ['/api/system-configs'],
  'student-courses-modal': ['/api/student/courses', '/api/student/dashboard'],
  'student-learning-center': ['ALL'],
  'student-knowledge-graph': ['/api/knowledge', '/api/student/courses'],
  'admin-governance-analytics': [
    '/api/analytics/users/growth',
    '/api/analytics/heatmap/platform',
    '/api/analytics/dashboard',
    '/api/analytics/courses/ranking',
  ],
  'admin-profile': ['ALL'],
}

/** 外壳与会话类端点：把它们也打空会得到 /login 重定向或主题丢失，红得无法归因 */
export const SHELL_KEEP_ALIVE_PATHS = ['/api/auth/', '/api/notifications/']

/** 空态桩的**接管面**判据本体（R-C2-1／4c-fix-C2b 的 S1）。纯函数 ⇒ "它会不会吃掉源码模块"
 *  能像 F5／F6 那样入库断言，不必等一次真事故来证明（helpers.spec.ts 那条守卫喂候选清单）。
 * 【为什么要有这个名字，而不是把判据写在回调里】`page.route` 的第一个参数是 **URL 通配串**，
 *  它的语义是"URL 里含某一段"而不是"pathname 以某一段开头"。dev server 的**源码模块请求**
 *  `/src/api/auth.ts`（以及预打包产物 `/node_modules/.vite/deps/api.js`）都含 `/api/` 那一段，
 *  于是 `'ALL'` 那一类不做前缀收窄的锚点会把**一个 JS 模块**也 fulfill 成一段 JSON ——
 *  应用在自己的模块图上就断了，剩下的白屏、主题丢失、专属断言红全是下游后果。
 *  非 `'ALL'` 锚点当时不受影响（它们要求 pathname 以 `/api/…` 开头），这正是"只有两格红"的原因。
 * 【判据（不许放宽成"看起来像 API 就行"）】三段按序，任何一段不满足就**不接管**：
 *  ① `pathname` 必须以 `/api/` **开头**（前缀，不是子串）——模块路径一律落在接管面之外；
 *  ② 不在 `SHELL_KEEP_ALIVE_PATHS` 里（外壳类打空会让红无法归因到空态）；
 *  ③ 命中该锚点 `DATA_PATHS` 的前缀，或该锚点登记为 `'ALL'`。
 * 【接线】`anchors.spec.ts` 的 `stubApiToEmpty` 既把 route 的通配串换成**锚到 pathname 前缀的正则**，
 *  又在回调里走本函数收窄——两道都要：前者让模块请求根本不进回调（漏改判据也不会打碎应用），
 *  后者保留 `'ALL'` 与前缀名单的既有语义。改任何一段都要同步 helpers 那条守卫，别只改一边。 */
export function apiStubTakesPathname(pathname: string, prefixes: readonly string[]): boolean {
  if (!pathname.startsWith('/api/')) return false
  if (SHELL_KEEP_ALIVE_PATHS.some((k) => pathname.startsWith(k))) return false
  return prefixes.includes('ALL') || prefixes.some((p) => pathname.startsWith(p))
}
