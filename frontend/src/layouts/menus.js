/**
 * 三端导航菜单配置
 * 与 router/index.js 的路由一一对应；分组用 group 字段表达。
 * 图标统一使用 @element-plus/icons-vue。
 */
import {
  Aim,
  Bell,
  Calendar,
  ChatDotRound,
  Collection,
  Connection,
  Cpu,
  DataAnalysis,
  Document,
  EditPen,
  FolderOpened,
  Key,
  MagicStick,
  Monitor,
  Notebook,
  OfficeBuilding,
  QuestionFilled,
  Reading,
  Setting,
  TrendCharts,
  TrophyBase,
  User,
  VideoCamera,
  WarningFilled,
} from '@element-plus/icons-vue'

/** 管理员端 */
export const ADMIN_MENUS = [
  { path: '/admin/dashboard', title: '数据看板', icon: Monitor },
  { group: '用户与权限' },
  { path: '/admin/users', title: '用户管理', icon: User },
  { path: '/admin/permissions', title: '权限配置', icon: Key },
  { path: '/admin/classes', title: '班级管理', icon: OfficeBuilding },
  { group: '教学管理' },
  { path: '/admin/courses', title: '课程管理', icon: Reading },
  { path: '/admin/questions', title: '题库管理', icon: QuestionFilled },
  { path: '/admin/resources', title: '资源管理', icon: FolderOpened },
  { path: '/admin/exams', title: '考试管理', icon: Document },
  { path: '/admin/grades', title: '成绩管理', icon: TrophyBase },
  { group: '监管与分析' },
  { path: '/admin/supervision', title: '教学监督', icon: DataAnalysis },
  { path: '/admin/exam-governance', title: '考试治理', icon: VideoCamera },
  { path: '/admin/analytics', title: '数据统计', icon: TrendCharts },
  { group: '系统' },
  { path: '/admin/knowledge-base', title: '知识库管理', icon: Collection },
  { path: '/admin/notifications', title: '通知公告', icon: Bell },
  { path: '/admin/ai-supplier', title: 'AI 服务配置', icon: Cpu },
  { path: '/admin/system', title: '系统设置', icon: Setting },
  { path: '/admin/logs', title: '日志审计', icon: Notebook },
]

/** 教师端 */
export const TEACHER_MENUS = [
  { path: '/teacher/dashboard', title: '工作台', icon: Monitor },
  { path: '/teacher/courses', title: '我的课程', icon: Reading },
  { group: '作业考试' },
  { path: '/teacher/bank', title: '题库管理', icon: QuestionFilled },
  { path: '/teacher/exams', title: '试卷库', icon: EditPen },
  { group: '教学工具' },
  { path: '/teacher/analytics', title: '学情统计', icon: TrendCharts },
  { path: '/teacher/interaction', title: '课堂互动', icon: ChatDotRound },
  { path: '/teacher/discussion', title: '课程讨论', icon: Connection },
  { path: '/teacher/knowledge', title: '知识图谱', icon: Aim },
  { path: '/teacher/ai', title: 'AI 教学中心', icon: MagicStick },
]

/** 学生端 */
export const STUDENT_MENUS = [
  { path: '/student/dashboard', title: '学习首页', icon: Monitor },
  { path: '/student/courses', title: '我的课程', icon: Reading },
  { path: '/student/ai-assistant', title: 'AI 助手', icon: MagicStick },
  { group: '学习' },
  { path: '/student/knowledge-graph', title: '知识图谱', icon: Connection },
  { path: '/student/learning-path', title: '学习路径', icon: Aim },
  { path: '/student/learning-calendar', title: '学习日历', icon: Calendar },
  { group: '课业' },
  { path: '/student/exams', title: '作业考试', icon: Document },
  { path: '/student/grades', title: '成绩查询', icon: TrophyBase },
  { path: '/student/wrong-questions', title: '错题本', icon: WarningFilled },
  { path: '/student/discussion', title: '讨论区', icon: ChatDotRound },
  { group: '我的' },
  { path: '/student/notifications', title: '通知中心', icon: Bell },
  { path: '/student/profile', title: '个人中心', icon: User },
]

/** 角色元信息：徽标文案与标签类型 */
export const ROLE_META = {
  ADMIN: { label: '管理员', tagType: 'danger', brand: '管理控制台' },
  TEACHER: { label: '教师', tagType: 'success', brand: '教学工作台' },
  STUDENT: { label: '学生', tagType: 'primary', brand: '学习中心' },
}
