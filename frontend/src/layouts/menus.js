/**
 * 云枢教学平台 · 三端导航分区配置
 * 侧边栏手风琴结构：分区标签 → 一级菜单项 → 二级子菜单
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
        key: 'users', text: '用户权限', icon: 'users',
        path: '/admin/users',
      },
      {
        key: 'roles', text: '角色管理', icon: 'shield',
        path: '/admin/permissions',
      },
      {
        key: 'settings', text: '系统设置', icon: 'settings',
        path: '/admin/system',
      },
    ],
  },
  {
    label: '教学管理',
    items: [
      {
        key: 'courses', text: '课程库总览', icon: 'book',
        children: [
          { key: 'courses-all', text: '全部课程', path: '/admin/courses' },
          { key: 'courses-classes', text: '专业班级管理', path: '/admin/classes' },
        ],
      },
      { key: 'questions', text: '题库管理', icon: 'doc', path: '/admin/questions' },
      { key: 'resources', text: '资源管理', icon: 'archive', path: '/admin/resources' },
    ],
  },
  {
    label: '数据与公告',
    items: [
      { key: 'dashboard', text: '数据看板', icon: 'chart', path: '/admin/dashboard' },
      { key: 'analytics', text: '教学分析', icon: 'sliders', path: '/admin/analytics' },
      {
        key: 'governance', text: '监管与考试', icon: 'shield',
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
    ],
  },
]

/* ============================== 教师端分区 ============================== */
export const TEACHER_SECTIONS = [
  {
    label: '教学工作台',
    items: [
      { key: 'my', text: '我的课程', icon: 'book', path: '/teacher/courses' },
      { key: 'dashboard', text: '教学概览', icon: 'chart', path: '/teacher/dashboard' },
      { key: 'bank', text: '试题库', icon: 'doc', path: '/teacher/bank' },
    ],
  },
  {
    label: '教学工具',
    items: [
      { key: 'analytics', text: '学情统计', icon: 'sliders', path: '/teacher/analytics' },
      { key: 'interaction', text: '课堂互动', icon: 'message', path: '/teacher/interaction' },
      { key: 'discussion', text: '课程讨论', icon: 'grid', path: '/teacher/discussion' },
    ],
  },
  {
    label: '智能教学',
    items: [
      { key: 'knowledge', text: '知识图谱', icon: 'layers', path: '/teacher/knowledge' },
      { key: 'ai', text: 'AI 教学中心', icon: 'grad', path: '/teacher/ai' },
      { key: 'exams-manage', text: '考试管理', icon: 'clipboard', path: '/teacher/exams-manage' },
    ],
  },
]

/* ============================== 学生端分区 ============================== */
export const STUDENT_SECTIONS = [
  {
    label: '学习',
    items: [
      { key: 'hall', text: '课程大厅', icon: 'book', path: '/student/courses' },
      { key: 'dashboard', text: '学习概览', icon: 'chart', path: '/student/dashboard' },
      { key: 'ai-assistant', text: 'AI 助手', icon: 'grad', path: '/student/ai-assistant' },
    ],
  },
  {
    label: '学习工具',
    items: [
      { key: 'knowledge-graph', text: '知识图谱', icon: 'layers', path: '/student/knowledge-graph' },
      { key: 'learning-path', text: '学习路径', icon: 'pin', path: '/student/learning-path' },
      { key: 'calendar', text: '学习日历', icon: 'calendar', path: '/student/learning-calendar' },
    ],
  },
  {
    label: '课业中心',
    items: [
      { key: 'exams', text: '作业考试', icon: 'clipboard', path: '/student/exams' },
      { key: 'grades', text: '成绩查询', icon: 'award', path: '/student/grades' },
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
