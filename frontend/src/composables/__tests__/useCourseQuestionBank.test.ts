import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * useCourseQuestionBank（课程题库页逻辑）单元测试 —— fix-1a 字段契约错位回归。
 *
 * <p>后端写侧绑的是实体而不是 VO：`QuestionController.java:304`
 * `createBasic(@Valid @RequestBody Question)`、`:319` `update(@PathVariable Long, @Valid @RequestBody Question)`；
 * `entity/Question.java` 的字段名是 `content`（:38，带 `@NotBlank(message = "题干不能为空")`）、
 * `correctAnswers`（:48）、`explanation`（:68），**没有** stem / answer / analysis / reviewed 属性，
 * Jackson 会静默丢弃未知键。本组合式函数过去按 `{stem, answer, analysis}` 发 payload，
 * `content` 收到 null 触发校验 → 本页「新建题目」「编辑题目」必然吃 400。
 * 读侧同样返回实体（`QuestionController.java:65-68` list 用 LambdaQueryWrapper<Question> 直接分页），
 * 所以列表回显与编辑回填都必须读 content/correctAnswers/explanation。</p>
 *
 * <p>另外钉住两处弹窗缺陷：`resetForm` 未从组合式函数导出（`@closed` 拿到 undefined handler，
 * 关闭后编辑态与表单不重置），以及删除确认文案读旧键导致题目名恒空。</p>
 *
 * <p>刻意不覆盖：审核按钮的 DOM 渲染（需要真实 el-table，jsdom 下要连 ResizeObserver 一起桩，
 * 成本高于收益）。审核态本身已在下方「缺陷 B5」组内按数据层覆盖，展示层由
 * dialog-loading-directive 同款源码守卫钉住「不得再读不存在的 row.reviewed」。</p>
 */

const mocks = vi.hoisted(() => ({
  getQuestionList: vi.fn(),
  createQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  deleteQuestion: vi.fn(),
  reviewQuestion: vi.fn(),
  copyQuestion: vi.fn(),
  generateByAI: vi.fn(),
  // ElMessage 在源码里既被当函数调用又被当对象使用，用「可调用函数 + 挂载方法」建模
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
  confirm: vi.fn(),
  route: { params: { id: '42' }, path: '/teacher/courses/42/questions' },
}))

vi.mock('@/api/question', () => ({
  default: {
    getQuestionList: mocks.getQuestionList,
    createQuestion: mocks.createQuestion,
    updateQuestion: mocks.updateQuestion,
    deleteQuestion: mocks.deleteQuestion,
    reviewQuestion: mocks.reviewQuestion,
    copyQuestion: mocks.copyQuestion,
    generateByAI: mocks.generateByAI,
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm },
}))
// 页面壳（CourseQuestionBank.vue）用 useRoute 取课程 id。
// 必须保留 vue-router 的真实导出：本组合式函数复用 resolveReviewStatus（useQuestionBank.ts），
// 后者经 @/api/course → @/api/request → src/router/index.ts 触到 createRouter，
// 整体替换 mock 会让 router/index.ts 在收集阶段就报错。
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => mocks.route,
    useRouter: () => ({ push: vi.fn() }),
  }
})

import { useCourseQuestionBank } from '@/composables/useCourseQuestionBank'

const CourseQuestionBank = (await import('@/views/teacher/cockpit/CourseQuestionBank.vue')).default
const CqbQuestionTable = (await import('@/views/teacher/cockpit/components/CqbQuestionTable.vue'))
  .default
const CqbEditDialog = (await import('@/views/teacher/cockpit/components/CqbEditDialog.vue')).default

/** 后端 /questions 列表行的真实形状（entity/Question 序列化结果）。
 *  type/difficulty 为实库值（2026-10-26 F3 更正：*_CHOICE 风格 + 小写难度）。 */
const ENTITY_ROW = {
  id: 1,
  courseId: 42,
  type: 'SINGLE_CHOICE',
  difficulty: 'medium',
  content: '题干',
  correctAnswers: 'A',
  explanation: '解析',
  isActive: true,
}

function withSetup(courseId = '42') {
  let mod!: ReturnType<typeof useCourseQuestionBank>
  const courseIdRef = ref(courseId)
  mount(
    defineComponent({
      setup() {
        mod = useCourseQuestionBank(courseIdRef)
        return () => h('div')
      },
    }),
  )
  return { mod, courseIdRef }
}

/** 新建分支的表单填充：题干分别按新键与旧键写入。
 * 旧实现靠 `stem` 通过「请输入题干」校验，若只写新键会被校验短路而拿不到 payload，
 * 也就看不到「字段名错位」这一根因；修复后 save() 只显式组装 content/correctAnswers/explanation。 */
function fillBothKeyShapes(mod: ReturnType<typeof useCourseQuestionBank>) {
  Object.assign(mod.form, {
    content: '栈的特点是？',
    correctAnswers: 'A',
    explanation: '后进先出',
    stem: '栈的特点是？',
    answer: 'A',
    analysis: '后进先出',
  })
}

describe('useCourseQuestionBank — 写侧 payload 与实体契约对齐（缺陷 1）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getQuestionList.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mocks.createQuestion.mockResolvedValue({ code: 200, data: ENTITY_ROW })
    mocks.updateQuestion.mockResolvedValue({ code: 200, data: ENTITY_ROW })
    mocks.deleteQuestion.mockResolvedValue({ code: 200 })
    mocks.confirm.mockResolvedValue('confirm')
  })

  it('新建：POST 恰好发 content/correctAnswers/explanation + courseId/type/difficulty，不含 stem/answer/analysis', async () => {
    const { mod } = withSetup()
    mod.openCreate()
    fillBothKeyShapes(mod)

    await mod.save()

    expect(mocks.createQuestion).toHaveBeenCalledTimes(1)
    expect(mocks.updateQuestion).not.toHaveBeenCalled()
    const payload = mocks.createQuestion.mock.calls[0][0]
    expect(Object.keys(payload).sort()).toEqual([
      'content',
      'correctAnswers',
      'courseId',
      'difficulty',
      'explanation',
      'type',
    ])
    expect(payload.content).toBe('栈的特点是？')
    expect(payload.correctAnswers).toBe('A')
    expect(payload.explanation).toBe('后进先出')
    expect(payload).not.toHaveProperty('stem')
    expect(payload).not.toHaveProperty('answer')
    expect(payload).not.toHaveProperty('analysis')
    expect(mocks.message.success).toHaveBeenCalledWith('题目已创建')
  })

  it('编辑：PUT 同型且带被编辑题目 id（今天 content 为空串，请求根本发不出去）', async () => {
    const { mod } = withSetup()
    mod.openEdit({ ...ENTITY_ROW, id: 71, content: '旧题干' })
    mod.form.content = '新题干'

    await mod.save()

    expect(mocks.updateQuestion).toHaveBeenCalledTimes(1)
    expect(mocks.createQuestion).not.toHaveBeenCalled()
    const [id, payload] = mocks.updateQuestion.mock.calls[0]
    expect(id).toBe(71)
    expect(Object.keys(payload).sort()).toEqual([
      'content',
      'correctAnswers',
      'courseId',
      'difficulty',
      'explanation',
      'type',
    ])
    expect(payload.content).toBe('新题干')
    expect(payload).not.toHaveProperty('stem')
    expect(payload).not.toHaveProperty('answer')
    expect(payload).not.toHaveProperty('analysis')
    expect(mocks.message.success).toHaveBeenCalledWith('题目已更新')
  })

  it('题干为空（新键）时仍只做前端校验，不发任何写请求', async () => {
    const { mod } = withSetup()
    mod.openCreate()

    await mod.save()

    expect(mocks.message.warning).toHaveBeenCalledWith('请输入题干')
    expect(mocks.createQuestion).not.toHaveBeenCalled()
    expect(mocks.updateQuestion).not.toHaveBeenCalled()
  })
})

describe('useCourseQuestionBank — 编辑回填读实体键（缺陷 1 读侧）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getQuestionList.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  })

  it('openEdit 传入实体行时 content/correctAnswers/explanation 三项都取到值（旧实现恒空）', () => {
    const { mod } = withSetup()

    mod.openEdit({ ...ENTITY_ROW })

    expect(mod.form.id).toBe(1)
    expect(mod.form.type).toBe('SINGLE_CHOICE')
    expect(mod.form.difficulty).toBe('medium')
    expect(mod.form.content).toBe('题干')
    expect(mod.form.correctAnswers).toBe('A')
    expect(mod.form.explanation).toBe('解析')
    expect(mod.dialog.editing).toBe(true)
  })

  it('openEdit 保留旧键兜底链（stem/answer/analysis，照抄同仓 QbQuestionTable.vue:6 的写法）', () => {
    const { mod } = withSetup()

    mod.openEdit({
      id: 2,
      type: 'TRUE_FALSE',
      difficulty: 'easy',
      stem: '旧题干',
      answer: 'true',
      analysis: '旧解析',
    })

    expect(mod.form.content).toBe('旧题干')
    expect(mod.form.correctAnswers).toBe('true')
    expect(mod.form.explanation).toBe('旧解析')
  })

  it('openCreate 把三项表单清成空串，不残留上一行的数据', () => {
    const { mod } = withSetup()
    mod.openEdit({ ...ENTITY_ROW })

    mod.openCreate()

    expect(mod.form.content).toBe('')
    expect(mod.form.correctAnswers).toBe('')
    expect(mod.form.explanation).toBe('')
    expect(mod.dialog.editing).toBe(false)
  })
})

describe('useCourseQuestionBank — resetForm 导出与删除文案（缺陷 3 / 4）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getQuestionList.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mocks.deleteQuestion.mockResolvedValue({ code: 200 })
    mocks.confirm.mockResolvedValue('confirm')
  })

  it('组合式函数导出 resetForm，调用后编辑态与题干一并复位', () => {
    const { mod } = withSetup()
    expect(typeof mod.resetForm).toBe('function')

    mod.openEdit({ ...ENTITY_ROW })
    expect(mod.dialog.editing).toBe(true)
    expect(mod.form.content).toBe('题干')

    mod.resetForm()

    expect(mod.dialog.editing).toBe(false)
    expect(mod.form.id).toBeNull()
    expect(mod.form.content).toBe('')
    expect(mod.form.correctAnswers).toBe('')
    expect(mod.form.explanation).toBe('')
  })

  it('删除确认文案用 content，题目名不再恒空', async () => {
    const { mod } = withSetup()

    await mod.remove({ ...ENTITY_ROW, id: 9, content: '待删题目' })

    expect(mocks.confirm.mock.calls[0][0]).toContain('待删题目')
    expect(mocks.deleteQuestion).toHaveBeenCalledWith(9)
  })

  it('删除确认文案兜底旧键 stem/title', async () => {
    const { mod } = withSetup()

    await mod.remove({ id: 10, stem: '旧键题干' })
    expect(mocks.confirm.mock.calls[0][0]).toContain('旧键题干')

    await mod.remove({ id: 11, title: '标题题干' })
    expect(mocks.confirm.mock.calls[1][0]).toContain('标题题干')
  })
})

describe('CourseQuestionBank.vue — 弹窗 @closed 接线（缺陷 4 的真实生效面）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getQuestionList.mockResolvedValue({
      code: 200,
      data: { records: [ENTITY_ROW], total: 1 },
    })
  })

  it('弹窗关闭后编辑态与表单被重置（旧实现 handler 恒 undefined）', async () => {
    // shallow 只桩掉已解析的组件；页面模板里的 el-button 在测试环境没有全局注册，
    // 显式桩掉以免「Failed to resolve component」噪声掩盖真正的 Vue warn。
    const wrapper = mount(CourseQuestionBank, {
      shallow: true,
      global: { stubs: { 'el-button': true, 'el-icon': true } },
    })
    await flushPromises()

    // 通过表格行的「编辑」事件进入编辑态，再触发弹窗 closed
    wrapper.findComponent(CqbQuestionTable).vm.$emit('edit', { ...ENTITY_ROW, id: 5 })
    await nextTick()
    const dialog = wrapper.findComponent(CqbEditDialog)
    expect(dialog.props('editing')).toBe(true)
    expect(dialog.props('form')).toMatchObject({ content: '题干' })

    dialog.vm.$emit('closed')
    await nextTick()

    expect(dialog.props('editing')).toBe(false)
    expect(dialog.props('form')).toMatchObject({ content: '', id: null })
  })
})

describe('useCourseQuestionBank — 题型/难度实库契约（2026-10-26 F3 更正）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getQuestionList.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  })

  it('新建/AI 表单默认值与 DIFF 选项必须是实库值（*_CHOICE 风格 + 小写难度；写侧原样落库）', () => {
    const { mod } = withSetup()
    mod.openCreate()
    expect(mod.form.type).toBe('SINGLE_CHOICE')
    expect(mod.form.difficulty).toBe('medium')

    mod.openAIDialog()
    expect(mod.aiForm.type).toBe('SINGLE_CHOICE')
    expect(mod.aiForm.difficulty).toBe('medium')

    // 选项 key 全部为实库值，不含旧风格与实库不存在的 CODE
    const typeKeys = mod.TYPE_OPTS.map((o) => o.value)
    expect(typeKeys).toEqual(['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'ESSAY'])
    expect(mod.DIFF_OPTS.map((o) => o.value)).toEqual(['easy', 'medium', 'hard'])
  })

  it('写侧 payload 的 type/difficulty 原样为实库值', async () => {
    mocks.createQuestion.mockResolvedValue({ code: 200, data: ENTITY_ROW })
    const { mod } = withSetup()
    mod.openCreate()
    Object.assign(mod.form, { content: '题干', correctAnswers: 'A', explanation: '' })

    await mod.save()

    const payload = mocks.createQuestion.mock.calls[0][0]
    expect(payload.type).toBe('SINGLE_CHOICE')
    expect(payload.difficulty).toBe('medium')
  })
})

/**
 * 缺陷 B5（2026-10-10 定口径）：审核态与缺陷 1 同族——`entity/Question.java` 没有 `reviewed`
 * 属性，审核动作只写 `is_active`（`QuestionServiceImpl.java:245`），且建题默认 1
 * （V1__init_schema.sql:514），所以后端**不存在独立的待审核态**。
 * 同仓已有一次修复口径（2026-09-21 P1-1，`useQuestionBank.ts:21` `resolveReviewStatus`），
 * 本复用而不再立一套标签，避免两处漂移。
 */
describe('useCourseQuestionBank — 审核态按 isActive 推导（缺陷 B5）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('isActive=false 才计入待审核（旧实现读不存在的 reviewed，统计卡恒为全量待审核/0）', async () => {
    mocks.getQuestionList.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 1, isActive: true },
          { id: 2, isActive: false },
          { id: 3, isActive: true },
        ],
        total: 3,
      },
    })

    const { mod } = withSetup()
    await flushPromises()

    expect(mod.countPending.value).toBe(1)
    expect(mod.countReviewed.value).toBe(2)
  })

  it('展示层与统计层不得再读 Question 上不存在的 reviewed 字段（源码守卫 + 阴性对照）', () => {
    const guarded = [
      resolve(__dirname, '../../composables/useCourseQuestionBank.ts'),
      resolve(__dirname, '../../views/teacher/cockpit/components/CqbQuestionTable.vue'),
    ]
    const offenders = guarded.filter((f) => /\.reviewed\b/.test(readFileSync(f, 'utf8')))
    expect(offenders).toEqual([])
    // 阴性对照：反例必须被抓，否则本守卫是空断言
    expect(/\.reviewed\b/.test('v-if="!row.reviewed"')).toBe(true)
  })
})
