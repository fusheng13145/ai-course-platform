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

/* ============================== 管理员端分区 ============================== */
export const ADMIN_SECTIONS = [
  {
    label: '系统管理',
    items: [
      {
        key: 'users',
        text: '用户权限',
        icon: 'users',
        path: '/admin/users',
      },
      {
        key: 'roles',
        text: '角色管理',
        icon: 'shield',
        path: '/admin/permissions',
      },
      {
        key: 'settings',
        text: '系统设置',
        icon: 'settings',
        path: '/admin/system',
      },
    ],
  },
  {
    label: '教学管理',
    items: [
      {
        key: 'courses',
        text: '课程库总览',
        icon: 'book',
        children: [
          { key: 'courses-all', text: '全部课程', path: '/admin/courses' },
          { key: 'courses-classes', text: '专业班级管理', path: '/admin/classes' },
        ],
      },
      { key: 'questions', text: '题库管理', icon: 'doc', path: '/admin/questions' },
      {
        key: 'resources',
        text: '资源管理',
        icon: 'archive',
        children: [
          { key: 'resources-all', text: '资源库', path: '/admin/resources' },
          { key: 'resource-versions', text: '资源版本', path: '/admin/resource-versions' },
        ],
      },
    ],
  },
  {
    label: '数据与公告',
    items: [
      { key: 'dashboard', text: '数据看板', icon: 'chart', path: '/admin/dashboard' },
      { key: 'analytics', text: '教学分析', icon: 'sliders', path: '/admin/analytics' },
      {
        key: 'governance',
        text: '监管与考试',
        icon: 'shield',
        children: [
          { key: 'gov-supervision', text: '教学监督', path: '/admin/supervision' },
          { key: 'gov-exams', text: '考试治理', path: '/admin/exam-governance' },
          { key: 'gov-plagiarism', text: '查重管理', path: '/admin/plagiarism' },
        ],
      },
      { key: 'notices', text: '通知公告', icon: 'megaphone', path: '/admin/notifications' },
    ],
  },
  {
    label: '系统配置',
    items: [
      { key: 'ai-cfg', text: 'AI 服务配置', icon: 'log', path: '/admin/ai-supplier' },
      { key: 'logs', text: '操作日志', icon: 'list', path: '/admin/logs' },
      { key: 'login-logs', text: '登录日志', icon: 'clock', path: '/admin/login-logs' },
      { key: 'kb', text: '知识库管理', icon: 'layers', path: '/admin/knowledge-base' },
      { key: 'rag', text: 'RAG 文档索引', icon: 'search', path: '/admin/rag-docs' },
      { key: 'user-sessions', text: '会话管理', icon: 'monitor', path: '/admin/user-sessions' },
      { key: 'file-upload', text: '上传管理', icon: 'upload', path: '/admin/file-upload' },
      { key: 'reminders', text: '提醒管理', icon: 'bell', path: '/admin/reminders' },
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
}

/** 课程目录的「总览」入口（统一列表页，保留在动态课程之前） */
const COURSE_CATALOG_ENTRY = {
  TEACHER: { allText: '课程总览', allPath: '/teacher/courses' },
  STUDENT: { allText: '课程总览', allPath: '/student/courses' },
}

/**
 * 按角色 + 课程列表构建「课程目录」分区（一级课程目录 → 二级各课程 → 三级功能页签）
 * @param role   TEACHER / STUDENT
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
// 统一路由（/teacher/bank、/teacher/ai、/teacher/analytics 等）保留兼容与跨课直达。
// 唯一保留的静态项是「教学概览」：它是教师登录后的落地页（stores/auth.ts、utils/permission.ts），
// 收敛后侧边栏再无入口，进入即回不去，故显式留在导航里。
export const TEACHER_SECTIONS = [
  {
    label: '教学工作台',
    items: [{ key: 'dashboard', text: '教学概览', icon: 'chart', path: '/teacher/dashboard' }],
  },
]

/* ============================== 学生端分区（静态部分） ============================== */
// 与教师端同为「课程目录 + 静态区」的混合结构：可归属单门课程的功能
// （课程概览/作业·学习中心/考试练习/课程成绩/知识图谱/AI 助手）走每门课程的三级页签；
// 跨课程与纯个人入口保留在静态区，分区口径与路由表一致（study/ 归学习工具、academic/ 归课业中心）。
export const STUDENT_SECTIONS = [
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
    label: '个人',
    items: [
      { key: 'discussion', text: '讨论区', icon: 'message', path: '/student/discussion' },
      { key: 'notifications', text: '通知中心', icon: 'bell', path: '/student/notifications' },
    ],
  },
]
