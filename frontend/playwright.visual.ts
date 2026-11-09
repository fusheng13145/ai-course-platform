import { defineConfig, devices } from '@playwright/test'

// 与 playwright.config.ts 分开的理由：那边 screenshot 是 'only-on-failure'
// （playwright.config.ts:25），成功即无图；视觉回归要的正是成功时的图。
// 两者共用一份 config 会让 9 个流程 spec 的行为连带改变。
const PORT = process.env.PW_PORT || '5175'
const BASE_URL = `http://localhost:${PORT}`
// 【P-18 修复（V8 批）】失败工件（diff 图 / error-context）此前落在固定的 test-results/visual/ 下，
// 下一轮整目录重建即被覆盖——V2 批曾因此丢掉一轮 4 张红的归因证据。改为**按轮次时间戳隔离**，
// 每轮工件独立留存；基线本体在 e2e/visual/__snapshots__（snapshotPathTemplate），不受影响。
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)

export default defineConfig({
  globalSetup: './e2e/visual/global-setup.ts',
  testDir: './e2e/visual',
  // 与流程套件分开落点（见 playwright.config.ts 的 outputDir 注释）；
  // 注意：取证图是测试里显式 page.screenshot({ path }) 写出的，不归 outputDir 管。
  outputDir: `./test-results/visual/${RUN_ID}`,
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
