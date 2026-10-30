import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useResourceManagement（课程资源管理逻辑）单元测试
 *
 * <p>本文件此前 0% 覆盖，且承载 P0-5 缺陷修复：`/resources/upload` 声明
 * multipart/form-data，旧实现 `fd.append('courseId', selectedCourseId.value)` 在未选课程时
 * 把 null 序列化成字符串 "null" 上报，后端按 Long 解析必然 400，界面只显示一句无信息量的
 * 「上传失败」。现在组合式函数在本地拒发并明确提示，API 层 buildUploadForm 再兜一道
 * （普通对象与 FormData 都归一化成合法 FormData，courseId 缺失/"null" 抛可读错误）。</p>
 *
 * <p>这里刻意只 mock 传输层 `@/api/request`，让真实的 `@/api/resource`（含 buildUploadForm）
 * 参与执行，从而把「组合式函数 → API 归一化 → 实际发出的 multipart 载荷」整条链路钉住。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  del: vi.fn(),
  getAllCourses: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
  confirm: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    patch: mocks.patch,
    delete: mocks.del,
  },
}))
vi.mock('@/api/course', () => ({ default: { getAllCourses: mocks.getAllCourses } }))
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm },
}))

// 浮层宿主接缝：element-plus 已被上方 mock 替换，注册 EP 适配器后既有断言语义不变。
import { setOverlayHost } from '@/bootstrap/overlayHost'
import { epOverlayHost } from '@/bootstrap/overlayHostEp'
setOverlayHost(epOverlayHost)

import { useResourceManagement } from '@/composables/useResourceManagement'
import resourceApi from '@/api/resource'

type Resource = { id: number; fileName?: string; fileType?: string; visibleToAll?: boolean }

function withSetup() {
  let state!: ReturnType<typeof useResourceManagement>
  mount(
    defineComponent({
      setup() {
        state = useResourceManagement()
        return () => h('div')
      },
    }),
  )
  return { state }
}

const file = (name = '第一章课件.pdf') => new File(['abc'], name, { type: 'application/pdf' })

/** selectedCourseId 由 `ref(null)` 推导为 Ref<null>，测试注入真实课程 id 需绕过该字面量类型 */
function pickCourse(state: { selectedCourseId: { value: unknown } }, id: unknown) {
  state.selectedCourseId.value = id
}

describe('useResourceManagement — 上传链路（P0-5 回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllCourses.mockResolvedValue([])
    mocks.get.mockResolvedValue({ data: [] })
    mocks.post.mockResolvedValue({ code: 200 })
  })

  it('已选课程时发出合法 multipart：courseId 是字符串 id、file 部件存在', async () => {
    const { state } = withSetup()
    pickCourse(state, 7)

    await state.doUpload({ file: file(), description: '第一章课件' })

    expect(mocks.post).toHaveBeenCalledTimes(1)
    const [url, body, config] = mocks.post.mock.calls[0] as [
      string,
      FormData,
      { headers: Record<string, string> },
    ]
    expect(url).toBe('/resources/upload')
    expect(body).toBeInstanceOf(FormData)
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
    // 旧实现在未选课程时会发出 "null"，这里必须是真实课程 id 的字符串形态
    expect(body.get('courseId')).toBe('7')
    expect((body.get('file') as File).name).toBe('第一章课件.pdf')
    expect(body.get('description')).toBe('第一章课件')
    expect(mocks.message.success).toHaveBeenCalledWith('上传成功')
    expect(state.showUpload.value).toBe(false)
    expect(state.uploading.value).toBe(false)
    // 上传成功后必须刷新列表
    expect(mocks.get).toHaveBeenCalledWith('/resources/course/7')
  })

  it('未选课程：本地拒发并给出可读提示，绝不发出请求', async () => {
    const { state } = withSetup()

    await state.doUpload({ file: file(), description: '' })

    expect(mocks.post).not.toHaveBeenCalled()
    expect(mocks.message.warning).toHaveBeenCalledWith('请先选择所属课程后再上传')
    expect(state.uploading.value).toBe(false)
  })

  it('未选文件：本地拒发，绝不把 undefined 塞进 FormData 变成 "undefined" 分片', async () => {
    // 缺陷回归（2026-10-04）：旧实现直接 `fd.append('file', payload.file)`，
    // 未选文件时上报 9 字节的文本分片 "undefined"，后端报「上传失败」却看不出原因。
    const { state } = withSetup()
    pickCourse(state, 9)

    await state.doUpload({ description: '只有说明没有文件' })

    expect(mocks.post).not.toHaveBeenCalled()
    expect(mocks.message.warning).toHaveBeenCalledWith('请先选择要上传的文件')
    expect(state.uploading.value).toBe(false)
  })

  it('courseId 被污染成字符串 "null" 时由 API 层 buildUploadForm 二次拦截，错误信息可读', async () => {
    const { state } = withSetup()
    pickCourse(state, 'null')

    await state.doUpload({ file: file(), description: '' })

    expect(mocks.post).not.toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('上传失败：请先选择所属课程后再上传')
    expect(state.uploading.value).toBe(false)
  })

  it('上传接口失败时把后端 message 透出来，而不是只说「上传失败」', async () => {
    mocks.post.mockRejectedValue({ response: { data: { message: '文件类型不受支持' } } })
    const { state } = withSetup()
    pickCourse(state, 3)

    await state.doUpload({ file: file(), description: 'x' })

    expect(mocks.message.error).toHaveBeenCalledWith('上传失败：文件类型不受支持')
    expect(state.uploading.value).toBe(false)
  })

  it('buildUploadForm：普通对象入参（CourseResourcePanel 形态）也能转成合法 FormData', () => {
    const fd = resourceApi.buildUploadForm({
      file: file('a.docx'),
      courseId: 12,
      description: '讲稿',
    })

    expect(fd).toBeInstanceOf(FormData)
    expect(fd.get('courseId')).toBe('12')
    expect(fd.get('description')).toBe('讲稿')
    expect((fd.get('file') as File).name).toBe('a.docx')
  })

  it('buildUploadForm：FormData 入参原样归一化，visibleToAll 只在显式给出时携带', () => {
    const raw = new FormData()
    raw.append('file', file('b.pptx'))
    raw.append('courseId', '42')

    const fd = resourceApi.buildUploadForm(raw)
    expect(fd.get('courseId')).toBe('42')
    expect(fd.has('visibleToAll')).toBe(false)

    const withFlag = resourceApi.buildUploadForm({
      file: file('b.pptx'),
      courseId: 42,
      visibleToAll: false,
    })
    expect(withFlag.get('visibleToAll')).toBe('false')
  })

  it('buildUploadForm：缺文件 / courseId 非法（空、null、"undefined"）一律抛可读错误', () => {
    expect(() => resourceApi.buildUploadForm({ courseId: 3 })).toThrow('请选择要上传的文件')
    expect(() => resourceApi.buildUploadForm({ file: file(), courseId: null })).toThrow(
      '请先选择所属课程后再上传',
    )
    expect(() => resourceApi.buildUploadForm({ file: file(), courseId: '' })).toThrow(
      '请先选择所属课程后再上传',
    )
    const bad = new FormData()
    bad.append('file', file())
    bad.append('courseId', 'null')
    expect(() => resourceApi.buildUploadForm(bad)).toThrow('请先选择所属课程后再上传')
  })

  it('uploadResource：普通对象经归一化后才真正发出请求（API 层收敛）', async () => {
    await resourceApi.uploadResource({ file: file('c.pdf'), courseId: 8 })

    const [, body] = mocks.post.mock.calls[0] as [string, FormData]
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('courseId')).toBe('8')
  })
})

describe('useResourceManagement — 课程与资源加载', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ data: [] })
  })

  it('挂载即拉课程，并自动选中首门课程后加载其资源', async () => {
    mocks.getAllCourses.mockResolvedValue([{ id: 1, name: '数据结构' }])
    const { state } = withSetup()
    await flushPromises()

    expect(mocks.getAllCourses).toHaveBeenCalledOnce()
    expect(state.courseList.value).toEqual([{ id: 1, name: '数据结构' }])
    expect(state.selectedCourseId.value).toBe(1)
    expect(state.currentCourseName.value).toBe('数据结构')
    expect(mocks.get).toHaveBeenCalledWith('/resources/course/1')
  })

  it('课程 title 别名用于课程名展示', async () => {
    mocks.getAllCourses.mockResolvedValue([{ id: 2, title: '计算机网络' }])
    const { state } = withSetup()
    await flushPromises()
    expect(state.courseList.value).toHaveLength(1)
    // title 别名同样用于课程名展示
    expect(state.currentCourseName.value).toBe('计算机网络')
  })

  it('课程 API 已归一化分页结果后再驱动资源加载', async () => {
    mocks.getAllCourses.mockResolvedValue([{ id: 6, name: '数据库系统' }])
    const { state } = withSetup()
    await flushPromises()

    expect(state.courseList.value).toEqual([{ id: 6, name: '数据库系统' }])
    expect(state.selectedCourseId.value).toBe(6)
  })

  it('课程接口失败时清空列表而不中断页面', async () => {
    mocks.getAllCourses.mockRejectedValue(new Error('500'))
    const { state } = withSetup()
    await flushPromises()

    expect(state.courseList.value).toEqual([])
    expect(state.selectedCourseId.value).toBeNull()
    // 无选中课程时不发资源请求
    expect(mocks.get).not.toHaveBeenCalled()
  })

  /**
   * loadResources / loadCourses 未从组合式函数返回（见汇报的遗留缺陷），
   * 因此这里统一走「挂载 → 自动选中首课 → 加载资源」的真实入口。
   */
  async function mountWithResources(payload: unknown) {
    mocks.getAllCourses.mockResolvedValue([{ id: 5, name: '操作系统' }])
    mocks.get.mockResolvedValue(payload as never)
    const state = withSetup().state
    await flushPromises()
    return state
  }

  it('资源加载：裸数组/records/list 三种形状都落到 resources', async () => {
    const shapes: Array<[unknown, unknown[]]> = [
      [{ data: { records: [{ id: 1, fileName: 'a.pdf' }] } }, [{ id: 1, fileName: 'a.pdf' }]],
      [{ data: { list: [{ id: 2 }] } }, [{ id: 2 }]],
      [{ data: [{ id: 3 }] }, [{ id: 3 }]],
      [{ data: {} }, []],
      // 空 records 不得吞掉 list 形状的数据（P1-10 反方向）
      [{ data: { records: [], list: [{ id: 7 }] } }, [{ id: 7 }]],
      // 空 list 同样不得吞掉 records 形状的数据
      [{ data: { list: [], records: [{ id: 8 }] } }, [{ id: 8 }]],
    ]

    for (const [payload, expected] of shapes) {
      const state = await mountWithResources(payload)
      expect(state.selectedCourseId.value).toBe(5)
      expect(state.resources.value).toEqual(expected)
      expect(state.loading.value).toBe(false)
    }
  })

  it('资源接口失败：置空 + 报错 + 结束 loading', async () => {
    mocks.getAllCourses.mockResolvedValue([{ id: 5, name: '操作系统' }])
    mocks.get.mockImplementation((url: string) =>
      url.startsWith('/resources/course')
        ? Promise.reject(new Error('500'))
        : Promise.resolve({ data: [] }),
    )
    const { state } = withSetup()
    await flushPromises()

    expect(state.resources.value).toEqual([])
    expect(mocks.message.error).toHaveBeenCalledWith('加载资源失败')
    expect(state.loading.value).toBe(false)
  })

  it('无可选课程时不自动选中，也不发资源请求', async () => {
    mocks.getAllCourses.mockResolvedValue([])
    const { state } = withSetup()
    await flushPromises()

    expect(state.selectedCourseId.value).toBeNull()
    expect(mocks.get).not.toHaveBeenCalled()
    expect(state.resources.value).toEqual([])
  })
})

describe('useResourceManagement — 筛选与统计', () => {
  async function setupWithResources() {
    mocks.getAllCourses.mockResolvedValue([])
    mocks.get.mockResolvedValue({ data: [] })
    const { state } = withSetup()
    pickCourse(state, 1)
    state.resources.value = [
      { id: 1, fileName: 'Chapter1.pdf', fileType: 'application/pdf', visibleToAll: true },
      { id: 2, fileName: 'diagram.png', fileType: 'image/png', visibleToAll: false },
      { id: 3, name: 'lecture', fileType: 'video/mp4', visibleToAll: true },
    ] as never
    return state
  }

  it('resStats 按类型归类并区分公开数', async () => {
    const state = await setupWithResources()

    expect(state.resStats.value).toEqual({ total: 3, publicCount: 2, docCount: 1, mediaCount: 1 })
  })

  it('typeFilter 与关键词过滤（匹配 fileName/name/description，大小写不敏感）', async () => {
    const state = await setupWithResources()

    state.typeFilter.value = 'image'
    expect(state.filteredResources.value).toHaveLength(1)

    state.typeFilter.value = ''
    state.keyword.value = '  LECTURE '
    expect(state.filteredResources.value.map((r: Resource) => r.id)).toEqual([3])

    state.keyword.value = 'chapter1'
    expect(state.filteredResources.value.map((r: Resource) => r.id)).toEqual([1])
  })
})

describe('useResourceManagement — 可见性/下载/删除', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllCourses.mockResolvedValue([])
    mocks.get.mockResolvedValue({ data: [] })
    mocks.patch.mockResolvedValue({ code: 200 })
    mocks.del.mockResolvedValue({ code: 200 })
    window.URL.createObjectURL = vi.fn(() => 'blob:fake')
    window.URL.revokeObjectURL = vi.fn()
    // 真实 a.click() 会让 jsdom 尝试导航并抛「Not implemented」噪声
    vi.spyOn(window.HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('切换可见性命中资源接口并乐观更新行状态', async () => {
    const { state } = withSetup()
    const row: Record<string, unknown> = { id: 9, fileName: 'a.pdf', visibleToAll: false }

    await state.toggleVisibility(row as never, true)

    expect(mocks.patch.mock.calls[0][0]).toBe('/resources/9/visibility')
    expect(row.visibleToAll).toBe(true)
    expect(mocks.message.success).toHaveBeenCalledWith('已设为公开')
  })

  it('切换失败只提示，不回滚成本地值', async () => {
    mocks.patch.mockRejectedValue(new Error('403'))
    const { state } = withSetup()
    const row: Record<string, unknown> = { id: 9, fileName: 'a.pdf', visibleToAll: true }

    await state.toggleVisibility(row as never, false)

    expect(row.visibleToAll).toBe(true)
    expect(mocks.message.error).toHaveBeenCalledWith('设置失败')
  })

  it('下载走 blob 资源接口并触发浏览器保存', async () => {
    mocks.get.mockResolvedValue({ data: new Blob(['x']) })
    const { state } = withSetup()

    await state.downloadRes({ id: 4, fileName: 'a.pdf' } as never)

    expect(mocks.get).toHaveBeenCalledWith('/resources/download/4', { responseType: 'blob' })
    expect(window.URL.createObjectURL).toHaveBeenCalled()
    expect(window.URL.revokeObjectURL).toHaveBeenCalled()
  })

  it('下载失败提示且不创建对象 URL', async () => {
    mocks.get.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await state.downloadRes({ id: 4, name: 'a' } as never)

    expect(window.URL.createObjectURL).not.toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('下载失败')
  })

  it('删除确认后调用 DELETE 并重载资源', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    const { state } = withSetup()
    pickCourse(state, 6)

    state.removeRes({ id: 3, fileName: 'old.ppt' } as never)
    await flushPromises()

    expect(mocks.del).toHaveBeenCalledWith('/resources/3')
    expect(mocks.message.success).toHaveBeenCalledWith('删除成功')
    expect(mocks.get).toHaveBeenCalledWith('/resources/course/6')
  })

  it('删除取消时不发请求也不报错', async () => {
    mocks.confirm.mockRejectedValue('cancel')
    const { state } = withSetup()

    state.removeRes({ id: 3, name: 'old' } as never)
    await flushPromises()

    expect(mocks.del).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('删除接口失败提示删除失败', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    mocks.del.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    state.removeRes({ id: 3, fileName: 'old.ppt' } as never)
    await flushPromises()

    expect(mocks.message.error).toHaveBeenCalledWith('删除失败')
  })
})
