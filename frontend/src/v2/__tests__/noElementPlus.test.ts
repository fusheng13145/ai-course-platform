import { describe, expect, it, vi } from 'vitest'
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * v2 侧零 Element Plus 依赖是旧端下线批（§11.5 L-04）摘除 EP 的前提条件。
 * 这条守卫把它从"评审时口头承诺"变成机械判据。
 */
const v2Root = join(__dirname, '..')

function sourceFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') continue
      out.push(...sourceFiles(full))
    } else if (/\.(vue|ts)$/.test(entry)) {
      out.push(full)
    }
  }
  return out
}

/** 注释里提到 EP 是常态（宿主适配器就在解释为什么），扫描前必须剥掉，否则判据会被说明文字误触 */
const stripComments = (source: string) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')

const hits = (source: string) =>
  ['element-plus', '<el-', 'el-button', 'ElMessage'].filter((needle) =>
    stripComments(source).includes(needle),
  )

describe('v2 不引入 Element Plus', () => {
  it('阴性对照：检测器必须能抓住人为植入的 EP 用法', () => {
    expect(hits('<template><el-button @click="go">ok</el-button></template>')).not.toEqual([])
    expect(hits("import { ElMessage } from 'element-plus'")).not.toEqual([])
    expect(hits('<template><div class="clean" /></template>')).toEqual([])
  })

  it('src/v2 下所有源文件对 EP 的引用为 0（并先证明扫描面非空）', () => {
    const files = sourceFiles(v2Root)
    expect(files.length, '扫描面为空则判据恒真').toBeGreaterThanOrEqual(40)
    const offenders = files
      .map((file) => ({
        file: file.replace(v2Root, 'src/v2').replace(/\\/g, '/'),
        found: hits(readFileSync(file, 'utf8')),
      }))
      .filter((item) => item.found.length > 0)
    expect(offenders, `这些 v2 文件仍引用 EP：${JSON.stringify(offenders)}`).toEqual([])
  })
})

/**
 * M-12a：v2 消费的共用组合式函数与展示原语同样不得静态引 EP。
 * 这批文件是「一处实现、两端薄映射」的载体（useListFeedback 的双态文案、
 * uploadDisplay 的状态语义、useChunkedUpload 的续传链路），一旦引回 EP，
 * v2 又长不出第二套实现，摘 EP 的前提再次失效。
 */
describe('v2 消费的共用件不绑定 Element Plus', () => {
  it('useListFeedback／uploadDisplay／useChunkedUpload 的 EP 引用为 0', () => {
    const files = [
      join(__dirname, '../../composables/useListFeedback.ts'),
      join(__dirname, '../../composables/useClientPagination.ts'),
      join(__dirname, '../../utils/uploadDisplay.ts'),
      join(__dirname, '../../composables/useChunkedUpload.ts'),
    ]
    const offenders = files
      .map((file) => ({
        file: file.replace(/\\/g, '/').split('/src/')[1],
        found: hits(readFileSync(file, 'utf8')),
      }))
      .filter((item) => item.found.length > 0)
    expect(offenders, `这些共用件仍绑着 EP：${JSON.stringify(offenders)}`).toEqual([])
  })
})

/**
 * L-04 的真实欠账：v2 页面复用了旧端的编排 composable，这批共用件仍静态引 EP，
 * 于是 v2 通过依赖图传递依赖 EP——「摘掉旧端就能摘 EP」要到这批文件逐个断边才成立。
 * 清单只许收缩：每迁走/解绑一个就删一行，新增一条 EP 传递边则直接判红。
 * 口径＝从 src/v2 出发沿 `@/x` 引用做闭包遍历（含二跳以上），与
 * `docs/前端待办项.md` §11.5 记的「一跳 4 个／闭包 6 个」同一算法。
 */
const KNOWN_EP_BOUND = [
  'composables/useQuestionBank.ts',
  'composables/useStudentExamTimers.ts',
  'composables/useStudentExams.ts',
  'composables/useSubmissionGrading.ts',
  'composables/useTeacherGrades.ts',
  'utils/dangerConfirm.ts',
]

function sharedImportsOf(file: string): string[] {
  const source = stripComments(readFileSync(file, 'utf8'))
  const out: string[] = []
  for (const match of source.matchAll(/from\s+'@\/(?!v2)([^']+)'/g)) {
    const target = join(__dirname, '../..', match[1])
    for (const candidate of [`${target}.ts`, `${target}.vue`, join(target, 'index.ts')]) {
      if (existsSync(candidate)) out.push(candidate)
    }
  }
  return out
}

describe('v2 依赖图里的 EP 传递边只许收缩', () => {
  it('闭包内静态引 EP 的共用件必须全部在挂账清单内', () => {
    const seen = new Set<string>()
    const queue = sourceFiles(v2Root).flatMap(sharedImportsOf)
    while (queue.length) {
      const file = queue.pop() as string
      if (seen.has(file)) continue
      seen.add(file)
      queue.push(...sharedImportsOf(file))
    }
    expect(seen.size, '闭包为空则判据恒真').toBeGreaterThanOrEqual(40)
    const offenders = [...seen]
      .map((file) => relative(join(__dirname, '../..'), file).replace(/\\/g, '/'))
      .filter((relPath) => hits(readFileSync(join(__dirname, '../..', relPath), 'utf8')).length > 0)
      .sort()
    const outside = offenders.filter((file) => !KNOWN_EP_BOUND.includes(file))
    expect(outside, `这些 v2 依赖又接上了 EP：${JSON.stringify(outside)}`).toEqual([])
    // 双向钉住：某件解绑成功后这里会红，并把该件从清单删掉即可（棘轮只朝收缩方向）
    expect(offenders, `与挂账清单不一致：解绑成功的请从 KNOWN_EP_BOUND 删除`).toEqual(
      [...KNOWN_EP_BOUND].sort(),
    )
  })
})

/**
 * M-21：HTTP 与数据层必须与 EP 解绑。这一层两端共用，只要它静态引 EP，
 * v2 每个页面就都传递依赖 EP，「旧端下线即摘除 EP」便不成立。
 * 旧端适配器放在扫描面之外（src/bootstrap/apiHostEp.ts），下线时摘一处即可。
 */
describe('共用数据层不绑定 Element Plus 与旧端路由', () => {
  it('src/api 与 utils/response 的静态引用为 0（扫描面非空）', () => {
    const roots = [join(__dirname, '../../api'), join(__dirname, '../../utils/response.ts')]
    const files: string[] = []
    for (const root of roots) {
      if (statSync(root).isDirectory()) files.push(...sourceFiles(root))
      else files.push(root)
    }
    expect(files.length, '扫描面为空则判据恒真').toBeGreaterThanOrEqual(40)
    const needles = ['element-plus', 'ElMessage', "from '@/router'"]
    const offenders = files
      .map((file) => {
        const source = stripComments(readFileSync(file, 'utf8'))
        return {
          file: file.replace(/\\/g, '/').split('/src/')[1],
          found: needles.filter((needle) => source.includes(needle)),
        }
      })
      .filter((item) => item.found.length > 0)
    expect(offenders, `这些共用层文件仍绑着 EP 或旧路由：${JSON.stringify(offenders)}`).toEqual([])
  })

  it('未注册宿主时的降级不静默，且导航地址可判定（jsdom 下 assign 不可重定义，故测纯函数）', async () => {
    const { setApiHost, apiHost, isFallbackHost, fallbackLocationFor } =
      await import('../../api/apiHost')
    expect(isFallbackHost(), '本用例要求宿主仍为降级态').toBe(true)
    expect(fallbackLocationFor({ path: '/login', query: { redirect: '/admin/users' } })).toBe(
      '/login?redirect=%2Fadmin%2Fusers',
    )
    expect(fallbackLocationFor('/v2/login')).toBe('/v2/login')
    apiHost().message.warning('服务暂时不可用，正在重试...')

    setApiHost({
      message: { error: vi.fn(), warning: vi.fn(), info: vi.fn(), success: vi.fn() },
      push: vi.fn(),
    })
    expect(isFallbackHost(), '注册后不应仍判为降级态').toBe(false)
  })
})
