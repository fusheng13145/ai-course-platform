import { defineConfig, devices } from '@playwright/test'

// 与 playwright.config.ts 分开的理由：那边 screenshot 是 'only-on-failure'
// （playwright.config.ts:25），成功即无图；视觉回归要的正是成功时的图。
// 两者共用一份 config 会让 9 个流程 spec 的行为连带改变。
const PORT = process.env.PW_PORT || '5175'
const BASE_URL = `http://localhost:${PORT}`

export default defineConfig({
  globalSetup: './e2e/visual/global-setup.ts',
  testDir: './e2e/visual',
  fullyParallel: false,
  workers: 1,
  // 与流程 config 一致：CI 里遗留的 .only 必须直接失败，否则会静默把 24 张基线的矩阵缩成 1 张
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 60000,
  reporter: [['list']],
  expect: { timeout: 20000 },
  use: {
    baseURL: BASE_URL,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      name: 'chromium-visual',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 },
    },
  ],
  // 快照落点显式指定，符合 spec §10 的 __snapshots__ 约定
  //（Playwright 默认落在 `<spec>-snapshots/`，与本仓库文档不一致，故改写模板）
  snapshotPathTemplate: '{testDir}/__snapshots__/{testFilePath}/{arg}{ext}',
  webServer: {
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
})
