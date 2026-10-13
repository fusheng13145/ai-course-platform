/**
 * 云枢教学平台 · 三端导航分区配置
 * 侧边栏手风琴结构：分区标签 → 一级菜单项 → 二级子菜单 →（课程目录）三级功能页签
 * 「课程目录」为动态一级：展开后是 总览入口 + 各具体课程，
 * 每门课程下再挂该课程独立的 作业/考试/成绩/知识图谱 等子项（对齐 /{role}/courses/:id/* 路由）
 * 对齐 edu-platform-prototype 原型设计
 */

/* ============================== 角色元信息 ============================== */
export const ROLE_META = {
  ADMIN: {
    label: '系统管理员',
    tagType: 'danger',
    brandMark: '枢',
    brandName: '云枢教学平台',
    brandSub: 'ADMIN CONSOLE',
  },
  TEACHER: {
    label: '计算机学院 · 教师',
    tagType: 'success',
    brandMark: '枢',
    brandName: '云枢教学平台',
    brandSub: 'TEACHER STUDIO',
  },
  STUDENT: {
    label: '本科生',
    tagType: 'primary',
    brandMark: '枢',
    brandName: '云枢教学平台',
    brandSub: 'STUDENT SPACE',
  },
}

/* ============================== 管理员端分区（静态治理维度；课程目录见 buildCourseCatalogSection） ============================== */
// 菜单节点形状：一级项要么直跳（path），要么下挂二级（children）；二级项可再下挂三级页签。
// 显式标注是类型棘轮的要求：「内容与资源治理」「权限·系统·审计」两节的一级项全部只挂 children，
// 不标注时 TS 会把这些子数组推成「没有 path 属性」的字面量类型，
// 于是消费方的通用遍历（nav-reachability.test.ts:71、本套件对 ADMIN_SECTIONS[0].items[0].path 的断言）
// 各多出 1 条 TS2339；那两个文件按本轮约束都不可改，基线又只许下降，故在此把形状写清。
type MenuLeaf = { key: string; text: string; path: string }
type MenuChild = MenuLeaf & { children?: MenuLeaf[] }
type MenuNode = {
  key: string
  text: string
  icon: string
  path?: string
  children?: MenuChild[]
}
type MenuSection = { label: string; items: MenuNode[] }

// 角色边界（项目主 2026-10-09 给定矩阵）：管理员端是平台运维管控层，按治理维度组织，
// 不按"我教的课"组织。矩阵六维度中的「课程管理」由动态「课程目录」分区承担（本表不重复声明），
// 故静态区恰好 6 节；治理大盘必须排第一节，它是 /admin 空路径重定向的落点（router/index.ts:43）。
// 一级项每节 ≤3，超出者收进二级 children——与教师/学生端同口径。
export const ADMIN_SECTIONS: MenuSection[] = [
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
      // 跨课程 AI 统计 + 教师出题权限治理（2026-10-13）：数据源 ai_call_logs，此前该能力在角色矩阵里无实现落点，
      // 现按项目主指令补齐；口径边界（历史不可回填）由页面持久展示后端下发的 collectionNote。
      { key: 'ai-usage', text: 'AI 使用与治理', icon: 'star', path: '/admin/ai-usage' },
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
          // 角色矩阵 ①②（2026-10-13）：创建学期 / 归档过期学期——此前无任何落点，本轮补齐
          { key: 'semesters', text: '学期管理', path: '/admin/semesters' },
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

/* ============================== 课程目录（动态三级） ============================== */

/** 每门课程下的功能页签：suffix 对应 `/${role}/courses/:id/${suffix}` 子路由 */
export const COURSE_TABS = {
  TEACHER: [
    { suffix: 'overview', text: '课程概览' },
    { suffix: 'chapters', text: '章节与任务点' },
    { suffix: 'tasks', text: '作业管理' },
    { suffix: 'submissions', text: '作业批改' },
    { suffix: 'exams', text: '考试管理' },
    { suffix: 'grades', text: '成绩统计' },
    { suffix: 'knowledge', text: '知识图谱' },
    { suffix: 'knowledge-management', text: '知识点管理' },
    { suffix: 'bank', text: '课程题库' },
    { suffix: 'proctor', text: '在线监考' },
    { suffix: 'analytics', text: '学情统计' },
    { suffix: 'interaction', text: '课堂互动' },
    { suffix: 'ai', text: 'AI 教学助手' },
    { suffix: 'discussion', text: '课程讨论' },
  ],
  STUDENT: [
    { suffix: 'overview', text: '课程概览' },
    { suffix: 'learning-center', text: '作业 · 学习中心' },
    { suffix: 'exams', text: '考试练习' },
    { suffix: 'grades', text: '课程成绩' },
    { suffix: 'knowledge', text: '知识图谱' },
    { suffix: 'ai', text: 'AI 助手' },
  ],
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
}

/** 课程目录的「总览」入口（统一列表页，保留在动态课程之前） */
const COURSE_CATALOG_ENTRY = {
  TEACHER: { allText: '课程总览', allPath: '/teacher/courses' },
  STUDENT: { allText: '课程总览', allPath: '/student/courses' },
  ADMIN: { allText: '课程治理总览', allPath: '/admin/courses' },
}

/**
 * 按角色 + 课程列表构建「课程目录」分区（一级课程目录 → 二级各课程 → 三级功能页签）
 * @param role   TEACHER / STUDENT / ADMIN
 * @param courses [{ id, name }]
 */
export function buildCourseCatalogSection(role, courses) {
  const entry = COURSE_CATALOG_ENTRY[role]
  const tabs = COURSE_TABS[role] || []
  const roleLower = String(role).toLowerCase()
  const children = [
    { key: 'course-catalog-all', text: entry.allText, path: entry.allPath },
    ...(courses || []).map((c) => ({
      key: `course-${c.id}`,
      text: c.name,
      children: tabs.map((t) => ({
        key: `course-${c.id}-${t.suffix}`,
        text: t.text,
        path: `/${roleLower}/courses/${c.id}/${t.suffix}`,
      })),
    })),
  ]
  return [
    {
      label: '课程目录',
      items: [{ key: 'course-catalog', text: '课程目录', icon: 'book', children }],
    },
  ]
}

/* ============================== 教师端分区（静态部分） ============================== */
// 教学工具/试题库/AI 教学中心/课程级页签已全部收敛为「课程目录」下每门课程的独立子项，
// 但**跨课程聚合视图**是课程级页签替代不了的语义（教师带多门课时需要一处看全部）：
// /teacher/bank、/teacher/exams、/teacher/grades-manage、/teacher/analytics、
// /teacher/interaction、/teacher/discussion、/teacher/knowledge、/teacher/knowledge-management、
// /teacher/ai 这 9 条统一路由此前无入口（孤儿），2026-10-13 经逐条取证确认均可独立使用
// （自带课程下拉、无 courseId 时列全部课程），故以两节静态区正式给出入口；
// 一级项每节 ≤3，故按语义收进二级 children（与管理员端同口径）。
// ⚠️ /teacher/exams-manage 与 /teacher/exams 指向同一 examApi.getExamPaperList 且无课程筛选，
// 属收敛期旧拆分残留，已随本轮移除（路由与视图一并删除）。
// 显式标注 MenuSection[]（与 ADMIN_SECTIONS 同口径）：本次新增的一级项全部只挂 children，
// 不标注时 TS 会把子数组推成「没有 path 属性」的字面量类型，使消费方的通用遍历
// （nav-reachability.test.ts:71-72）多出 TS2339 而撞类型棘轮。
export const TEACHER_SECTIONS: MenuSection[] = [
  {
    label: '教学工作台',
    items: [{ key: 'dashboard', text: '教学概览', icon: 'chart', path: '/teacher/dashboard' }],
  },
  {
    label: '试题与学情',
    items: [
      {
        key: 't-assess',
        text: '试题与考试',
        icon: 'doc',
        children: [
          { key: 't-bank', text: '题库管理', path: '/teacher/bank' },
          { key: 't-exams', text: '试卷库', path: '/teacher/exams' },
        ],
      },
      {
        key: 't-insight',
        text: '学情与成绩',
        icon: 'chart',
        children: [
          { key: 't-grades', text: '成绩总览', path: '/teacher/grades-manage' },
          { key: 't-analytics', text: '学情统计', path: '/teacher/analytics' },
        ],
      },
    ],
  },
  {
    label: '课堂与知识',
    items: [
      {
        key: 't-class',
        text: '课堂运营',
        icon: 'message',
        children: [
          { key: 't-interaction', text: '课堂互动', path: '/teacher/interaction' },
          { key: 't-discussion', text: '课程讨论', path: '/teacher/discussion' },
        ],
      },
      {
        key: 't-kna',
        text: '知识与 AI',
        icon: 'layers',
        children: [
          { key: 't-knowledge', text: '知识图谱', path: '/teacher/knowledge' },
          { key: 't-km', text: '知识点管理', path: '/teacher/knowledge-management' },
          { key: 't-ai', text: 'AI 教学中心', path: '/teacher/ai' },
        ],
      },
    ],
  },
]

/* ============================== 学生端分区（静态部分） ============================== */
// 与教师端同为「课程目录 + 静态区」的混合结构：可归属单门课程的功能
// （课程概览/作业·学习中心/考试练习/课程成绩/知识图谱/AI 助手）走每门课程的三级页签；
// 跨课程与纯个人入口保留在静态区，分区口径与路由表一致（study/ 归学习工具、academic/ 归课业中心）。
// 显式标注 MenuSection[]，理由同 TEACHER_SECTIONS（防通用遍历处的字面量联合类型报 TS2339）。
export const STUDENT_SECTIONS: MenuSection[] = [
  {
    label: '学习',
    items: [{ key: 'dashboard', text: '学习概览', icon: 'chart', path: '/student/dashboard' }],
  },
  {
    label: '学习工具',
    items: [
      { key: 'learning-path', text: '学习路径', icon: 'pin', path: '/student/learning-path' },
      { key: 'calendar', text: '学习日历', icon: 'calendar', path: '/student/learning-calendar' },
      { key: 'graph', text: '知识图谱', icon: 'layers', path: '/student/knowledge-graph' },
    ],
  },
  {
    label: '课业中心',
    items: [
      { key: 'exams', text: '模拟考试', icon: 'clipboard', path: '/student/exams' },
      { key: 'grades', text: '我的成绩', icon: 'award', path: '/student/grades' },
      { key: 'wrong', text: '错题本', icon: 'warn', path: '/student/wrong-questions' },
    ],
  },
  {
    // /student/ai-assistant 与课程级「AI 助手」页签指向同一组件，但语义不同：
    // 前者课程下拉可留空（selectedCourseId 为空时不带 courseId 请求）→ 支持**不绑定课程的通识问答**，
    // 后者固定绑定当前课程。此前该路由无入口（孤儿），2026-10-13 取证后正式给出入口。
    label: '智能助手',
    items: [{ key: 'ai-assistant', text: 'AI 助手', icon: 'star', path: '/student/ai-assistant' }],
  },
  {
    label: '个人',
    items: [
      { key: 'discussion', text: '讨论区', icon: 'message', path: '/student/discussion' },
      { key: 'notifications', text: '通知中心', icon: 'bell', path: '/student/notifications' },
    ],
  },
]
