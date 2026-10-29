import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ROLE_HOME } from './types'

const V2Shell = () => import('./V2Shell.vue')
const V2HomeView = () => import('./views/V2HomeView.vue')
const V2LoginView = () => import('./views/auth/V2LoginView.vue')
const V2RegisterView = () => import('./views/auth/V2RegisterView.vue')
const V2ForgotPasswordView = () => import('./views/auth/V2ForgotPasswordView.vue')
const V2LegalView = () => import('./views/legal/V2LegalView.vue')
const V2ErrorView = () => import('./views/error/V2ErrorView.vue')

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
  return homeFor(auth.effectiveRole || auth.userRole) || '/v2/login'
}
const V2GlobalPage = () => import('./views/V2GlobalPage.vue')
const V2AdminQuestionsView = () => import('./views/admin/V2AdminQuestionsView.vue')
const V2AdminUploadsView = () => import('./views/admin/V2AdminUploadsView.vue')
const V2CoursesView = () => import('./views/V2CoursesView.vue')
const V2CourseView = () => import('./views/course/V2CourseView.vue')
const V2TeacherBankView = () => import('./views/teacher/V2TeacherBankView.vue')
const V2TeacherExamsView = () => import('./views/teacher/V2TeacherExamsView.vue')
const V2CreateCourseView = () => import('./views/teacher/V2CreateCourseView.vue')
const V2TeacherGradesView = () => import('./views/teacher/V2TeacherGradesView.vue')
const V2InteractionView = () => import('./views/teacher/V2InteractionView.vue')
const V2KnowledgeManagementView = () => import('./views/teacher/V2KnowledgeManagementView.vue')
const V2StudentExamsView = () => import('./views/student/V2StudentExamsView.vue')

const page = (
  path: string,
  role: 'STUDENT' | 'TEACHER' | 'ADMIN',
  v2page: string,
  title: string,
) => ({
  path,
  component: V2GlobalPage,
  meta: { role, v2page, title },
})

export const v2Routes: RouteRecordRaw[] = [
  {
    path: '/v2/login',
    component: V2LoginView,
    meta: { requiresAuth: false, title: '登录' },
    beforeEnter: authRedirect,
  },
  {
    path: '/v2/register',
    component: V2RegisterView,
    meta: { requiresAuth: false, title: '注册' },
    beforeEnter: authRedirect,
  },
  {
    path: '/v2/forgot-password',
    component: V2ForgotPasswordView,
    meta: { requiresAuth: false, title: '找回密码' },
  },
  {
    path: '/v2/agreement',
    component: V2LegalView,
    props: { kind: 'agreement' },
    meta: { requiresAuth: false, title: '用户服务协议' },
  },
  {
    path: '/v2/privacy',
    component: V2LegalView,
    props: { kind: 'privacy' },
    meta: { requiresAuth: false, title: '隐私政策' },
  },
  {
    path: '/v2/403',
    component: V2ErrorView,
    meta: { requiresAuth: false, v2error: '403', title: '无权限' },
  },
  {
    path: '/v2/404',
    component: V2ErrorView,
    meta: { requiresAuth: false, v2error: '404', title: '页面不存在' },
  },
  {
    path: '/v2/503',
    component: V2ErrorView,
    meta: { requiresAuth: false, v2error: '503', title: '服务连接失败' },
  },
  {
    path: '/v2',
    component: V2Shell,
    meta: { requiresAuth: true, title: '新版工作台' },
    redirect: () => {
      const auth = useAuthStore()
      if (!auth.isLoggedIn) return '/v2/login'
      return homeFor(auth.effectiveRole || auth.userRole) || '/v2/login'
    },
    children: [
      {
        path: 'student/home',
        component: V2HomeView,
        meta: { role: 'STUDENT', v2page: 'home', title: '工作台' },
      },
      {
        path: 'student/courses',
        component: V2CoursesView,
        meta: { role: 'STUDENT', v2page: 'courses', title: '我的课程' },
      },
      {
        path: 'student/courses/:id/:tab?',
        component: V2CourseView,
        meta: { role: 'STUDENT', v2course: true, title: '课程空间' },
      },
      page('student/tasks', 'STUDENT', 'tasks', '学习任务'),
      page('student/academic', 'STUDENT', 'academic', '学业档案'),
      {
        path: 'student/exams',
        component: V2StudentExamsView,
        meta: { role: 'STUDENT', v2page: 'exams', title: '我的考试' },
      },
      page('student/messages', 'STUDENT', 'messages', '消息'),
      page('student/profile', 'STUDENT', 'profile', '个人中心'),
      page('student/assistant', 'STUDENT', 'assistant', 'AI 助手'),

      {
        path: 'teacher/home',
        component: V2HomeView,
        meta: { role: 'TEACHER', v2page: 'home', title: '教学工作台' },
      },
      {
        path: 'teacher/courses',
        component: V2CoursesView,
        meta: { role: 'TEACHER', v2page: 'courses', title: '我的课程' },
      },
      {
        path: 'teacher/courses/create',
        component: V2CreateCourseView,
        meta: { role: 'TEACHER', v2page: 'createCourse', title: '创建课程' },
      },
      {
        path: 'teacher/courses/:id/:tab?',
        component: V2CourseView,
        meta: { role: 'TEACHER', v2course: true, title: '课程空间' },
      },
      {
        path: 'teacher/bank',
        component: V2TeacherBankView,
        meta: { role: 'TEACHER', v2page: 'bank', title: '题库管理' },
      },
      {
        path: 'teacher/exams',
        component: V2TeacherExamsView,
        meta: { role: 'TEACHER', v2page: 'exams', title: '试卷库' },
      },
      {
        path: 'teacher/grades',
        component: V2TeacherGradesView,
        meta: { role: 'TEACHER', v2page: 'grades', title: '成绩管理' },
      },
      {
        path: 'teacher/interaction',
        component: V2InteractionView,
        meta: { role: 'TEACHER', v2page: 'interaction', title: '课堂互动' },
      },
      {
        path: 'teacher/knowledge-management',
        component: V2KnowledgeManagementView,
        meta: { role: 'TEACHER', v2page: 'knowledge', title: '知识点管理' },
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
        component: V2HomeView,
        meta: { role: 'ADMIN', v2page: 'home', title: '治理总览' },
      },
      {
        path: 'admin/courses',
        component: V2CoursesView,
        meta: { role: 'ADMIN', v2page: 'courses', title: '课程目录' },
      },
      {
        path: 'admin/courses/:id/:tab?',
        component: V2CourseView,
        meta: { role: 'ADMIN', v2course: true, title: '课程治理' },
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
      {
        path: 'admin/questions',
        component: V2AdminQuestionsView,
        meta: { role: 'ADMIN', v2page: 'adminQuestions', title: '题库管理' },
      },
      {
        path: 'admin/uploads',
        component: V2AdminUploadsView,
        meta: { role: 'ADMIN', v2page: 'adminUploads', title: '上传会话监控' },
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
    path: '/v2/:pathMatch(.*)*',
    component: V2ErrorView,
    meta: { requiresAuth: false, v2error: '404', title: '页面不存在' },
  },
]
