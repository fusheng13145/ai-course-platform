import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

// Mock axios and element-plus
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    closeAll: vi.fn(),
  },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn() },
  ElNotification: vi.fn(),
}))

/* ============================================================
 * API 模块导入测试 — 验证新创建的 5 个 API 模块可导入
 * ============================================================ */

describe('API 模块：userSession', () => {
  it('应正确导入所有导出函数', async () => {
    const api = await import('@/api/userSession')
    expect(api.getOnlineUsers).toBeDefined()
    expect(api.getUserSessions).toBeDefined()
    expect(api.forceLogout).toBeDefined()
    expect(api.getSessionStats).toBeDefined()
    expect(api.getAbnormalSessions).toBeDefined()
  })
})

describe('API 模块：plagiarismCheck', () => {
  it('应正确导入所有导出函数', async () => {
    const api = await import('@/api/plagiarismCheck')
    expect(api.createCheck).toBeDefined()
    expect(api.executeCheck).toBeDefined()
    expect(api.getCheckDetail).toBeDefined()
    expect(api.getCheckMatches).toBeDefined()
    expect(api.getCheckHistory).toBeDefined()
  })
})

describe('API 模块：fileUpload', () => {
  it('应正确导入所有导出函数', async () => {
    const api = await import('@/api/fileUpload')
    expect(api.createSession).toBeDefined()
    expect(api.getProgress).toBeDefined()
    expect(api.cancelUpload).toBeDefined()
    // 分片上传与合并端点已按 N-03 下线，不应再导出。
    // 注意：改用 `in` 判定而非读取属性——这两个标识符已从模块类型中移除，
    // 直接访问会触发 TS2339（会顶破 typecheck 棘轮基线）。
    expect('uploadChunk' in api).toBe(false)
    expect('mergeChunks' in api).toBe(false)
  })
})

describe('API 模块：reminder', () => {
  it('应正确导入所有导出函数', async () => {
    const api = await import('@/api/reminder')
    expect(api.createReminder).toBeDefined()
    expect(api.getTodos).toBeDefined()
    expect(api.updateReminder).toBeDefined()
    expect(api.cancelReminder).toBeDefined()
    expect(api.sendDue).toBeDefined()
  })
})

describe('API 模块：resourceVersion', () => {
  it('应正确导入所有导出函数', async () => {
    const api = await import('@/api/resourceVersion')
    expect(api.getVersionHistory).toBeDefined()
    expect(api.createVersion).toBeDefined()
    expect(api.rollbackVersion).toBeDefined()
    expect(api.getActiveVersion).toBeDefined()
  })
})

/* ============================================================
 * 前端测试 — 验证原有页面修复
 * ============================================================ */

describe('teacher/Dashboard.vue — Loading 状态', () => {
  const factory = async () => {
    // Mock APIs（与页面真实依赖对齐：teacherApi）
    vi.mock('@/api/teacher', () => ({
      default: {
        getTeacherDashboardV2: vi.fn(() => ({ data: { stats: {}, courses: [] } })),
        createCourse: vi.fn(() => ({ data: {} })),
      },
    }))
    const Dashboard = (await import('@/views/teacher/Dashboard.vue')).default
    return mount(Dashboard, { global: { stubs: ['v-chart', 'el-icon'] } })
  }

  it('应渲染统计卡片', async () => {
    const wrapper = await factory()
    // 统计卡片容器使用设计系统全局类 stat-grid（design-system.css）
    expect(wrapper.find('.stat-grid').exists()).toBe(true)
  })
})

/* ============================================================
 * API 模块引用完整性 —— 真实死代码检测
 *
 * 原用例断言「api/common 不应存在」，但比的是测试内硬编码的字符串数组，
 * 而 `src/api/common.ts` 实际存在（38 行、承载批量上传端点）——该断言
 * 与产品现状矛盾且永不感知新增的死模块。现改为扫描真实源码引用关系。
 * ============================================================ */

describe('API 模块引用完整性（死代码检测）', () => {
  const apiDir = resolve(process.cwd(), 'src/api')
  const srcDir = resolve(process.cwd(), 'src')

  /** 递归收集生产代码（排除测试与 api 目录自身的内部引用） */
  function collectSources(dir: string, acc: string[] = []): string[] {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = resolve(dir, entry.name)
      if (entry.isDirectory()) {
        if (entry.name === '__tests__' || entry.name === 'api') continue
        collectSources(full, acc)
      } else if (/\.vue$|\.ts$/.test(entry.name) && !entry.name.endsWith('.d.ts')) {
        acc.push(full)
      }
    }
    return acc
  }

  const productionSources = collectSources(srcDir).map((f) => readFileSync(f, 'utf8'))
  const apiModules = readdirSync(apiDir)
    .filter((f) => f.endsWith('.ts') && !f.endsWith('.d.ts'))
    .map((f) => f.replace(/\.ts$/, ''))

  it('api 目录确实被扫描到（防止路径口径失效导致空转通过）', () => {
    expect(apiModules.length).toBeGreaterThan(30)
    expect(productionSources.length).toBeGreaterThan(100)
  })

  it.each(apiModules.filter((m) => !['request', 'requestKey'].includes(m)))(
    'api/%s 至少被一处生产代码引用（无死模块）',
    (moduleName) => {
      const ref = new RegExp(`@/api/${moduleName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`)
      expect(
        productionSources.some((text) => ref.test(text)),
        `src/api/${moduleName}.ts 未被任何生产代码引用，属死代码`,
      ).toBe(true)
    },
  )

  it('api/common 存在且被引用（旧用例的错误结论在此纠正）', () => {
    expect(apiModules).toContain('common')
    expect(existsSync(resolve(apiDir, 'common.ts'))).toBe(true)
  })
})
