import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{js,ts,vue}'],
    // 2026-08-28：本机（Windows + 中文路径）默认并发模式 worker 挂起，
    // 顺序模式实测 29.5s 全绿，固化 fileParallelism:false 保证可重复验证。
    fileParallelism: false,
    // ---- 覆盖率门禁（逻辑层收敛基线）----
    // 口径修正（2026-09-05）：原 include 为 src/**/*.{js,vue}，但项目实际为 TypeScript
    // （composables/stores/utils/api/router 均为 .ts），include 漏掉 .ts 导致门禁只统计到
    // 287 个 .vue 视图组件（大量 0% 覆盖，lines 仅 2.78%），而真正被测试的逻辑层反被排除。
    // 现收窄口径到「逻辑层 .ts」：287 个 .vue 视图组件（views/components/layouts 的 UI 渲染代码）
    // 单元测试投入产出比低，真正可测试的业务逻辑集中在 .ts 层。此为业界常规做法。
    // 逻辑层实测基线（63 个 .ts，2026-09-05）：lines 8.3% / functions 25.7% / branches 64.4% / statements 8.3%。
    // 2026-10-04 扩充到 103 个 .ts 后重测，阈值按新实测值上调（见下方 thresholds 注释）。
    // 阈值取基线下限留缓冲，遵循"只许上调、不得下调"；目标随逻辑层测试补充逐步上调。
    // CI 使用方式：frontend-quality job 追加 `npm run test:coverage`。
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary'],
      // 本地（WorkBuddy 沙箱）文件删除保护会拦截 vitest 覆盖率清理步骤导致测试中断，
      // 故关闭 clean；CI（ubuntu 裸环境）无此限制、每次全新 checkout 结果等价。
      clean: false,
      include: [
        'src/api/**/*.ts',
        'src/composables/**/*.ts',
        'src/stores/**/*.ts',
        'src/utils/**/*.ts',
        'src/router/**/*.ts',
        'src/config/**/*.ts',
        'src/monitoring/**/*.ts',
        'src/layouts/**/*.ts',
      ],
      exclude: ['src/main.ts', 'src/utils/logger.ts', 'src/**/__tests__/**', 'src/**/*.d.ts'],
      thresholds: {
        // 基线 2026-10-04 重测（1092 用例 / 54 文件全绿）：
        //   lines 34.19 / statements 34.19 / functions 67.31 / branches 82.99
        // 上一档（2026-10-04 上午）：lines 20.35 / functions 50.59 / branches 76.58；
        // 再上一档（2026-09-05）：lines 8.3 / functions 25.7 / branches 64.4。
        // 棘轮制：只许上调、不得下调；下调需在评审中说明理由。
        lines: 30,
        functions: 62,
        branches: 78,
        statements: 30,
      },
    },
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
})
