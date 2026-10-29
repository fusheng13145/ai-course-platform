import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, type Ref } from 'vue'

const searchApi = vi.hoisted(() => ({ searchAll: vi.fn() }))
vi.mock('@/api/search', () => ({
  searchAll: searchApi.searchAll,
  default: { searchAll: searchApi.searchAll },
}))

const dataApi = vi.hoisted(() => ({ getCourses: vi.fn() }))
vi.mock('../data/api', () => dataApi)

const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {} }),
  useRouter: () => ({ push: router.push }),
}))

interface UiState {
  open: Ref<boolean>
  query: Ref<string>
  role: Ref<string>
  roleLabel: Ref<string>
}

// 命令面板的状态是 ui 组合式函数里的共享 ref：mock 必须给真 ref，否则 v-model 与渲染都不动
const ui = vi.hoisted(() => ({}) as UiState)
vi.mock('../composables/ui', async () => {
  const { ref: makeRef } = await import('vue')
  ui.open = makeRef(false)
  ui.query = makeRef('')
  ui.role = makeRef('student')
  ui.roleLabel = makeRef('学生')
  return {
    useV2Ui: () => ({
      paletteOpen: ui.open,
      paletteQuery: ui.query,
      role: ui.role,
      roleLabel: ui.roleLabel,
    }),
  }
})

import V2Palette from '../components/V2Palette.vue'

interface Hit {
  type: string
  id: number
  title: string
  snippet: string
  score: number
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const mounted: VueWrapper[] = []

function mountPalette() {
  const wrapper = mount(V2Palette)
  mounted.push(wrapper)
  return wrapper
}

/** 键入关键词并等防抖窗口过去 */
async function typeKeyword(wrapper: VueWrapper, keyword: string) {
  const input = wrapper.find('input')
  await input.setValue(keyword)
  await nextTick()
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  dataApi.getCourses.mockResolvedValue([])
  searchApi.searchAll.mockResolvedValue([])
  ui.open.value = true
  ui.query.value = ''
  ui.role.value = 'student'
})

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount()
  ui.open.value = false
  ui.query.value = ''
  vi.useRealTimers()
})

describe('v2 命令面板接入服务端全站检索', () => {
  it('服务端命中原样出现在「全站检索」分组，本地导航结果并存', async () => {
    searchApi.searchAll.mockResolvedValue([
      { type: 'course', id: 4007, title: '计算机网络', snippet: '课程概览', score: 0.92 } as Hit,
    ])
    const wrapper = mountPalette()
    await typeKeyword(wrapper, '课')

    // 防抖窗口内不发请求，停顿后才打一次
    expect(searchApi.searchAll).not.toHaveBeenCalled()
    vi.advanceTimersByTime(250)
    await flushPromises()

    expect(searchApi.searchAll).toHaveBeenCalledTimes(1)
    expect(searchApi.searchAll).toHaveBeenCalledWith('课')
    expect(wrapper.text()).toContain('导航与课程')
    expect(wrapper.text()).toContain('全站检索')
    expect(wrapper.text()).toContain('计算机网络')
    // 本地命中没有被服务端结果挤掉
    expect(wrapper.text()).toContain('我的课程')
    expect(wrapper.find('.palette-status').exists()).toBe(false)
  })

  it('失败与空态是两种可读状态，失败可重试', async () => {
    searchApi.searchAll.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountPalette()
    await typeKeyword(wrapper, '不存在词')
    vi.advanceTimersByTime(250)
    await flushPromises()

    const failed = wrapper.find('.palette-status.is-error')
    expect(failed.exists()).toBe(true)
    expect(failed.text()).toContain('检索服务暂不可用')
    expect(wrapper.text()).not.toContain('没有匹配结果')
    expect(wrapper.find('.palette-retry').exists()).toBe(true)

    await wrapper.find('.palette-retry').trigger('click')
    await flushPromises()

    expect(searchApi.searchAll).toHaveBeenCalledTimes(2)
    expect(searchApi.searchAll).toHaveBeenLastCalledWith('不存在词')
    expect(wrapper.find('.palette-status.is-error').exists()).toBe(false)
    expect(wrapper.find('.palette-status').text()).toContain('没有匹配结果')
  })

  it('空关键词零请求，清空在途防抖同样不发请求', async () => {
    const wrapper = mountPalette()
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(searchApi.searchAll).not.toHaveBeenCalled()

    await typeKeyword(wrapper, '网络')
    await typeKeyword(wrapper, '')
    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(searchApi.searchAll).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('没有匹配结果')
  })

  it('过期响应不覆盖新关键词的结果', async () => {
    const first = deferred<Hit[]>()
    const second = deferred<Hit[]>()
    searchApi.searchAll.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const wrapper = mountPalette()
    await typeKeyword(wrapper, '网')
    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(searchApi.searchAll).toHaveBeenCalledTimes(1)

    await typeKeyword(wrapper, '网络')
    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(searchApi.searchAll).toHaveBeenCalledTimes(2)

    // 新请求先回，旧请求后回：旧结果必须整包丢弃
    second.resolve([{ type: 'course', id: 2, title: '新命中', snippet: '', score: 1 }])
    await flushPromises()
    expect(wrapper.text()).toContain('新命中')

    first.resolve([{ type: 'course', id: 1, title: '旧命中', snippet: '', score: 1 }])
    await flushPromises()
    expect(wrapper.text()).toContain('新命中')
    expect(wrapper.text()).not.toContain('旧命中')
  })

  it('服务端命中按类型落到 v2 路由，回车沿扁平结果选择', async () => {
    searchApi.searchAll.mockResolvedValue([
      { type: 'course', id: 4007, title: '计算机网络', snippet: '课程概览', score: 0.9 } as Hit,
    ])
    const wrapper = mountPalette()
    await typeKeyword(wrapper, '网络')
    vi.advanceTimersByTime(250)
    await flushPromises()

    await wrapper.find('input').trigger('keydown', { key: 'ArrowDown' })
    await wrapper.find('input').trigger('keydown', { key: 'Enter' })
    expect(router.push).toHaveBeenCalledWith('/v2/student/courses/4007/overview')
  })

  it('24 条本地命中并存时，检索失败状态仍排在结果区最前，无需滚动即可见', async () => {
    dataApi.getCourses.mockResolvedValue(
      Array.from({ length: 24 }, (_, i) => ({
        id: 5000 + i,
        name: `课程 ${i + 1}`,
        code: `C${i + 1}`,
      })),
    )
    // 从关闭态打开面板，让 getCourses 真正灌满本地课程条目
    ui.open.value = false
    const wrapper = mountPalette()
    ui.open.value = true
    await flushPromises()

    searchApi.searchAll.mockRejectedValueOnce(new Error('503'))
    await typeKeyword(wrapper, '课')

    // 本地命中已顶到 24 条上限
    expect(wrapper.findAll('.palette-item')).toHaveLength(24)

    vi.advanceTimersByTime(250)
    await flushPromises()

    const results = wrapper.find('.palette-results')
    const status = results.find('.palette-status.is-error')
    expect(status.exists()).toBe(true)
    expect(status.text()).toContain('检索服务暂不可用')
    expect(status.text()).toContain('重试')
    // 状态条是结果区第一个子节点：330px 滚动容器里不会被 24 条命中挤到折叠线以下
    expect(results.element.firstElementChild).toBe(status.element)
  })

  it('服务端响应落地时不重置键盘选中，回车仍打开原条目', async () => {
    const pending = deferred<Hit[]>()
    searchApi.searchAll.mockReturnValueOnce(pending.promise)

    const wrapper = mountPalette()
    await typeKeyword(wrapper, '学')
    vi.advanceTimersByTime(250)
    await flushPromises()
    expect(searchApi.searchAll).toHaveBeenCalledTimes(1)

    // 请求在途时用 ↓ 选中第 3 条本地命中：学习任务
    await wrapper.find('input').trigger('keydown', { key: 'ArrowDown' })
    await wrapper.find('input').trigger('keydown', { key: 'ArrowDown' })
    expect(wrapper.find('.palette-item.is-active').text()).toContain('学习任务')

    pending.resolve([{ type: 'course', id: 4007, title: '服务端命中', snippet: '', score: 1 }])
    await flushPromises()

    // 服务端结果追加后，选中项仍停在原条目
    const active = wrapper.find('.palette-item.is-active')
    expect(active.text()).toContain('学习任务')
    expect(active.text()).not.toContain('服务端命中')

    await wrapper.find('input').trigger('keydown', { key: 'Enter' })
    expect(router.push).toHaveBeenCalledWith('/v2/student/tasks')
  })

  it('课程命中缺少 id 时不可点，重复缺 id 的命中也能共存', async () => {
    searchApi.searchAll.mockResolvedValueOnce([
      { type: 'course', id: null, title: '空 ID 课程', snippet: '', score: 0.9 },
      { type: 'course', id: null, title: '另一个空 ID 课程', snippet: '', score: 0.8 },
    ])
    const wrapper = mountPalette()
    await typeKeyword(wrapper, '空')
    vi.advanceTimersByTime(250)
    await flushPromises()

    expect(wrapper.html()).not.toContain('/courses/null/overview')
    const disabled = wrapper.findAll('.palette-item.is-disabled')
    expect(disabled.length).toBe(2)
    expect(disabled[0].text()).toContain('空 ID 课程')
    expect(disabled[0].text()).toContain('暂无可打开入口')
    expect(disabled[1].text()).toContain('另一个空 ID 课程')

    await wrapper.find('input').trigger('keydown', { key: 'Enter' })
    expect(router.push).not.toHaveBeenCalled()
  })

  it('管理员侧的笔记命中展示但不可点，未知类型同理', async () => {
    ui.role.value = 'admin'
    searchApi.searchAll.mockResolvedValue([
      { type: 'note', id: 9, title: '课堂笔记', snippet: '第三章', score: 0.8 } as Hit,
      { type: 'unknown', id: 10, title: '未知命中', snippet: '', score: 0.1 } as Hit,
    ])
    const wrapper = mountPalette()
    await typeKeyword(wrapper, '笔记')
    vi.advanceTimersByTime(250)
    await flushPromises()

    const disabled = wrapper.findAll('.palette-item.is-disabled')
    expect(disabled.length).toBe(2)
    expect(disabled[0].attributes('aria-disabled')).toBe('true')
    expect(disabled[0].text()).toContain('课堂笔记')
    expect(disabled[0].text()).toContain('暂无可打开入口')

    await disabled[0].trigger('click')
    await wrapper.find('input').trigger('keydown', { key: 'Enter' })
    expect(router.push).not.toHaveBeenCalled()
  })
})
