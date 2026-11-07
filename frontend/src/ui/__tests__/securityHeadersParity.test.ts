import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * nginx 直出形态与后端 SecurityConfig 的安全响应头同口径守卫（A-16，2026-11-06）。
 *
 * 背景：验收实测 `frontend/nginx.conf` 的 add_header 命中为 0，而 CSP／X-Frame-Options／
 * HSTS／Referrer 只由后端挂在 `/api` 响应上 —— SPA 文档由 nginx 直出（正是 A 路径默认形态），
 * 于是该形态下浏览器一条安全头都收不到。头补在 nginx 侧就意味着**同一策略存在两份文本**，
 * 本守卫是防止两份分叉的唯一机械防线。
 */

const NGINX = readFileSync(join('..', 'frontend', 'nginx.conf'), 'utf-8')
const SECURITY = readFileSync(
  join('..', 'backend', 'src', 'main', 'java', 'com', 'aicourse', 'config', 'SecurityConfig.java'),
  'utf-8',
)

/** 取出 policyDirectives(...) 里被 + 拼接的所有字面量，按声明顺序拼回一条策略串。 */
function cspFromJava(): string {
  const start = SECURITY.indexOf('policyDirectives(')
  expect(start, 'SecurityConfig 里找不到 CSP 声明').toBeGreaterThan(-1)
  const slice = SECURITY.slice(start, SECURITY.indexOf(')))', start))
  const parts = [...slice.matchAll(/"([^"]*)"/g)].map((m) => m[1])
  expect(parts.length, 'CSP 字面量段数异常').toBeGreaterThanOrEqual(2)
  return parts.join('')
}

/** 取出 nginx 的 CSP 头值。 */
function cspFromNginx(): string {
  const hit = NGINX.match(/add_header\s+Content-Security-Policy\s+"([^"]*)"/)
  expect(hit, 'nginx.conf 没有 CSP add_header').not.toBeNull()
  return hit![1]
}

describe('安全响应头同口径（nginx 直出 ⇄ SecurityConfig）', () => {
  it('CSP 策略串逐字一致', () => {
    expect(cspFromNginx()).toBe(cspFromJava())
  })

  it('后端在 SecurityConfig 里声明的四类头，nginx 必须全部补发', () => {
    for (const header of [
      'Content-Security-Policy',
      'X-Frame-Options',
      'X-Content-Type-Options',
      'Referrer-Policy',
      'Strict-Transport-Security',
    ]) {
      expect(NGINX, `nginx.conf 缺 ${header}`).toMatch(
        new RegExp(`add_header\\s+${header}\\s+"[^"]+"\\s+always;`),
      )
    }
  })

  it('nginx 不隐藏版本令牌（server_tokens off）', () => {
    expect(NGINX).toMatch(/server_tokens\s+off;/)
  })

  it('阴性对照：检测器抓得住人造的口径分叉（防恒真假绿）', () => {
    const drifted = cspFromNginx().replace("frame-ancestors 'none'", "frame-ancestors 'self'")
    expect(drifted).not.toBe(cspFromJava())
  })
})
