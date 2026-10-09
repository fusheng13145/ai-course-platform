import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock NProgress
vi.mock('nprogress', () => ({
  default: { start: vi.fn(), done: vi.fn(), configure: vi.fn() },
}))

// 可控制的 mock auth store
const mockStore = {
  isLoggedIn: false,
  userRole: '',
  // 路由鉴权实际读取的字段（优先服务端角色，见 stores/auth.effectiveRole）
  effectiveRole: '',
  permissions: null,
  // 权限是否已由本会话内的真实服务端响应置位
  permissionsLoaded: false,
  // user 是真实 store 的形状成员；守卫链路不读取它，仅为一致性声明
  user: null as unknown,
  getDefaultRoute: vi.fn(() => '/student/dashboard'),
  fetchPermissions: vi.fn(),
  resetAuthState: vi.fn(),
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockStore,
}))

// Mock 懒加载组件
vi.mock('@/views/auth/Login.vue', () => ({ default: { name: 'Login' } }))
vi.mock('@/views/auth/Register.vue', () => ({ default: { name: 'Register' } }))
vi.mock('@/views/auth/ForgotPassword.vue', () => ({ default: { name: 'ForgotPassword' } }))
vi.mock('@/views/auth/Agreement.vue', () => ({ default: { name: 'Agreement' } }))
vi.mock('@/views/auth/Privacy.vue', () => ({ default: { name: 'Privacy' } }))
vi.mock('@/views/error/403.vue', () => ({ default: { name: 'Forbidden' } }))
vi.mock('@/views/error/404.vue', () => ({ default: { name: 'NotFound' } }))
vi.mock('@/layouts/AdminLayout.vue', () => ({ default: { name: 'AdminLayout' } }))
vi.mock('@/layouts/TeacherLayout.vue', () => ({ default: { name: 'TeacherLayout' } }))
vi.mock('@/layouts/StudentLayout.vue', () => ({ default: { name: 'StudentLayout' } }))

// Mock 所有视图组件
vi.mock('@/views/admin/Dashboard.vue', () => ({ default: { name: 'AdminDashboard' } }))
vi.mock('@/views/admin/user/UserManagement.vue', () => ({ default: { name: 'UserManagement' } }))
vi.mock('@/views/admin/user/ClassManagement.vue', () => ({ default: { name: 'ClassManagement' } }))
vi.mock('@/views/admin/teaching/CourseManagement.vue', () => ({
  default: { name: 'CourseManagement' },
}))
vi.mock('@/views/admin/teaching/ResourceManagement.vue', () => ({
  default: { name: 'ResourceManagement' },
}))
vi.mock('@/views/admin/governance/Analytics.vue', () => ({ default: { name: 'Analytics' } }))
vi.mock('@/views/admin/teaching/ExamManagement.vue', () => ({
  default: { name: 'ExamManagement' },
}))
vi.mock('@/views/admin/teaching/GradeManagement.vue', () => ({
  default: { name: 'GradeManagement' },
}))
vi.mock('@/views/admin/system/NotificationManagement.vue', () => ({
  default: { name: 'NotificationManagement' },
}))
vi.mock('@/views/admin/teaching/QuestionManagement.vue', () => ({
  default: { name: 'QuestionManagement' },
}))
vi.mock('@/views/admin/user/PermissionManagement.vue', () => ({
  default: { name: 'PermissionManagement' },
}))
vi.mock('@/views/admin/system/OperationLogs.vue', () => ({ default: { name: 'OperationLogs' } }))
vi.mock('@/views/admin/system/SystemSettings.vue', () => ({ default: { name: 'SystemSettings' } }))
vi.mock('@/views/admin/governance/TeachingSupervision.vue', () => ({
  default: { name: 'TeachingSupervision' },
}))
vi.mock('@/views/admin/governance/ExamGovernance.vue', () => ({
  default: { name: 'ExamGovernance' },
}))
vi.mock('@/views/admin/system/AISupplierConfig.vue', () => ({
  default: { name: 'AISupplierConfig' },
}))
vi.mock('@/views/admin/system/KnowledgeBase.vue', () => ({ default: { name: 'KnowledgeBase' } }))
vi.mock('@/views/common/Profile.vue', () => ({ default: { name: 'Profile' } }))
vi.mock('@/views/common/Notifications.vue', () => ({ default: { name: 'Notifications' } }))
vi.mock('@/views/teacher/Dashboard.vue', () => ({ default: { name: 'TeacherDashboard' } }))
vi.mock('@/views/teacher/Courses.vue', () => ({ default: { name: 'TeacherCourses' } }))
vi.mock('@/views/teacher/CreateCourse.vue', () => ({ default: { name: 'CreateCourse' } }))
vi.mock('@/views/teacher/cockpit/CourseCockpit.vue', () => ({ default: { name: 'CourseCockpit' } }))
vi.mock('@/views/teacher/cockpit/CourseOverview.vue', () => ({
  default: { name: 'CourseOverview' },
}))
vi.mock('@/views/teacher/cockpit/CourseChapters.vue', () => ({
  default: { name: 'CourseChapters' },
}))
vi.mock('@/views/teacher/cockpit/CourseTasks.vue', () => ({ default: { name: 'CourseTasks' } }))
vi.mock('@/views/teacher/cockpit/CourseSubmissions.vue', () => ({
  default: { name: 'CourseSubmissions' },
}))
vi.mock('@/views/teacher/cockpit/CourseQuestionBank.vue', () => ({
  default: { name: 'CourseQuestionBank' },
}))
vi.mock('@/views/teacher/cockpit/CourseExams.vue', () => ({ default: { name: 'CourseExams' } }))
vi.mock('@/views/teacher/cockpit/CourseProctor.vue', () => ({ default: { name: 'CourseProctor' } }))
vi.mock('@/views/teacher/cockpit/CourseGrades.vue', () => ({ default: { name: 'CourseGrades' } }))
vi.mock('@/views/teacher/cockpit/CourseAnalytics.vue', () => ({
  default: { name: 'CourseAnalytics' },
}))
vi.mock('@/views/teacher/cockpit/CourseInteraction.vue', () => ({
  default: { name: 'CourseInteraction' },
}))
vi.mock('@/views/teacher/cockpit/CourseDiscussion.vue', () => ({
  default: { name: 'CourseDiscussion' },
}))
vi.mock('@/views/teacher/assessment/QuestionBank.vue', () => ({
  default: { name: 'QuestionBank' },
}))
vi.mock('@/views/teacher/assessment/ExamPapers.vue', () => ({ default: { name: 'ExamPapers' } }))
vi.mock('@/views/teacher/tools/Interaction.vue', () => ({ default: { name: 'Interaction' } }))
vi.mock('@/views/teacher/tools/Discussion.vue', () => ({ default: { name: 'Discussion' } }))
vi.mock('@/views/teacher/tools/KnowledgeGraph.vue', () => ({ default: { name: 'KnowledgeGraph' } }))
vi.mock('@/views/teacher/tools/AICenter.vue', () => ({ default: { name: 'AICenter' } }))
vi.mock('@/views/student/Dashboard.vue', () => ({ default: { name: 'StudentDashboard' } }))
vi.mock('@/views/student/Courses.vue', () => ({ default: { name: 'StudentCourses' } }))
vi.mock('@/views/student/course/CourseDetail.vue', () => ({ default: { name: 'CourseDetail' } }))
vi.mock('@/views/student/course/LearningCenter.vue', () => ({
  default: { name: 'LearningCenter' },
}))
vi.mock('@/views/student/study/LearningPath.vue', () => ({ default: { name: 'LearningPath' } }))
vi.mock('@/views/student/AIAssistant.vue', () => ({ default: { name: 'AIAssistant' } }))
vi.mock('@/views/student/academic/Exams.vue', () => ({ default: { name: 'Exams' } }))
vi.mock('@/views/student/academic/Grades.vue', () => ({ default: { name: 'StudentGrades' } }))
vi.mock('@/views/student/academic/WrongQuestions.vue', () => ({
  default: { name: 'WrongQuestions' },
}))
vi.mock('@/views/student/study/LearningCalendar.vue', () => ({
  default: { name: 'LearningCalendar' },
}))

describe('路由守卫', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    // 重置 store 状态
    mockStore.isLoggedIn = false
    mockStore.userRole = ''
    mockStore.effectiveRole = ''
    mockStore.permissions = null
    mockStore.permissionsLoaded = false
    mockStore.user = null
    mockStore.getDefaultRoute = vi.fn(() => '/student/dashboard')
    // 默认让权限拉取"成功"，把 permissionsLoaded 置真，
    // 否则角色门禁会因服务端确认缺失而一律拒绝
    mockStore.fetchPermissions = vi.fn(async () => {
      mockStore.permissions = { menus: [], buttons: {} }
      mockStore.permissionsLoaded = true
    })

    const mod = await import('../index.js')
    router = mod.default

    // 重置到一个与 /login、/register 都不同的中立路由作为起点：
    // 若起点就是 /login，对 /login 的 push 会被 vue-router 当作重复导航短路，
    // 守卫是否执行就不再可观测，测试会退化成"看运气"。
    await router.replace('/agreement')
  })

  /** 先以游客身份落到指定页面，再切换为已登录态（用于观测"已在登录页"时的重定向） */
  async function arriveAsGuest(path) {
    mockStore.isLoggedIn = false
    await router.replace(path)
    expect(router.currentRoute.value.path).toBe(path)
    mockStore.isLoggedIn = true
  }

  describe('不需要认证的路由', () => {
    it('/login 路由 meta.requiresAuth 为 false', () => {
      const matched = router.getRoutes().find((r) => r.path === '/login')
      expect(matched).toBeDefined()
      expect(matched.meta.requiresAuth).toBeFalsy()
    })

    it('/register 路由 meta.requiresAuth 为 false', () => {
      const matched = router.getRoutes().find((r) => r.path === '/register')
      expect(matched).toBeDefined()
      expect(matched.meta.requiresAuth).toBeFalsy()
    })

    it('/forgot-password 路由 meta.requiresAuth 为 false', () => {
      const matched = router.getRoutes().find((r) => r.path === '/forgot-password')
      expect(matched).toBeDefined()
      expect(matched.meta.requiresAuth).toBeFalsy()
    })

    it('未登录用户可以访问 /login', async () => {
      mockStore.isLoggedIn = false
      await router.push('/login')
      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('已登录学生访问 /login 真正被重定向到学生工作台', async () => {
      await arriveAsGuest('/login')
      mockStore.userRole = 'STUDENT'
      mockStore.effectiveRole = 'STUDENT'
      mockStore.getDefaultRoute = vi.fn(() => '/student/dashboard')

      await router.push('/student/dashboard?_r=' + Math.random()).catch(() => {})
      // 回到登录页触发「已登录访问 /login」分支
      await router.push('/login').catch(() => {})

      // 断言**真实导航结果**：守卫必须把人从 /login 送出
      expect(router.currentRoute.value.path).toBe('/student/dashboard')
      expect(mockStore.getDefaultRoute).toHaveBeenCalled()
    })

    it('已登录访问 /register 同样被重定向出注册页', async () => {
      await arriveAsGuest('/register')
      mockStore.userRole = 'TEACHER'
      mockStore.effectiveRole = 'TEACHER'
      mockStore.getDefaultRoute = vi.fn(() => '/teacher/dashboard')

      await router.push('/teacher/dashboard').catch(() => {})
      await router.push('/register').catch(() => {})

      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })

    it('回归（P0-3）：默认落点等于当前路由时不产生自指向重定向', async () => {
      await arriveAsGuest('/login')
      // 模拟"角色未知"的历史场景：getDefaultRoute 返回 /login 本身
      mockStore.getDefaultRoute = vi.fn(() => '/login')

      await router.push('/login').catch(() => {})

      // 关键不变式：必须以"停在 /login"收尾。原实现 next('/login') 自指 →
      // 守卫无限重入，router.push 永不 resolve。这里能正常 await 返回即证明有界。
      expect(router.currentRoute.value.path).toBe('/login')
      // 守卫在"目标 === 当前"时直接 next(false)，不会继续循环调用 getDefaultRoute
      expect(mockStore.getDefaultRoute.mock.calls.length).toBeLessThanOrEqual(2)
    })

    it('回归（P0-3）：/register 上默认落点回指 /register 时同样不循环', async () => {
      await arriveAsGuest('/register')
      mockStore.getDefaultRoute = vi.fn(() => '/register')

      await router.push('/register').catch(() => {})

      expect(router.currentRoute.value.path).toBe('/register')
      expect(mockStore.getDefaultRoute.mock.calls.length).toBeLessThanOrEqual(2)
    })
  })

  describe('需要认证的路由', () => {
    it('未登录用户访问 /student/dashboard 重定向到 /login', async () => {
      mockStore.isLoggedIn = false
      mockStore.userRole = ''
      mockStore.effectiveRole = ''

      await router.push('/student/dashboard')
      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('未登录用户访问 /admin/dashboard 重定向到 /login', async () => {
      mockStore.isLoggedIn = false
      mockStore.userRole = ''
      mockStore.effectiveRole = ''

      await router.push('/admin/dashboard')
      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('未登录用户访问需要认证的路由时带 redirect query', async () => {
      mockStore.isLoggedIn = false
      mockStore.userRole = ''

      await router.push('/student/courses')
      expect(router.currentRoute.value.query.redirect).toBe('/student/courses')
    })

    it('已登录用户访问匹配角色路由正常通过', async () => {
      mockStore.isLoggedIn = true
      mockStore.userRole = 'STUDENT'
      mockStore.effectiveRole = 'STUDENT'

      await router.push('/student/dashboard')
      expect(router.currentRoute.value.path).toBe('/student/dashboard')
    })

    it('已登录用户访问不匹配角色路由重定向到 /403', async () => {
      mockStore.isLoggedIn = true
      mockStore.userRole = 'STUDENT'
      mockStore.effectiveRole = 'STUDENT'

      await router.push('/admin/dashboard')
      expect(router.currentRoute.value.path).toBe('/403')
    })

    it('教师角色不能访问管理员路由', async () => {
      mockStore.isLoggedIn = true
      mockStore.userRole = 'TEACHER'
      mockStore.effectiveRole = 'TEACHER'

      await router.push('/admin/dashboard')
      expect(router.currentRoute.value.path).toBe('/403')
    })

    it('学生角色不能访问教师路由', async () => {
      mockStore.isLoggedIn = true
      mockStore.userRole = 'STUDENT'
      mockStore.effectiveRole = 'STUDENT'

      await router.push('/teacher/dashboard')
      expect(router.currentRoute.value.path).toBe('/403')
    })

    it('教师可以访问教师路由', async () => {
      mockStore.isLoggedIn = true
      mockStore.userRole = 'TEACHER'
      mockStore.effectiveRole = 'TEACHER'

      await router.push('/teacher/dashboard')
      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })

    it('管理员在服务端权限确认后可以访问管理员路由', async () => {
      mockStore.isLoggedIn = true
      mockStore.userRole = 'ADMIN'
      mockStore.effectiveRole = 'ADMIN'
      mockStore.permissions = { menus: ['admin/dashboard'], buttons: {} }
      mockStore.permissionsLoaded = true

      await router.push('/admin/dashboard')
      expect(router.currentRoute.value.path).toBe('/admin/dashboard')
    })

    it('篡改 userRole 为 ADMIN 但服务端未确认（effectiveRole 为空）→ 管理端拒绝', async () => {
      mockStore.isLoggedIn = true
      // 模拟攻击者在控制台把 localStorage 的 user.role 改成 ADMIN：
      // 真实 store 的 effectiveRole 在缺少服务端权限确认时对 ADMIN 返回 ''
      mockStore.userRole = 'ADMIN'
      mockStore.effectiveRole = ''
      mockStore.permissionsLoaded = false

      await router.push('/admin/dashboard')
      expect(router.currentRoute.value.path).toBe('/403')
      // 且不应把用户送进任何 admin 子路由
      expect(router.currentRoute.value.path.startsWith('/admin')).toBe(false)
    })
  })

  // DR-45：守卫必须消费 fetchPermissions 回传的「服务端会话结论」。
  // 原实现在 await fetchPermissions() 之后无条件 next()，于是本地缓存里有 user 的过期会话
  // 照样渲染受保护页；同一时刻拦截器又各自 router.push('/login')，两条导航互相取消，
  // 用户看到的是「还显示已登录、点什么都没反应，再点一次才跳登录页」。
  describe('会话确认结论（DR-45）', () => {
    /** 以「本地有缓存登录态、服务端结论待定」的姿态发起一次受保护导航 */
    async function navigateWithStaleSession(verdict, target) {
      mockStore.isLoggedIn = true
      mockStore.user = { id: 1001, role: 'TEACHER' }
      mockStore.effectiveRole = 'TEACHER'
      mockStore.permissions = { menus: [], buttons: {} }
      mockStore.permissionsLoaded = false
      mockStore.fetchPermissions = vi.fn(async () => {
        if (verdict === 'expired') {
          // 真实 store 在 401 分支会清空本地登录态
          mockStore.isLoggedIn = false
          mockStore.user = null
        }
        return verdict
      })
      await router.push(target)
    }

    it('服务端判定会话已过期：不渲染受保护页，带 redirect 跳登录页', async () => {
      await navigateWithStaleSession('expired', '/teacher/dashboard')
      expect(router.currentRoute.value.path).toBe('/login')
      expect(router.currentRoute.value.query.redirect).toBe('/teacher/dashboard')
    })

    it('服务端不可达（网络/5xx）：保留本地登录态放行，不把用户踢出登录页', async () => {
      await navigateWithStaleSession('unavailable', '/teacher/dashboard')
      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })

    it('服务端确认成功：正常放行，且不带多余的登录页跳转', async () => {
      await navigateWithStaleSession('ok', '/teacher/dashboard')
      expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
    })
  })

  describe('路由配置', () => {
    it('根路径 / 重定向到 /login', async () => {
      mockStore.isLoggedIn = false
      await router.push('/')
      expect(router.currentRoute.value.path).toBe('/login')
    })

    it('404 路由 meta.requiresAuth 为 false 且是通配兜底', () => {
      const matched = router.getRoutes().find((r) => r.path === '/:pathMatch(.*)*')
      expect(matched).toBeDefined()
      expect(matched.meta.requiresAuth).toBe(false)
    })

    it('三大角色区的路由链上都必须声明 meta.role（守卫据此拦截）', () => {
      // 注意：不能用 getRoutes() + find(path) 判定 —— 父记录与 path:'' 的子记录
      // 会被规范化成同一个 path，find 命中哪一条不确定，meta.role 会"看起来缺失"。
      // 这里用 resolve().matched（即守卫里 to.matched 的同一份数据）来断言。
      const rolesOf = (target) =>
        router
          .resolve(target)
          .matched.map((r) => r.meta?.role)
          .filter(Boolean)

      expect(rolesOf('/admin/dashboard')).toContain('ADMIN')
      expect(rolesOf('/teacher/dashboard')).toContain('TEACHER')
      expect(rolesOf('/student/dashboard')).toContain('STUDENT')

      // 三区都必须实际挂载了子路由，否则 meta.role 形同虚设
      for (const prefix of ['/admin', '/teacher', '/student']) {
        const children = router.getRoutes().filter((r) => r.path.startsWith(prefix + '/'))
        expect(children.length, `${prefix} 区子路由过少`).toBeGreaterThan(5)
      }
    })

    it('所有需认证路由都必须落在某个角色区内（否则对所有登录用户开放）', () => {
      const offenders = router
        .getRoutes()
        // meta.requiresAuth 未显式关闭 → 守卫按"需要认证"处理
        .filter((r) => r.meta.requiresAuth !== false)
        .filter((r) => {
          // 容器/重定向记录本身不作为页面判定对象
          if (r.redirect !== undefined) return false
          const matched = router.resolve(r.path).matched
          const hasRole = matched.some((m) => m.meta?.role)
          return !hasRole
        })
        .map((r) => r.path)

      expect(offenders, `以下路由需要认证却没有任何 role 门禁：${offenders.join(', ')}`).toEqual([])
    })
  })
})
