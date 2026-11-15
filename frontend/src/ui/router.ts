import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw, Router, RouterHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { installRouterGuard } from '@/router/guard'
import { cancelAllPendingRequests } from '@/api/request'
import { ROLE_HOME } from './types'

const Shell = () => import('./Shell.vue')
const HomeView = () => import('./views/HomeView.vue')
const LoginView = () => import('./views/auth/LoginView.vue')
const RegisterView = () => import('./views/auth/RegisterView.vue')
const ForgotPasswordView = () => import('./views/auth/ForgotPasswordView.vue')
const LegalView = () => import('./views/legal/LegalView.vue')
const ErrorView = () => import('./views/error/ErrorView.vue')

function homeFor(roleValue: unknown): string {
  const role = String(roleValue || '')
    .trim()
    .toLowerCase()
  if (role === 'student' || role === 'teacher' || role === 'admin') return ROLE_HOME[role]
  return ''
}

const authRedirect = () => {
  const auth = useAuthStore()
  if (!auth.isLoggedIn) return true
  return homeFor(auth.effectiveRole || auth.userRole) || '/login'
}
const GlobalPage = () => import('./views/GlobalPage.vue')
const AdminQuestionsView = () => import('./views/admin/AdminQuestionsView.vue')
const AdminUploadsView = () => import('./views/admin/AdminUploadsView.vue')
const CoursesView = () => import('./views/CoursesView.vue')
const CourseView = () => import('./views/course/CourseView.vue')
const TeacherBankView = () => import('./views/teacher/TeacherBankView.vue')
const TeacherExamsView = () => import('./views/teacher/TeacherExamsView.vue')
const CreateCourseView = () => import('./views/teacher/CreateCourseView.vue')
const TeacherGradesView = () => import('./views/teacher/TeacherGradesView.vue')
const InteractionView = () => import('./views/teacher/InteractionView.vue')
const KnowledgeManagementView = () => import('./views/teacher/KnowledgeManagementView.vue')
const StudentExamsView = () => import('./views/student/StudentExamsView.vue')

const page = (
  path: string,
  role: 'STUDENT' | 'TEACHER' | 'ADMIN',
  pageKey: string,
  title: string,
) => ({
  path,
  component: GlobalPage,
  meta: { role, pageKey, title },
})

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: LoginView,
    meta: { requiresAuth: false, title: '登录' },
    beforeEnter: authRedirect,
  },
  {
    path: '/register',
    component: RegisterView,
    meta: { requiresAuth: false, title: '注册' },
    beforeEnter: authRedirect,
  },
  {
    path: '/forgot-password',
    component: ForgotPasswordView,
    meta: { requiresAuth: false, title: '找回密码' },
  },
  {
    path: '/agreement',
    component: LegalView,
    props: { kind: 'agreement' },
    meta: { requiresAuth: false, title: '用户服务协议' },
  },
  {
    path: '/privacy',
    component: LegalView,
    props: { kind: 'privacy' },
    meta: { requiresAuth: false, title: '隐私政策' },
  },
  {
    path: '/403',
    component: ErrorView,
    meta: { requiresAuth: false, errorCode: '403', title: '无权限' },
  },
  {
    path: '/404',
    component: ErrorView,
    meta: { requiresAuth: false, errorCode: '404', title: '页面不存在' },
  },
  {
    path: '/503',
    component: ErrorView,
    meta: { requiresAuth: false, errorCode: '503', title: '服务连接失败' },
  },
  {
    path: '/',
    component: Shell,
    meta: { requiresAuth: true, title: '新版工作台' },
    redirect: () => {
      const auth = useAuthStore()
      if (!auth.isLoggedIn) return '/login'
      return homeFor(auth.effectiveRole || auth.userRole) || '/login'
    },
    children: [
      {
        path: 'student/home',
        component: HomeView,
        meta: { role: 'STUDENT', pageKey: 'home', title: '工作台' },
      },
      {
        path: 'student/courses',
        component: CoursesView,
        meta: { role: 'STUDENT', pageKey: 'courses', title: '我的课程' },
      },
      {
        path: 'student/courses/:id/:tab?',
        component: CourseView,
        meta: { role: 'STUDENT', courseSpace: true, title: '课程空间' },
      },
      page('student/tasks', 'STUDENT', 'tasks', '学习任务'),
      page('student/academic', 'STUDENT', 'academic', '学业档案'),
      {
        path: 'student/exams',
        component: StudentExamsView,
        meta: { role: 'STUDENT', pageKey: 'exams', title: '我的考试' },
      },
      page('student/messages', 'STUDENT', 'messages', '消息'),
      page('student/profile', 'STUDENT', 'profile', '个人中心'),
      page('student/assistant', 'STUDENT', 'assistant', 'AI 助手'),

      {
        path: 'teacher/home',
        component: HomeView,
        meta: { role: 'TEACHER', pageKey: 'home', title: '教学工作台' },
      },
      {
        path: 'teacher/courses',
        component: CoursesView,
        meta: { role: 'TEACHER', pageKey: 'courses', title: '我的课程' },
      },
      {
        path: 'teacher/courses/create',
        component: CreateCourseView,
        meta: { role: 'TEACHER', pageKey: 'createCourse', title: '创建课程' },
      },
      {
        path: 'teacher/courses/:id/:tab?',
        component: CourseView,
        meta: { role: 'TEACHER', courseSpace: true, title: '课程空间' },
      },
      {
        path: 'teacher/bank',
        component: TeacherBankView,
        meta: { role: 'TEACHER', pageKey: 'bank', title: '题库管理' },
      },
      {
        path: 'teacher/exams',
        component: TeacherExamsView,
        meta: { role: 'TEACHER', pageKey: 'exams', title: '试卷库' },
      },
      {
        path: 'teacher/grades',
        component: TeacherGradesView,
        meta: { role: 'TEACHER', pageKey: 'grades', title: '成绩管理' },
      },
      {
        path: 'teacher/interaction',
        component: InteractionView,
        meta: { role: 'TEACHER', pageKey: 'interaction', title: '课堂互动' },
      },
      {
        path: 'teacher/knowledge-management',
        component: KnowledgeManagementView,
        meta: { role: 'TEACHER', pageKey: 'knowledge', title: '知识点管理' },
      },
      page('teacher/inbox', 'TEACHER', 'inbox', '教学任务'),
      page('teacher/analytics', 'TEACHER', 'analytics', '学情分析'),
      page('teacher/resources', 'TEACHER', 'resources', '资源中心'),
      page('teacher/advising', 'TEACHER', 'advising', '我的班级'),
      page('teacher/advising/profile', 'TEACHER', 'advisingProfile', '班级画像'),
      page('teacher/advising/alerts', 'TEACHER', 'advisingAlerts', '学生预警'),
      page('teacher/advising/notices', 'TEACHER', 'advisingNotices', '班级通知'),
      page('teacher/messages', 'TEACHER', 'messages', '消息'),
      page('teacher/profile', 'TEACHER', 'profile', '个人中心'),

      {
        path: 'admin/home',
        component: HomeView,
        meta: { role: 'ADMIN', pageKey: 'home', title: '治理总览' },
      },
      {
        path: 'admin/courses',
        component: CoursesView,
        meta: { role: 'ADMIN', pageKey: 'courses', title: '课程目录' },
      },
      {
        path: 'admin/courses/:id/:tab?',
        component: CourseView,
        meta: { role: 'ADMIN', courseSpace: true, title: '课程治理' },
      },
      page('admin/colleges', 'ADMIN', 'colleges', '学院与专业'),
      page('admin/classes', 'ADMIN', 'adminClasses', '行政班'),
      page('admin/users', 'ADMIN', 'users', '用户与账号'),
      page('admin/roles', 'ADMIN', 'roles', '角色与权限'),
      page('admin/semesters', 'ADMIN', 'semesters', '学期管理'),
      page('admin/approvals', 'ADMIN', 'approvals', '课程审批'),
      page('admin/teaching-classes', 'ADMIN', 'teachingClasses', '教学班监管'),
      page('admin/enrollments', 'ADMIN', 'enrollments', '选课与名单'),
      page('admin/supervision', 'ADMIN', 'supervision', '教学监督'),
      page('admin/exam-governance', 'ADMIN', 'examGovernance', '考试治理'),
      page('admin/grades', 'ADMIN', 'grades', '成绩与预警'),
      page('admin/ai-usage', 'ADMIN', 'aiUsage', 'AI 使用与治理'),
      page('admin/ai-provider', 'ADMIN', 'adminAi', 'AI 供应商'),
      {
        path: 'admin/questions',
        component: AdminQuestionsView,
        meta: { role: 'ADMIN', pageKey: 'adminQuestions', title: '题库管理' },
      },
      {
        path: 'admin/uploads',
        component: AdminUploadsView,
        meta: { role: 'ADMIN', pageKey: 'adminUploads', title: '上传会话监控' },
      },
      page('admin/moderation', 'ADMIN', 'moderation', '内容审核'),
      page('admin/notices', 'ADMIN', 'notices', '通知公告'),
      page('admin/audit', 'ADMIN', 'audit', '操作与登录日志'),
      page('admin/sessions', 'ADMIN', 'sessions', '会话管理'),
      page('admin/system', 'ADMIN', 'system', '系统设置'),
      page('admin/profile', 'ADMIN', 'profile', '个人中心'),
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    component: ErrorView,
    meta: { requiresAuth: false, errorCode: '404', title: '页面不存在' },
  },
]

/**
 * 给任意 router 装上 的鉴权守卫：落点固定在本命名空间内，
 * 因此旧 router 摘除后 仍能独立完成登录态校验、权限探针与角色门禁。
 */
export function installGuards(router: Router): void {
  installRouterGuard(router, () => ({
    loginPath: '/login',
    forbiddenPath: '/403',
    unavailablePath: '/503',
  }))
  // 2026-11-14 P2-5：路由切换后取消上一页遗留的在途请求——此前
  // cancelAllPendingRequests 全仓零调用，「页面切换统一取消」是死功能，旧页响应
  // 仍会写入新页状态。放在 afterEach（守卫探针 fetchPermissions 之后发起，
  // 不会被误杀）；新页请求在 afterEach 之后才发出，不受影响。
  router.afterEach(() => {
    cancelAllPendingRequests()
  })
}

/**
 * 独立可用的 router（测试传 createMemoryHistory，正式环境用浏览器历史）。
 */
export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  const router = createRouter({ history, routes: routes })
  installGuards(router)
  return router
}

/** 正式应用的唯一 router：入口与 HTTP 宿主共用同一个实例 */
export const appRouter = createAppRouter()
