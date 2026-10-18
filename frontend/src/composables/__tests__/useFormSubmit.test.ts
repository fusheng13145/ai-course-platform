import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { useFormSubmit, validateFormFields, type ElFormLike } from '@/composables/useFormSubmit'

/**
 * useFormSubmit 原语测试（D2-4）
 *
 * <p>锁定三类语义：①校验失败中止提交（首错定位由 el-form 承担）；
 * ②提交中重复调用被闸门拦截（防重复）；③无 validate 能力的表单
 * （测试桩场景）跳过校验保持兼容。</p>
 */
describe('useFormSubmit — 表单提交守卫原语', () => {
  it('校验通过 → 执行提交，submitting 经历 true → false', async () => {
    let resolveSubmit!: () => void
    const submit = vi.fn(
      () =>
        new Promise<void>((r) => {
          resolveSubmit = r
        }),
    )
    const formRef = ref<ElFormLike | null>({ validate: vi.fn().mockResolvedValue(true) })
    const { submitting, run } = useFormSubmit(formRef, submit)

    const p = run()
    // validate 前置使 run 内部多一跳微任务，用 flushPromises 排空（对齐 useStudentExams 的有界等待纪律）
    await flushPromises()
    expect(submitting.value).toBe(true)
    expect(submit).toHaveBeenCalledTimes(1)

    resolveSubmit()
    await p
    expect(submitting.value).toBe(false)
  })

  it('校验失败 → 不执行提交且中止（内联错误与滚动由 el-form 承担）', async () => {
    const submit = vi.fn()
    const formRef = ref<ElFormLike | null>({
      validate: vi.fn().mockRejectedValue(new Error('validate failed')),
    })
    const { submitting, run } = useFormSubmit(formRef, submit)

    await run()

    expect(submit).not.toHaveBeenCalled()
    expect(submitting.value).toBe(false)
  })

  it('提交中（submitting=true）重复调用 run → 被闸门拦截，不重复提交', async () => {
    let resolveSubmit!: () => void
    const submit = vi.fn(
      () =>
        new Promise<void>((r) => {
          resolveSubmit = r
        }),
    )
    const formRef = ref<ElFormLike | null>({ validate: vi.fn().mockResolvedValue(true) })
    const { run } = useFormSubmit(formRef, submit)

    const first = run()
    await flushPromises() // 等 validate 前置微任务排空，进入 submitting 态
    await run() // 闸门拦截
    await run() // 闸门拦截

    expect(submit).toHaveBeenCalledTimes(1)
    resolveSubmit()
    await first
  })

  it('提交抛错 → submitting 仍必须复位（finally 收口），错误向上抛出', async () => {
    const submit = vi.fn().mockRejectedValue(new Error('500'))
    const formRef = ref<ElFormLike | null>({ validate: vi.fn().mockResolvedValue(true) })
    const { submitting, run } = useFormSubmit(formRef, submit)

    await expect(run()).rejects.toThrow('500')
    expect(submitting.value).toBe(false)
  })

  it('无 validate 能力的表单（测试桩/无规则表单）→ 跳过校验直接提交', async () => {
    const submit = vi.fn().mockResolvedValue(undefined)
    // 模拟无 validate 的组件桩
    const formRef = ref<ElFormLike | null>({} as ElFormLike)
    const { run } = useFormSubmit(formRef, submit)

    await run()

    expect(submit).toHaveBeenCalledTimes(1)
  })

  it('formRef 为空（表单未挂载）→ 跳过校验直接提交', async () => {
    const submit = vi.fn().mockResolvedValue(undefined)
    const formRef = ref<ElFormLike | null>(null)
    const { run } = useFormSubmit(formRef, submit)

    await run()

    expect(submit).toHaveBeenCalledTimes(1)
  })
})

describe('validateFormFields — 分步字段校验（向导场景）', () => {
  it('指定字段走 validateField（不触发全校验）', async () => {
    const validate = vi.fn().mockResolvedValue(true)
    const validateField = vi.fn().mockResolvedValue(true)
    const formRef = ref<ElFormLike | null>({ validate, validateField })

    const ok = await validateFormFields(formRef, ['email'])

    expect(ok).toBe(true)
    expect(validateField).toHaveBeenCalledWith(['email'])
    expect(validate).not.toHaveBeenCalled()
  })

  it('指定字段校验失败 → 返回 false', async () => {
    const formRef = ref<ElFormLike | null>({
      validate: vi.fn(),
      validateField: vi.fn().mockRejectedValue(new Error('invalid')),
    })

    expect(await validateFormFields(formRef, ['code'])).toBe(false)
  })

  it('未指定字段 → 回退全校验 validate', async () => {
    const validate = vi.fn().mockResolvedValue(true)
    const formRef = ref<ElFormLike | null>({ validate })

    expect(await validateFormFields(formRef)).toBe(true)
    expect(validate).toHaveBeenCalledTimes(1)
  })

  it('无校验能力（测试桩）→ 返回 true 保持兼容', async () => {
    const formRef = ref<ElFormLike | null>({} as ElFormLike)
    expect(await validateFormFields(formRef, ['email'])).toBe(true)
    expect(await validateFormFields(ref(null))).toBe(true)
  })
})
