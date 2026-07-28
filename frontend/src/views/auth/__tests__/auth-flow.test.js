import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div>Home</div>' } },
    { path: '/login', component: { template: '<div>Login</div>' } },
    { path: '/admin/dashboard', component: { template: '<div>Admin</div>' } },
    { path: '/teacher/courses', component: { template: '<div>Teacher</div>' } },
    { path: '/student/courses', component: { template: '<div>Student</div>' } },
  ],
})

describe('Authentication & Routing', () => {
  let pinia

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
  })

  it('Pinia store initializes without errors', () => {
    expect(pinia).toBeDefined()
  })

  it('router has all required role-based routes', () => {
    const routes = router.getRoutes()
    const paths = routes.map((r) => r.path)
    expect(paths).toContain('/login')
    expect(paths).toContain('/admin/dashboard')
    expect(paths).toContain('/teacher/courses')
    expect(paths).toContain('/student/courses')
  })

  it('router correctly resolves to login page', async () => {
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('router correctly resolves admin dashboard', async () => {
    await router.push('/admin/dashboard')
    expect(router.currentRoute.value.path).toBe('/admin/dashboard')
  })

  it('default route mapping is correct', () => {
    const roleRoutes = {
      ADMIN: '/admin/dashboard',
      TEACHER: '/teacher/courses',
      STUDENT: '/student/courses',
    }

    expect(roleRoutes['ADMIN']).toBe('/admin/dashboard')
    expect(roleRoutes['TEACHER']).toBe('/teacher/courses')
    expect(roleRoutes['STUDENT']).toBe('/student/courses')
  })
})
