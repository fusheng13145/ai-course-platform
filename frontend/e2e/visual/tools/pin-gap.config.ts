import { defineConfig, devices } from '@playwright/test'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

/**
 * Task 0 差集取证用的**一次性只读 config**（修复轮 1 的 I-3② 把它从 gitignore 的
 * `test-results/pin-gap/` 挪进入库面；配套用例是同目录的 `pin-gap.probe.ts`）。
 *
 * 【跑法】工作目录 `frontend/`：
 *   `npx playwright test -c e2e/visual/tools/pin-gap.config.ts`
 * 读数落 `frontend/test-results/pin-gap/pin-gap-diff.json`（该目录 gitignore）与系统临时目录各一份。
 *
 * 【为什么不复用 `playwright.visual.ts`】那份 config 的 `globalSetup` 带 `--update-snapshots` 闸门，
 * 且 `testDir: './e2e/visual'` 会把整个视觉目录的用例一起收走——本脚本只要自己这一条。
 * `pin-gap.probe.ts` 故意**不叫 `.spec.ts`**：那样它既不会被 `playwright.visual.ts` 收进交付套件
 * （runner 的 testMatch 只认 `*.spec.ts`／`*.test.ts`），也不会被 `helpers.spec.ts` 那条
 * "e2e/visual 下的 spec 集合必须正好是那五个"的钉住断言点名——两者都是**有意的**：
 * 这条取证要打开后端、要登录会话，不属于交付套件每次跑一遍的内容。
 *
 * 【`outputDir` 必须挪出 `test-results/`（LEDGER 记的 harness 事实，见 `P0a-HARNESS-03`）】
 * `playwright.config.ts` 与 `playwright.visual.ts` 都没设 `outputDir` ⇒ 默认 `<cwd>/test-results`，
 * 而 Playwright **每次跑测开头清空 outputDir 整棵树** ⇒ 一次性脚本若住在 `test-results/` 里，
 * ① 测试发现阶段会跳过那棵树（症状：`No tests found`），② 任何人跑一遍官方套件就连目录带脚本删掉
 * （Task 0 踩了两次）。所以这里显式指向 `tmpdir()`，脚本本身住在入库面，删不掉。
 */
const PORT = process.env.PW_PORT || '5175'

export default defineConfig({
  outputDir: join(tmpdir(), 'pin-gap-pw-out'),
  testDir: '.',
  testMatch: 'pin-gap.probe.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 240000,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    {
      name: 'pin-gap',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
})
