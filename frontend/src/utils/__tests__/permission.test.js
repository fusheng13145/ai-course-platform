import { describe, it, expect } from 'vitest'
import { getDefaultRouteByRole, canAccessRoute } from '../permission'

describe('getDefaultRouteByRole', () => {
  it('ADMIN 应返回 /admin/dashboard', () => {
    expect(getDefaultRouteByRole('ADMIN')).toBe('/admin/dashboard')
  })

  it('TEACHER 应返回 /teacher/dashboard', () => {
    expect(getDefaultRouteByRole('TEACHER')).toBe('/teacher/dashboard')
  })

  it('STUDENT 应返回 /student/dashboard', () => {
    expect(getDefaultRouteByRole('STUDENT')).toBe('/student/dashboard')
  })

  it('未知角色应返回 /login', () => {
    expect(getDefaultRouteByRole('UNKNOWN')).toBe('/login')
  })

  it('空角色应返回 /login', () => {
    expect(getDefaultRouteByRole('')).toBe('/login')
  })

  it('null 角色应返回 /login', () => {
    expect(getDefaultRouteByRole(null)).toBe('/login')
  })

  it('undefined 角色应返回 /login', () => {
    expect(getDefaultRouteByRole(undefined)).toBe('/login')
  })
})

describe('canAccessRoute', () => {
  describe('ADMIN 角色', () => {
    it('应能访问 /admin 路径', () => {
      expect(canAccessRoute('ADMIN', '/admin/dashboard')).toBe(true)
      expect(canAccessRoute('ADMIN', '/admin/users')).toBe(true)
    })

    it('不应访问 /teacher 路径', () => {
      expect(canAccessRoute('ADMIN', '/teacher/dashboard')).toBe(false)
    })

    it('不应访问 /student 路径', () => {
      expect(canAccessRoute('ADMIN', '/student/dashboard')).toBe(false)
    })

    it('应能访问公共路径', () => {
      expect(canAccessRoute('ADMIN', '/home')).toBe(true)
    })
  })

  describe('TEACHER 角色', () => {
    it('应能访问 /teacher 路径', () => {
      expect(canAccessRoute('TEACHER', '/teacher/dashboard')).toBe(true)
      expect(canAccessRoute('TEACHER', '/teacher/courses')).toBe(true)
    })

    it('不应访问 /admin 路径', () => {
      expect(canAccessRoute('TEACHER', '/admin/dashboard')).toBe(false)
    })

    it('不应访问 /student 路径', () => {
      expect(canAccessRoute('TEACHER', '/student/dashboard')).toBe(false)
    })
  })

  describe('STUDENT 角色', () => {
    it('应能访问 /student 路径', () => {
      expect(canAccessRoute('STUDENT', '/student/dashboard')).toBe(true)
      expect(canAccessRoute('STUDENT', '/student/courses')).toBe(true)
    })

    it('不应访问 /admin 路径', () => {
      expect(canAccessRoute('STUDENT', '/admin/dashboard')).toBe(false)
    })

    it('不应访问 /teacher 路径', () => {
      expect(canAccessRoute('STUDENT', '/teacher/dashboard')).toBe(false)
    })
  })

  describe('空/无效角色', () => {
    it('空字符串角色应拒绝所有受保护路径', () => {
      expect(canAccessRoute('', '/admin/dashboard')).toBe(false)
      expect(canAccessRoute('', '/teacher/dashboard')).toBe(false)
      expect(canAccessRoute('', '/student/dashboard')).toBe(false)
    })

    it('null 角色应返回 false', () => {
      expect(canAccessRoute(null, '/admin/dashboard')).toBe(false)
    })

    it('undefined 角色应返回 false', () => {
      expect(canAccessRoute(undefined, '/admin/dashboard')).toBe(false)
    })
  })
})
