import { defineConfig, devices } from '@playwright/test'

// 端口可由 PW_PORT 指定：本机 5173 常被其它项目占用，而 webServer.reuseExistingServer
// 在非 CI 下为 true —— 直接跑会**静默复用那个项目**的服务，测出来的全是假缺陷。
// 默认仍为 5173（CI 与队友行为不变）；后端 CORS 白名单覆盖 5173/5174/5175。
const PORT = process.env.PW_PORT || '5173'
const BASE_URL = `http://localhost:${PORT}`

export default defineConfig({
  testDir: './e2e',
  // 视觉回归有自己的 config（playwright.visual.ts）：那边 workers:1、快照进 __snapshots__、
  // 端口 5175。留在这里被扫到，流程套件就会在 5173 上跑基线并假红。
  testIgnore: /[/\\]visual[/\\]/,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // 本地并发收敛为 4 worker：默认(CPU 核数)在 Windows 上 10 并发易因资源竞争产生 flaky
  // （auth 重定向 / student-ai 曾出现单跑通过、并发失败）；CI 保持单 worker 稳定
  workers: process.env.CI ? 1 : 4,
  reporter: [['html', { open: 'never' }], ['list']],
  timeout: 30000,
  expect: {
    timeout: 10000,
  },
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    // CI 全栈 e2e 用 vite preview 提供已构建静态资源（更快更稳），本地默认仍用 dev server
    command: process.env.PW_WEB_SERVER_COMMAND || `npm run dev -- --port ${PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
})
