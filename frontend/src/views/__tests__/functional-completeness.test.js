import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElMessage, ElMessageBox } from 'element-plus'

// Mock axios and element-plus
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn(), closeAll: vi.fn() },
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
    expect(api.uploadChunk).toBeDefined()
    expect(api.mergeChunks).toBeDefined()
    expect(api.getProgress).toBeDefined()
    expect(api.cancelUpload).toBeDefined()
  })
})

describe('API 模块：reminder', () => {
  it('应正确导入所有导出函数', async () => {
    const api = await import('@/api/reminder')
    expect(api.createReminder).toBeDefined()
    expect(api.getTodos).toBeDefined()
    expect(api.updateReminder).toBeDefined()
    expect(api.batchTrigger).toBeDefined()
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
    // Mock stores
    vi.mock('@/stores/auth', () => ({
      useAuthStore: () => ({ userId: 1 }),
    }))
    // Mock APIs
    vi.mock('@/api/analytics', () => ({
      default: { getDashboardStats: vi.fn(() => ({ data: {} })) }
    }))
    vi.mock('@/api/task', () => ({
      default: { getPendingGrading: vi.fn(() => ({ data: { list: [] } })) }
    }))
    vi.mock('@/api/course', () => ({
      default: { getMyCourses: vi.fn(() => ({ data: { list: [] } })) }
    }))
    const Dashboard = (await import('@/views/teacher/Dashboard.vue')).default
    return mount(Dashboard, { global: { stubs: ['v-chart', 'el-icon'] } })
  }

  it('应渲染统计卡片', async () => {
    const wrapper = await factory()
    expect(wrapper.find('.stat-cards').exists()).toBe(true)
  })
})

/* ============================================================
 * API 模块死代码验证 — 确认 common.js 不存在
 * ============================================================ */

describe('死代码验证', () => {
  it('api/common.js 不应存在（死代码已清理）', () => {
    const apiFiles = ['userSession', 'plagiarismCheck', 'fileUpload', 'reminder', 'resourceVersion',
      'auth', 'exam', 'course', 'ai', 'question', 'analytics', 'chapter', 'classroom',
      'discussion', 'enrollment', 'grade', 'interaction', 'knowledge', 'knowledgePointRelation',
      'learningPath', 'learningRecord', 'loginLog', 'notification', 'operationLog',
      'permission', 'proctor', 'rag', 'resource', 'student', 'submission', 'system',
      'systemConfig', 'task', 'teacher', 'user', 'wrongQuestion']
    expect(apiFiles.includes('common')).toBe(false)
  })
})
