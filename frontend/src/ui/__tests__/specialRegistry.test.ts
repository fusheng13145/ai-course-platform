import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { pageDef } from '../nav'
import { specialBodyFile, specialBodyLoader } from '../views/specials/registry'

/**
 * 全局页按命名约定加载 Body：special `users` → `./UsersBody.vue`。
 * 这里钉住：路径拼法（含驼峰）、空值/非法值、文件存在性与 nav 的 special 声明。
 * 文件不存在时 loader 返回 null，页面回落到示例块——这是「新增页面前行为不变」的判据。
 */
const REGISTRY = join(__dirname, '../views/specials/registry.ts')
const PAGE = join(__dirname, '../views/GlobalPage.vue')
const BODIES = Object.keys(import.meta.glob('../views/specials/*Body.vue'))

describe('全局页 Body 注册表', () => {
  it('合法 special 按首字母大写拼出文件路径', () => {
    expect(specialBodyFile('users')).toBe('./UsersBody.vue')
    expect(specialBodyFile('adminClasses')).toBe('./AdminClassesBody.vue')
  })

  it('驼峰化只改首字母，其余分段原样保留', () => {
    expect(specialBodyFile('teachingClasses')).toBe('./TeachingClassesBody.vue')
    expect(specialBodyFile('advisingNotices')).toBe('./AdvisingNoticesBody.vue')
    expect(specialBodyFile('examGovernance')).toBe('./ExamGovernanceBody.vue')
  })

  it('空值与非法名返回 null，不会拼出路径（含路径穿越）', () => {
    expect(specialBodyFile()).toBeNull()
    expect(specialBodyFile('')).toBeNull()
    expect(specialBodyFile('   ')).toBeNull()
    expect(specialBodyFile('Users')).toBeNull()
    expect(specialBodyFile('2fa')).toBeNull()
    expect(specialBodyFile('admin-classes')).toBeNull()
    expect(specialBodyFile('admin/classes')).toBeNull()
    expect(specialBodyFile('../Users')).toBeNull()
  })

  it('loader 只认已落地的 Body 文件：现存文件的 special 都能加载，其余为 null', () => {
    expect(BODIES.length, '扫描面为空则判据恒真').toBeGreaterThanOrEqual(3)
    for (const key of BODIES) {
      const base = key.split('/').pop() as string
      const special = base.charAt(0).toLowerCase() + base.slice(1, -'Body.vue'.length)
      expect(specialBodyFile(special), `${base} 的约定路径不一致`).toBe(`./${base}`)
      expect(typeof specialBodyLoader(special), `${base} 没有懒加载器`).toBe('function')
    }
    expect(specialBodyLoader('noSuchPageXyz')).toBeNull()
    expect(specialBodyLoader('admin-classes')).toBeNull()
    expect(specialBodyLoader()).toBeNull()
  })

  it('文件尚未落地时只有约定路径、没有 loader（全局页据此回落示例块）', () => {
    expect(specialBodyFile('noSuchPageXyz')).toBe('./NoSuchPageXyzBody.vue')
    expect(specialBodyLoader('noSuchPageXyz')).toBeNull()
  })

  it('nav 为待迁页面声明 special，且不顶掉原有 moduleId', () => {
    expect(pageDef('admin', 'users').special).toBe('users')
    expect(pageDef('admin', 'roles').special).toBe('roles')
    expect(pageDef('admin', 'semesters').special).toBe('semesters')
    expect(pageDef('student', 'tasks').special).toBe('tasks')
    // 批次 23：审计与会话接真后也走 special 约定，不再由「待扩展」空态接管
    expect(pageDef('admin', 'audit').special).toBe('audit')
    expect(pageDef('admin', 'sessions').special).toBe('sessions')
    expect(pageDef('admin', 'users').moduleId).toBe('users')
    expect(pageDef('student', 'tasks').moduleId).toBe('tasks')
  })

  it('注册表不引入 Element Plus / echarts', () => {
    const source = readFileSync(REGISTRY, 'utf8')
    expect(source).not.toContain('element-plus')
    expect(source).not.toContain('echarts')
  })

  it('全局页保留既有显式分支，异步组件之后仍有示例块兜底（含「示例数据」警示）', () => {
    const source = readFileSync(PAGE, 'utf8')
    // aiUsage 自 2026-11-04（M-14）按课程维度接真后，走 special 约定加载 AiUsageBody，
    // 不再占用显式分支，也不再由 notProvidedCopy 接管——下面两条断言钉住这个位置。
    for (const special of ['examGovernance', 'analytics', 'messages']) {
      expect(source, `显式分支 ${special} 被移除`).toContain(`def.special === '${special}'`)
    }
    // 全局 AI 助手已判下线（M-30），AiTab 是硬编码示例面板，随死码清理删除：
    // 源码里再出现它就说明有人把示例面板接回来了。
    expect(source, 'AiTab 死分支被重新引入').not.toContain('AiTab')
    expect(source, 'aiUsage 不该再被写成显式分支').not.toContain(`def.special === 'aiUsage'`)
    expect(source, '待扩展空态分支被移除').toContain('notProvidedCopy')
    expect(source, '缺少按约定加载的异步组件').toContain('specialBodyComponent')
    expect(source, '示例块兜底被移除').toContain('v-else-if="block"')
    expect(source, '示例块警示被移除').toContain('示例数据')
  })
})
