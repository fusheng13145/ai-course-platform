import { describe, it, expect } from 'vitest'
import { isSafeInternalRedirect, resolvePostAuthRedirect } from '@/utils/redirect'

/**
 * 开放重定向（Open Redirect）回归护栏（P0-6）。
 *
 * 历史缺陷：Login.vue / Register.vue 直接 `router.push(route.query.redirect)`，
 * redirect 完全来自 URL 查询参数、由攻击者可控。
 * `/login?redirect=//evil.example` 会在用户登录成功的那一瞬间把人送出站点。
 * 本文件把「什么算站内路径」的边界钉死，任何放宽都会让这里变红。
 */
describe('isSafeInternalRedirect - 允许的站内路径', () => {
  it.each([
    '/student/dashboard',
    '/admin/users',
    '/student/course/1',
    '/student/courses?tab=enrolled',
    '/teacher/cockpit/1/grades?page=2',
    '/403',
    '/notifications#inbox',
  ])('%s 应判定为安全', (target) => {
    expect(isSafeInternalRedirect(target)).toBe(true)
  })
})

describe('isSafeInternalRedirect - 必须拒绝的外部跳转', () => {
  it.each([
    ['//evil.example', '协议相对 URL：浏览器会补协议后直接跳出站点'],
    ['//evil.example/steal', '协议相对 + 路径'],
    ['https://evil.example', '绝对 https'],
    ['http://evil.example', '绝对 http'],
    ['HTTPS://EVIL.EXAMPLE', '大小写混写的绝对 URL'],
    ['javascript:alert(1)', '伪协议'],
    ['data:text/html;base64,PHN2Zz4=', 'data 协议'],
    ['/\\evil.example', '反斜杠归一化：部分浏览器等价于 //'],
    ['/\\evil.example/path', '反斜杠后接路径'],
    ['student/dashboard', '缺少前导斜杠（相对路径，语义不确定）'],
    ['', '空字符串'],
    ['\t/student/dashboard', '含制表符前缀'],
    ['/student\r\n//evil.example', '含换行的注入'],
  ])('%s —— %s', (target) => {
    expect(isSafeInternalRedirect(target)).toBe(false)
  })

  it.each([null, undefined, 0, 1, {}, [], true, ['/a/b']])(
    '非字符串类型 %p 一律拒绝，不抛异常',
    (target) => {
      expect(isSafeInternalRedirect(target)).toBe(false)
    },
  )
})

describe('resolvePostAuthRedirect - 跳转目标兜底', () => {
  const FALLBACK = '/student/dashboard'

  it('合法站内 redirect 原样返回', () => {
    expect(resolvePostAuthRedirect('/admin/users', FALLBACK)).toBe('/admin/users')
    expect(resolvePostAuthRedirect('/student/course/1?tab=exam', FALLBACK)).toBe(
      '/student/course/1?tab=exam',
    )
  })

  it.each([
    '//evil.example',
    'https://evil.example',
    'javascript:alert(1)',
    null,
    undefined,
    '',
    42,
    {},
  ])('非法/缺失 redirect（%p）回落到 fallback，绝不外跳', (target) => {
    expect(resolvePostAuthRedirect(target, FALLBACK)).toBe(FALLBACK)
  })

  it('回落到 fallback 时不丢失 query 参数（站内带参路径完整保留）', () => {
    const target = '/student/courses?keyword=%E9%AB%98&page=2'
    expect(resolvePostAuthRedirect(target, FALLBACK)).toBe(target)
  })
})
