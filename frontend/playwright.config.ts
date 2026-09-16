import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
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
    baseURL: 'http://localhost:5173',
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
    command: process.env.PW_WEB_SERVER_COMMAND || 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
})
