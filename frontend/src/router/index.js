import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false, title: '注册' }
  },
  {
    path: '/forgot-password',
    component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: { requiresAuth: false, title: '忘记密码' }
  },
  { path: '/403', component: () => import('@/views/error/403.vue'), meta: { requiresAuth: false, title: '无权限' } },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/error/404.vue'), meta: { requiresAuth: false, title: '页面不存在' } },

  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'users', component: () => import('@/views/admin/UserManagement.vue'), meta: { title: '用户管理' } },
      { path: 'courses', component: () => import('@/views/admin/CourseManagement.vue'), meta: { title: '课程管理' } },
      { path: 'analytics', component: () => import('@/views/admin/Analytics.vue'), meta: { title: '数据分析' } },
      { path: 'exams', component: () => import('@/views/admin/ExamManagement.vue'), meta: { title: '考试管理' } },
      { path: 'grades', component: () => import('@/views/admin/GradeManagement.vue'), meta: { title: '成绩管理' } },
      { path: 'notifications', component: () => import('@/views/admin/NotificationManagement.vue'), meta: { title: '通知管理' } },
      { path: 'questions', component: () => import('@/views/admin/QuestionManagement.vue'), meta: { title: '题库管理' } },
      { path: 'permissions', component: () => import('@/views/admin/PermissionManagement.vue'), meta: { title: '权限管理' } },
      { path: 'system', component: () => import('@/views/admin/SystemSettings.vue'), meta: { title: '系统设置' } }
    ]
  },

  {
    path: '/teacher',
    component: () => import('@/layouts/TeacherLayout.vue'),
    meta: { requiresAuth: true, role: 'TEACHER' },
    children: [
      { path: '', redirect: '/teacher/dashboard' },
      { path: 'dashboard', component: () => import('@/views/teacher/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'courses', component: () => import('@/views/teacher/Courses.vue'), meta: { title: '课程管理' } },
      { path: 'courses/create', component: () => import('@/views/teacher/CreateCourse.vue'), meta: { title: '创建课程' } },
      { path: 'courses/:id', component: () => import('@/views/teacher/CourseDetail.vue'), meta: { title: '课程详情' } },
      { path: 'knowledge-management', component: () => import('@/views/teacher/KnowledgeManagement.vue'), meta: { title: '知识管理' } },
      { path: 'analytics', component: () => import('@/views/teacher/Analytics.vue'), meta: { title: '教学分析' } },
      { path: 'exams', component: () => import('@/views/teacher/Exams.vue'), meta: { title: '考试管理' } },
      { path: 'grades', component: () => import('@/views/teacher/Grades.vue'), meta: { title: '成绩管理' } }
    ]
  },

  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: { requiresAuth: true, role: 'STUDENT' },
    children: [
      { path: '', redirect: '/student/dashboard' },
      { path: 'dashboard', component: () => import('@/views/student/Dashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'courses', component: () => import('@/views/student/Courses.vue'), meta: { title: '我的课程' } },
      { path: 'courses/:id', component: () => import('@/views/student/CourseDetail.vue'), meta: { title: '课程详情' } },
      { path: 'knowledge-graph', component: () => import('@/views/student/KnowledgeGraph.vue'), meta: { title: '知识图谱' } },
      { path: 'learning-path', component: () => import('@/views/student/LearningPath.vue'), meta: { title: '学习路径' } },
      { path: 'ai-assistant', component: () => import('@/views/student/AIAssistant.vue'), meta: { title: 'AI助手' } },
      { path: 'exams', component: () => import('@/views/student/Exams.vue'), meta: { title: '模拟考试' } },
      { path: 'grades', component: () => import('@/views/student/Grades.vue'), meta: { title: '我的成绩' } },
      { path: 'wrong-questions', component: () => import('@/views/student/WrongQuestions.vue'), meta: { title: '错题本' } },
      { path: 'learning-calendar', component: () => import('@/views/student/LearningCalendar.vue'), meta: { title: '学习日历' } },
      { path: 'profile', component: () => import('@/views/common/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'notifications', component: () => import('@/views/common/Notifications.vue'), meta: { title: '通知中心' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth !== false)

  if (!requiresAuth) {
    if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
      next(authStore.getDefaultRoute())
      return
    }
    next(); return
  }

  if (!authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } }); return
  }

  const requiredRole = to.matched.find(r => r.meta.role)?.meta.role
  if (requiredRole && authStore.userRole !== requiredRole) {
    next('/403'); return
  }

  next()
})

router.afterEach(() => { NProgress.done() })

export default router