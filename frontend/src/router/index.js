import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false, title: '注册' },
  },
  {
    path: '/forgot-password',
    component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: { requiresAuth: false, title: '忘记密码' },
  },
  {
    path: '/agreement',
    component: () => import('@/views/auth/Agreement.vue'),
    meta: { requiresAuth: false, title: '用户服务协议' },
  },
  {
    path: '/privacy',
    component: () => import('@/views/auth/Privacy.vue'),
    meta: { requiresAuth: false, title: '隐私政策' },
  },
  {
    path: '/403',
    component: () => import('@/views/error/403.vue'),
    meta: { requiresAuth: false, title: '无权限' },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      {
        path: 'dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'users',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'classes',
        component: () => import('@/views/admin/ClassManagement.vue'),
        meta: { title: '组织与班级' },
      },
      {
        path: 'courses',
        component: () => import('@/views/admin/CourseManagement.vue'),
        meta: { title: '课程管理' },
      },
      {
        path: 'resources',
        component: () => import('@/views/admin/ResourceManagement.vue'),
        meta: { title: '资源库' },
      },
      {
        path: 'analytics',
        component: () => import('@/views/admin/Analytics.vue'),
        meta: { title: '数据分析' },
      },
      {
        path: 'exams',
        component: () => import('@/views/admin/ExamManagement.vue'),
        meta: { title: '考试管理' },
      },
      {
        path: 'grades',
        component: () => import('@/views/admin/GradeManagement.vue'),
        meta: { title: '成绩管理' },
      },
      {
        path: 'notifications',
        component: () => import('@/views/admin/NotificationManagement.vue'),
        meta: { title: '通知管理' },
      },
      {
        path: 'questions',
        component: () => import('@/views/admin/QuestionManagement.vue'),
        meta: { title: '题库管理' },
      },
      {
        path: 'permissions',
        component: () => import('@/views/admin/PermissionManagement.vue'),
        meta: { title: '权限管理' },
      },
      {
        path: 'logs',
        component: () => import('@/views/admin/OperationLogs.vue'),
        meta: { title: '操作审计日志' },
      },
      {
        path: 'system',
        component: () => import('@/views/admin/SystemSettings.vue'),
        meta: { title: '系统设置' },
      },
      {
        path: 'supervision',
        component: () => import('@/views/admin/TeachingSupervision.vue'),
        meta: { title: '教学运行监管' },
      },
      {
        path: 'exam-governance',
        component: () => import('@/views/admin/ExamGovernance.vue'),
        meta: { title: '考试防作弊治理' },
      },
      {
        path: 'ai-supplier',
        component: () => import('@/views/admin/AISupplierConfig.vue'),
        meta: { title: 'AI 供应商配置' },
      },
      {
        path: 'knowledge-base',
        component: () => import('@/views/admin/KnowledgeBase.vue'),
        meta: { title: '知识库管理' },
      },
      {
        path: 'profile',
        component: () => import('@/views/common/Profile.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'message-center',
        component: () => import('@/views/common/Notifications.vue'),
        meta: { title: '通知中心' },
      },
    ],
  },

  {
    path: '/teacher',
    component: () => import('@/layouts/TeacherLayout.vue'),
    meta: { requiresAuth: true, role: 'TEACHER' },
    children: [
      { path: '', redirect: '/teacher/dashboard' },
      // 教师驾驶舱（增强仪表盘 v2）
      {
        path: 'dashboard',
        component: () => import('@/views/teacher/Dashboard.vue'),
        meta: { title: '教师驾驶舱' },
      },
      // 我的课程
      {
        path: 'courses',
        component: () => import('@/views/teacher/Courses.vue'),
        meta: { title: '我的课程' },
      },
      {
        path: 'courses/create',
        component: () => import('@/views/teacher/CreateCourse.vue'),
        meta: { title: '新建课程' },
      },
      // 课程驾驶舱（二级嵌套：概览/章节/作业/题库/考试/监考/成绩/学情/互动/讨论）
      {
        path: 'courses/:id',
        component: () => import('@/views/teacher/cockpit/CourseCockpit.vue'),
        meta: { title: '课程驾驶舱' },
        children: [
          { path: '', redirect: 'overview' },
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
            path: 'tasks',
            component: () => import('@/views/teacher/cockpit/CourseTasks.vue'),
            meta: { title: '作业任务' },
          },
          {
            path: 'submissions',
            component: () => import('@/views/teacher/cockpit/CourseSubmissions.vue'),
            meta: { title: '作业批改' },
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
            path: 'proctor',
            component: () => import('@/views/teacher/cockpit/CourseProctor.vue'),
            meta: { title: '在线监考' },
          },
          {
            path: 'grades',
            component: () => import('@/views/teacher/cockpit/CourseGrades.vue'),
            meta: { title: '过程性成绩' },
          },
          {
            path: 'analytics',
            component: () => import('@/views/teacher/cockpit/CourseAnalytics.vue'),
            meta: { title: '学情统计' },
          },
          {
            path: 'interaction',
            component: () => import('@/views/teacher/cockpit/CourseInteraction.vue'),
            meta: { title: '课堂互动' },
          },
          {
            path: 'discussion',
            component: () => import('@/views/teacher/cockpit/CourseDiscussion.vue'),
            meta: { title: '课程讨论' },
          },
        ],
      },
      // 跨课程功能域
      {
        path: 'bank',
        component: () => import('@/views/teacher/QuestionBank.vue'),
        meta: { title: '题库管理' },
      },
      {
        path: 'exams',
        component: () => import('@/views/teacher/ExamPapers.vue'),
        meta: { title: '试卷库' },
      },
      {
        path: 'analytics',
        component: () => import('@/views/teacher/Analytics.vue'),
        meta: { title: '学情统计' },
      },
      {
        path: 'interaction',
        component: () => import('@/views/teacher/Interaction.vue'),
        meta: { title: '课堂互动' },
      },
      {
        path: 'discussion',
        component: () => import('@/views/teacher/Discussion.vue'),
        meta: { title: '课程讨论' },
      },
      {
        path: 'knowledge',
        component: () => import('@/views/teacher/KnowledgeGraph.vue'),
        meta: { title: '知识图谱' },
      },
      {
        path: 'ai',
        component: () => import('@/views/teacher/AICenter.vue'),
        meta: { title: 'AI 教学中心' },
      },
      {
        path: 'profile',
        component: () => import('@/views/common/Profile.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'notifications',
        component: () => import('@/views/common/Notifications.vue'),
        meta: { title: '通知中心' },
      },
    ],
  },

  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: { requiresAuth: true, role: 'STUDENT' },
    children: [
      { path: '', redirect: '/student/dashboard' },
      {
        path: 'dashboard',
        component: () => import('@/views/student/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'courses',
        component: () => import('@/views/student/Courses.vue'),
        meta: { title: '我的课程' },
      },
      {
        path: 'courses/:id',
        component: () => import('@/views/student/CourseDetail.vue'),
        meta: { title: '课程详情' },
      },
      {
        path: 'courses/:id/learning-center',
        component: () => import('@/views/student/LearningCenter.vue'),
        meta: { title: '学习中心' },
      },
      {
        path: 'knowledge-graph',
        component: () => import('@/views/student/KnowledgeGraph.vue'),
        meta: { title: '知识图谱' },
      },
      {
        path: 'learning-path',
        component: () => import('@/views/student/LearningPath.vue'),
        meta: { title: '学习路径' },
      },
      {
        path: 'ai-assistant',
        component: () => import('@/views/student/AIAssistant.vue'),
        meta: { title: 'AI助手' },
      },
      {
        path: 'exams',
        component: () => import('@/views/student/Exams.vue'),
        meta: { title: '模拟考试' },
      },
      {
        path: 'grades',
        component: () => import('@/views/student/Grades.vue'),
        meta: { title: '我的成绩' },
      },
      {
        path: 'wrong-questions',
        component: () => import('@/views/student/WrongQuestions.vue'),
        meta: { title: '错题本' },
      },
      {
        path: 'discussion',
        component: () => import('@/views/student/Discussion.vue'),
        meta: { title: '课程讨论' },
      },
      {
        path: 'learning-calendar',
        component: () => import('@/views/student/LearningCalendar.vue'),
        meta: { title: '学习日历' },
      },
      {
        path: 'profile',
        component: () => import('@/views/common/Profile.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'notifications',
        component: () => import('@/views/common/Notifications.vue'),
        meta: { title: '通知中心' },
      },
    ],
  },
  // 404 路由必须放在最后，否则会拦截所有后续路由匹配
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/error/404.vue'),
    meta: { requiresAuth: false, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)

  if (!requiresAuth) {
    if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
      next(authStore.getDefaultRoute())
      return
    }
    next()
    return
  }

  if (!authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 刷新后权限可能尚未加载：确保已拉取一次，避免细粒度菜单/按钮权限判定失效
  if (!authStore.permissions) {
    await authStore.fetchPermissions()
  }

  const requiredRole = to.matched.find((r) => r.meta.role)?.meta.role
  if (requiredRole && authStore.userRole !== requiredRole) {
    next('/403')
    return
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
