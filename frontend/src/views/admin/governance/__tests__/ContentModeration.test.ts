import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'

/**
 * D2-2：ContentModeration「同时封禁作者」对话框双关语义锁定。
 *
 * 该对话框不是普通的「确认/取消」：确认 = 同时封禁作者；取消/关闭 = 仅处置内容
 * （不是中止处置）。改造为 confirmDanger 后必须保持这一语义，并带上
 * 「影响范围 / 不可逆 / 恢复路径」三要素（恢复路径指向本页封禁名单）。
 */

const mocks = vi.hoisted(() => ({
  prompt: vi.fn(),
  confirm: vi.fn(),
  applyAction: vi.fn(),
  unban: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { prompt: mocks.prompt, confirm: mocks.confirm },
}))

vi.mock('@/composables/useModeration', () => ({
  useModeration: () => ({
    tab: ref('posts'),
    loading: ref(false),
    loadError: ref(''),
    actionError: ref(''),
    busy: ref(false),
    statusFilter: ref(''),
    keyword: ref(''),
    pageNo: ref(1),
    pageSize: ref(10),
    total: ref(1),
    posts: ref([
      {
        id: 7,
        content: '测试回复内容',
        authorName: '张三',
        moderationStatus: 'NORMAL',
        createdAt: '2026-10-14T10:00:00',
      },
    ]),
    resources: ref([]),
    bannedAuthors: ref([]),
    load: vi.fn(),
    setTab: vi.fn(),
    search: vi.fn(),
    applyAction: mocks.applyAction,
    unban: mocks.unban,
  }),
}))

import ContentModeration from '../ContentModeration.vue'

function mountView() {
  return mount(ContentModeration, {
    global: {
      stubs: {
        PageHeader: true,
        CollapsePanel: { template: '<div><slot /></div>' },
        'el-button': {
          props: ['disabled'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          emits: ['click'],
        },
        'el-select': { template: '<div class="select-stub" />' },
        'el-option': { template: '<div class="option-stub" />' },
        'el-input': { template: '<input class="input-stub" />' },
        'el-alert': { template: '<div class="alert-stub" />' },
      },
    },
  })
}

/** 点击某条回复的「删除」按钮（danger 型） */
async function clickDelete(wrapper: ReturnType<typeof mountView>) {
  const btn = wrapper.findAll('button').find((b) => b.text() === '删除')
  expect(btn, '应渲染出删除按钮').toBeTruthy()
  await btn!.trigger('click')
  await flushPromises()
}

describe('ContentModeration 封禁作者确认框（D2-2）', () => {
  beforeEach(() => {
    mocks.prompt.mockReset().mockResolvedValue({ value: 'reason-x' })
    mocks.confirm.mockReset()
    mocks.applyAction.mockReset().mockResolvedValue(true)
    mocks.unban.mockReset()
  })

  it('确认封禁 → applyAction 收到 banAuthor=true，且确认语含三要素', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()
    await clickDelete(wrapper)

    // 先问原因（prompt），再问是否封禁作者（confirm）
    expect(mocks.prompt).toHaveBeenCalledTimes(1)
    expect(mocks.confirm).toHaveBeenCalledTimes(1)

    const [message, title, options] = mocks.confirm.mock.calls[0]
    expect(message).toContain('张三') // 影响对象
    expect(message).toContain('无法登录') // 影响范围
    expect(message).toContain('此操作不可恢复') // 不可逆性
    expect(message).toContain('封禁名单') // 恢复路径
    expect(title).toBe('封禁确认')
    expect(options.confirmButtonText).toBe('同时封禁作者')
    expect(options.cancelButtonText).toBe('仅处置内容')

    // 关键语义：确认 = 封禁作者 + 继续处置内容
    expect(mocks.applyAction).toHaveBeenCalledWith(
      'discussion-posts',
      7,
      'DELETE',
      'reason-x',
      true,
    )
  })

  it('取消封禁 → 不中止处置，applyAction 收到 banAuthor=false', async () => {
    mocks.confirm.mockRejectedValue('cancel')
    const wrapper = mountView()
    await flushPromises()
    await clickDelete(wrapper)

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    // 关键语义：取消 ≠ 放弃处置内容，而是「仅处置内容」
    expect(mocks.applyAction).toHaveBeenCalledWith(
      'discussion-posts',
      7,
      'DELETE',
      'reason-x',
      false,
    )
  })

  it('隐藏（HIDE）同样询问封禁作者，取消则仅隐藏', async () => {
    mocks.prompt.mockResolvedValue({ value: 'reason-y' })
    mocks.confirm.mockRejectedValue('cancel')
    const wrapper = mountView()
    await flushPromises()

    // NORMAL 状态的帖子渲染「隐藏」按钮
    const hideBtn = wrapper.findAll('button').find((b) => b.text() === '隐藏')
    expect(hideBtn, '应渲染出隐藏按钮').toBeTruthy()
    await hideBtn!.trigger('click')
    await flushPromises()

    // HIDE 与 DELETE 同属会询问封禁的分支；取消 → 仅隐藏不封禁
    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.applyAction).toHaveBeenCalledWith('discussion-posts', 7, 'HIDE', 'reason-y', false)
  })
})
