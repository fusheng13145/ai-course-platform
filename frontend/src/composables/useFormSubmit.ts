import type { Ref } from 'vue'
import { ref } from 'vue'

/**
 * 表单校验交互统一原语（D2-4）
 * ============================
 * 把「规则校验（含首错定位）→ 提交防重复 → 提交态收口」固化为可复用组合式函数，
 * 供各表单页统一接入，避免逐页手写 if 校验 + 各自一套防重复逻辑导致交互口径漂移。
 *
 * 解决的三类问题：
 * 1. **规则声明形同虚设**：页面声明了 `:rules` 但从不调 `formRef.validate()`，
 *    校验逻辑散落为手写 if（且与 rules 文案不一致）；
 * 2. **首个错误无定位**：校验失败时用户看不到哪个字段出错（`scroll-to-error`
 *    未开启、`.validate()` 未调用），需要人工逐字段排查；
 * 3. **提交可重复触发**：按钮无 submitting 闸门，快速双击 / 回车连按会重复触发
 *    同一提交（重复建单、重复发请求）。
 *
 * 约定：
 * - `formRef` 指向 `<el-form>`，且该表单开启 `scroll-to-error`（失败时自动滚动
 *   定位首个错误字段；本原语负责触发 validate，滚动由 el-form 承担）；
 * - 测试桩 / 无校验表单（`validate` 不存在的形如搜索框场景）下自动跳过校验，
 *   便于渐进接入与单测隔离；
 * - `run()` 在 `submitting` 为 true 时直接忽略重复调用（返回 `undefined`），
 *   调用方可用返回值区分「已执行」与「被闸门拦截」。
 *
 * 用法：
 * ```ts
 * const formRef = ref()                     // <el-form ref="formRef" scroll-to-error>
 * const { submitting, run: submit } = useFormSubmit(formRef, async () => {
 *   await api.save(form)                    // 校验已由原语前置完成
 * })
 * // 模板：<button :disabled="submitting" @click="submit">
 * ```
 */
export interface ElFormLike {
  validate: () => Promise<unknown>
  /** 局部字段校验（向导分步场景，如先校验邮箱、再校验验证码） */
  validateField?: (props: string[]) => Promise<unknown>
}

/**
 * 校验指定字段（缺省全校验）；表单无校验能力（测试桩）时返回 true 保持兼容。
 * 用于向导/分步表单：每步只校验当前步字段，失败时 el-form 滚动定位该步首错。
 */
export async function validateFormFields(
  formRef: Ref<ElFormLike | null | undefined>,
  props?: string[],
): Promise<boolean> {
  const form = formRef?.value
  if (!form) return true
  try {
    if (props?.length && typeof form.validateField === 'function') {
      await form.validateField(props)
    } else if (typeof form.validate === 'function') {
      await form.validate()
    }
    return true
  } catch {
    return false
  }
}

export interface UseFormSubmitReturn {
  /** 提交中（闸门 + 按钮 loading/disabled 绑定） */
  submitting: Ref<boolean>
  /** 校验通过后执行提交；闸门拦截或校验失败时 resolve 为 undefined */
  run: () => Promise<void>
}

export function useFormSubmit(
  formRef: Ref<ElFormLike | null | undefined>,
  submit: () => Promise<void> | void,
): UseFormSubmitReturn {
  const submitting = ref(false)

  async function run(): Promise<void> {
    if (submitting.value) return
    // 无校验能力（测试桩 / 无规则表单）时跳过校验，保持行为与接入前一致
    const ok = await validateFormFields(formRef)
    if (!ok) {
      // 校验失败：el-form 已在 scroll-to-error 开启时滚动定位首个错误字段，
      // 内联错误文案由 el-form-item 渲染，此处仅中止提交
      return
    }
    submitting.value = true
    try {
      await submit()
    } finally {
      submitting.value = false
    }
  }

  return { submitting, run }
}
