import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
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
        meta: { title: '仪表盘', breadcrumb: '数据看板' },
      },

      // ---- 用户与权限 (views/admin/user/) ----
      {
        path: 'users',
        component: () => import('@/views/admin/user/UserManagement.vue'),
        meta: { title: '用户管理', breadcrumb: '用户管理' },
      },
      {
        path: 'permissions',
        component: () => import('@/views/admin/user/PermissionManagement.vue'),
        meta: { title: '权限管理', breadcrumb: '权限配置' },
      },
      {
        path: 'classes',
        component: () => import('@/views/admin/user/ClassManagement.vue'),
        meta: { title: '组织与班级', breadcrumb: '班级管理' },
      },

      // ---- 教学管理 (views/admin/teaching/) ----
      {
        path: 'courses',
        component: () => import('@/views/admin/teaching/CourseManagement.vue'),
        meta: { title: '课程管理', breadcrumb: '课程管理' },
      },
      {
        path: 'questions',
        component: () => import('@/views/admin/teaching/QuestionManagement.vue'),
        meta: { title: '题库管理', breadcrumb: '题库管理' },
      },
      {
        path: 'resources',
        component: () => import('@/views/admin/teaching/ResourceManagement.vue'),
        meta: { title: '资源库', breadcrumb: '资源管理' },
      },
      {
        path: 'exams',
        component: () => import('@/views/admin/teaching/ExamManagement.vue'),
        meta: { title: '考试管理', breadcrumb: '考试管理' },
      },
      {
        path: 'grades',
        component: () => import('@/views/admin/teaching/GradeManagement.vue'),
        meta: { title: '成绩管理', breadcrumb: '成绩管理' },
      },
      // ---- 新增教学管理页面 ----
      {
        path: 'plagiarism',
        component: () => import('@/views/admin/teaching/PlagiarismCheckManage.vue'),
        meta: { title: '查重管理', breadcrumb: '查重管理' },
      },
      {
        path: 'resource-versions',
        component: () => import('@/views/admin/teaching/ResourceVersionManage.vue'),
        meta: { title: '资源版本', breadcrumb: '资源管理' },
      },

      // ---- 监管与分析 (views/admin/governance/) ----
      {
        path: 'supervision',
        component: () => import('@/views/admin/governance/TeachingSupervision.vue'),
        meta: { title: '教学运行监管', breadcrumb: '教学监督' },
      },
      {
        path: 'exam-governance',
        component: () => import('@/views/admin/governance/ExamGovernance.vue'),
        meta: { title: '考试防作弊治理', breadcrumb: '考试治理' },
      },
      {
        path: 'analytics',
        component: () => import('@/views/admin/governance/Analytics.vue'),
        meta: { title: '数据分析', breadcrumb: '数据统计' },
      },

      // ---- 系统配置 (views/admin/system/) ----
      {
        path: 'knowledge-base',
        component: () => import('@/views/admin/system/KnowledgeBase.vue'),
        meta: { title: '知识库管理', breadcrumb: '知识库' },
      },
      {
        path: 'rag-docs',
        component: () => import('@/views/admin/system/RagDocs.vue'),
        meta: { title: 'RAG 文档索引', breadcrumb: 'RAG 知识库' },
      },
      {
        path: 'notifications',
        component: () => import('@/views/admin/system/NotificationManagement.vue'),
        meta: { title: '通知管理', breadcrumb: '通知公告' },
      },
      {
        path: 'ai-supplier',
        component: () => import('@/views/admin/system/AISupplierConfig.vue'),
        meta: { title: 'AI 供应商配置', breadcrumb: 'AI服务' },
      },
      {
        path: 'system',
        component: () => import('@/views/admin/system/SystemSettings.vue'),
        meta: { title: '系统设置', breadcrumb: '系统设置' },
      },
      {
        path: 'logs',
        component: () => import('@/views/admin/system/OperationLogs.vue'),
        meta: { title: '操作审计日志', breadcrumb: '日志审计' },
      },
      {
        path: 'login-logs',
        component: () => import('@/views/admin/system/LoginLogs.vue'),
        meta: { title: '登录日志', breadcrumb: '登录审计' },
      },
      // ---- 新增系统管理页面 ----
      {
        path: 'user-sessions',
        component: () => import('@/views/admin/system/UserSessionManage.vue'),
        meta: { title: '会话管理', breadcrumb: '安全审计' },
      },
      {
        path: 'file-upload',
        component: () => import('@/views/admin/system/FileUploadManage.vue'),
        meta: { title: '上传管理', breadcrumb: '文件管理' },
      },
      {
        path: 'reminders',
        component: () => import('@/views/admin/system/ReminderManage.vue'),
        meta: { title: '提醒管理', breadcrumb: '系统管理' },
      },

      // ---- 个人 (views/common/) ----
      {
        path: 'profile',
        component: () => import('@/views/common/Profile.vue'),
        meta: { title: '个人中心', breadcrumb: '个人中心' },
      },
      {
        path: 'message-center',
        component: () => import('@/views/common/Notifications.vue'),
        meta: { title: '通知中心', breadcrumb: '通知中心' },
      },
    ],
  },

  // ==================== 教师端 ====================
  {
    path: '/teacher',
    component: () => import('@/layouts/TeacherLayout.vue'),
    meta: { requiresAuth: true, role: 'TEACHER' },
    children: [
      { path: '', redirect: '/teacher/dashboard' },
      {
        path: 'dashboard',
        component: () => import('@/views/teacher/Dashboard.vue'),
        meta: { title: '教师驾驶舱', breadcrumb: '驾驶舱' },
      },
      {
        path: 'courses',
        component: () => import('@/views/teacher/Courses.vue'),
        meta: { title: '我的课程', breadcrumb: '我的课程' },
      },
      {
        path: 'courses/create',
        component: () => import('@/views/teacher/CreateCourse.vue'),
        meta: { title: '新建课程', breadcrumb: '新建课程' },
      },
      {
        path: 'courses/:id/detail',
        component: () => import('@/views/teacher/CourseDetail.vue'),
        meta: { title: '课程详情', breadcrumb: '课程详情' },
      },
      // 课程驾驶舱（views/teacher/cockpit/）
      {
        path: 'courses/:id',
        component: () => import('@/views/teacher/cockpit/CourseCockpit.vue'),
        meta: { title: '课程驾驶舱', breadcrumb: '课程驾驶舱' },
        children: [
          { path: '', redirect: (to) => `/teacher/courses/${to.params.id}/overview` },
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
            path: 'knowledge',
            component: () => import('@/views/teacher/tools/KnowledgeGraph.vue'),
            meta: { title: '课程知识图谱' },
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
            path: 'knowledge-management',
            component: () => import('@/views/teacher/tools/KnowledgeManagement.vue'),
            meta: { title: '知识点管理' },
          },
          {
            path: 'ai',
            component: () => import('@/views/teacher/tools/AICenter.vue'),
            meta: { title: '课程 AI 教学助手' },
          },
          {
            path: 'discussion',
            component: () => import('@/views/teacher/cockpit/CourseDiscussion.vue'),
            meta: { title: '课程讨论' },
          },
        ],
      },

      // ---- 作业考试 (views/teacher/assessment/) ----
      {
        path: 'bank',
        component: () => import('@/views/teacher/assessment/QuestionBank.vue'),
        meta: { title: '题库管理', breadcrumb: '题库管理' },
      },
      {
        path: 'exams',
        component: () => import('@/views/teacher/assessment/ExamPapers.vue'),
        meta: { title: '试卷库', breadcrumb: '试卷库' },
      },

      // ---- 考试管理（教师个人视角，非课程级） ----
      {
        path: 'exams-manage',
        component: () => import('@/views/teacher/Exams.vue'),
        meta: { title: '考试管理', breadcrumb: '考试管理' },
      },
      {
        path: 'grades-manage',
        component: () => import('@/views/teacher/Grades.vue'),
        meta: { title: '成绩管理', breadcrumb: '成绩管理' },
      },

      // ---- 教学工具 (views/teacher/tools/) ----
      {
        path: 'analytics',
        component: () => import('@/views/teacher/tools/Analytics.vue'),
        meta: { title: '学情统计', breadcrumb: '学情统计' },
      },
      {
        path: 'interaction',
        component: () => import('@/views/teacher/tools/Interaction.vue'),
        meta: { title: '课堂互动', breadcrumb: '课堂互动' },
      },
      {
        path: 'discussion',
        component: () => import('@/views/teacher/tools/Discussion.vue'),
        meta: { title: '课程讨论', breadcrumb: '课程讨论' },
      },
      {
        path: 'knowledge',
        component: () => import('@/views/teacher/tools/KnowledgeGraph.vue'),
        meta: { title: '知识图谱', breadcrumb: '知识图谱' },
      },
      {
        path: 'knowledge-management',
        component: () => import('@/views/teacher/tools/KnowledgeManagement.vue'),
        meta: { title: '知识管理', breadcrumb: '知识管理' },
      },
      {
        path: 'ai',
        component: () => import('@/views/teacher/tools/AICenter.vue'),
        meta: { title: 'AI 教学中心', breadcrumb: 'AI教学' },
      },

      // ---- 个人 (views/common/) ----
      {
        path: 'profile',
        component: () => import('@/views/common/Profile.vue'),
        meta: { title: '个人中心', breadcrumb: '个人中心' },
      },
      {
        path: 'notifications',
        component: () => import('@/views/common/Notifications.vue'),
        meta: { title: '通知中心', breadcrumb: '通知中心' },
      },
    ],
  },

  // ==================== 学生端 ====================
  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: { requiresAuth: true, role: 'STUDENT' },
    children: [
      { path: '', redirect: '/student/dashboard' },
      {
        path: 'dashboard',
        component: () => import('@/views/student/Dashboard.vue'),
        meta: { title: '仪表盘', breadcrumb: '学习首页' },
      },
      {
        path: 'courses',
        component: () => import('@/views/student/Courses.vue'),
        meta: { title: '我的课程', breadcrumb: '我的课程' },
      },
      // 课程学习壳（views/student/course/）：将 CourseDetail + LearningCenter 嵌套到 Tab 导航中
      {
        path: 'courses/:id',
        component: () => import('@/views/student/course/CourseShell.vue'),
        meta: { title: '课程详情', breadcrumb: '课程详情' },
        children: [
          { path: '', redirect: (to) => `/student/courses/${to.params.id}/overview` },
          {
            path: 'overview',
            component: () => import('@/views/student/course/CourseDetail.vue'),
            meta: { title: '课程概览', breadcrumb: '课程概览' },
          },
          {
            path: 'learning-center',
            component: () => import('@/views/student/course/LearningCenter.vue'),
            meta: { title: '学习中心', breadcrumb: '学习中心' },
          },
          {
            path: 'exams',
            component: () => import('@/views/student/academic/Exams.vue'),
            meta: { title: '课程考试', breadcrumb: '考试练习' },
          },
          {
            path: 'grades',
            component: () => import('@/views/student/academic/Grades.vue'),
            meta: { title: '课程成绩', breadcrumb: '课程成绩' },
          },
          {
            path: 'knowledge',
            component: () => import('@/views/student/study/KnowledgeGraph.vue'),
            meta: { title: '课程知识图谱', breadcrumb: '知识图谱' },
          },
          {
            path: 'ai',
            component: () => import('@/views/student/AIAssistant.vue'),
            meta: { title: '课程 AI 助手', breadcrumb: 'AI 助手' },
          },
        ],
      },
      {
        path: 'ai-assistant',
        component: () => import('@/views/student/AIAssistant.vue'),
        meta: { title: 'AI助手', breadcrumb: 'AI助手' },
      },

      // ---- 学习工具 (views/student/study/) ----
      {
        path: 'knowledge-graph',
        component: () => import('@/views/student/study/KnowledgeGraph.vue'),
        meta: { title: '知识图谱', breadcrumb: '知识图谱' },
      },
      {
        path: 'learning-path',
        component: () => import('@/views/student/study/LearningPath.vue'),
        meta: { title: '学习路径', breadcrumb: '学习路径' },
      },
      {
        path: 'learning-calendar',
        component: () => import('@/views/student/study/LearningCalendar.vue'),
        meta: { title: '学习日历', breadcrumb: '学习日历' },
      },

      // ---- 课业中心 (views/student/academic/) ----
      {
        path: 'exams',
        component: () => import('@/views/student/academic/Exams.vue'),
        meta: { title: '模拟考试', breadcrumb: '作业考试' },
      },
      {
        path: 'grades',
        component: () => import('@/views/student/academic/Grades.vue'),
        meta: { title: '我的成绩', breadcrumb: '成绩查询' },
      },
      {
        path: 'wrong-questions',
        component: () => import('@/views/student/academic/WrongQuestions.vue'),
        meta: { title: '错题本', breadcrumb: '错题本' },
      },
      {
        path: 'discussion',
        component: () => import('@/views/student/academic/Discussion.vue'),
        meta: { title: '课程讨论', breadcrumb: '讨论区' },
      },

      // ---- 个人 (views/common/) ----
      {
        path: 'profile',
        component: () => import('@/views/common/Profile.vue'),
        meta: { title: '个人中心', breadcrumb: '个人中心' },
      },
      {
        path: 'notifications',
        component: () => import('@/views/common/Notifications.vue'),
        meta: { title: '通知中心', breadcrumb: '通知中心' },
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
      // 缺陷修复（P0-3）：已登录访问登录/注册页时跳默认落地页。
      // 若默认落地页恰好等于当前路径（历史上未知角色会返回 '/login'），
      // next('/login') 会再次进入本分支 → 自指向重定向。此处对「目标 === 当前」
      // 直接终止导航（next(false)），保证重定向链路无条件有界。
      const target = authStore.getDefaultRoute()
      if (target === to.path || target === to.fullPath) {
        next(false)
        NProgress.done()
        return
      }
      next(target)
      return
    }
    next()
    return
  }

  if (!authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 刷新后权限可能尚未加载：确保已拉取一次，避免细粒度菜单/按钮权限判定失效。
  // permissionsLoaded 只由一次真实的 /auth/permissions 成功响应置位，
  // 因此这里用「未加载」而不是「未缓存」作为重新拉取的条件（防本地伪造）。
  if (!authStore.permissions || !authStore.permissionsLoaded) {
    // DR-45：`isLoggedIn` 只表示本地缓存里有 user，不等于服务端认这次会话。
    // 所以必须消费 fetchPermissions 回传的结论：服务端 401 就在此终止导航、带 redirect
    // 跳登录页。原实现 await 之后无视结果照常 next()，跳转又由拦截器另起一路，
    // 两条导航互相取消 → 过期用户看到「仍已登录、点击无反应，再点才跳登录」。
    const verdict = await authStore.fetchPermissions()
    if (verdict === 'expired') {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  const requiredRole = to.matched.find((r) => r.meta.role)?.meta.role
  if (requiredRole) {
    // 角色判定一律走 effectiveRole：优先服务端回传角色，本地可篡改的 user.role
    // 仅在服务端确认过的会话里作为回退（ADMIN 还额外要求 permissionsLoaded）。
    // 因此手工把 localStorage 的 user.role 改成 ADMIN 并不能进入管理端。
    if (authStore.effectiveRole !== requiredRole) {
      next('/403')
      return
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
