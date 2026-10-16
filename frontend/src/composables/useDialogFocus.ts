import { watch, nextTick } from 'vue'
import type { ComputedRef, Ref } from 'vue'

/**
 * 对话框焦点管理原语（D2-7）
 * ============================
 * Element Plus 的 `el-dialog` 打开时默认把焦点放在对话框容器（`role=dialog`）
 * 上，读屏软件能读到对话框标题，但**键盘用户仍需多次 Tab 才能到达首个
 * 输入框**；且全仓 64 个对话框此前无任何焦点管理（`.focus()` 零调用）。
 *
 * 本原语在对话框打开后，把焦点移入**首个可聚焦控件**（输入框 / 选择器 /
 * 文本域 / 按钮 / 自定义 tabindex），遵循 WAI-ARIA APG「打开即焦点入框」惯例。
 *
 * 设计取舍：
 * - 用 `watch(visible)` + `nextTick` + `requestAnimationFrame` 双等待：
 *   el-dialog 经 teleport + 过渡动画渲染，DOM 在下一帧才稳定，早于此时
 *   `querySelector` 会取不到元素；
 * - 选择器限定 `:not([disabled]):not([readonly])`，避免把焦点送给禁用控件
 *   （只读输入框允许聚焦，便于读屏朗读当前值）；
 * - **不动默认焦点顺序**：若对话框首个子节点本身就是关闭按钮（如确认框），
 *   聚焦首控件即聚焦关闭/取消，符合「危险操作不默认聚焦确认」的安全惯例。
 *
 * 用法：
 * ```ts
 * const dialogRef = ref(null)
 * useDialogFocus(dialogRef, computed(() => props.modelValue))
 * // 模板：<el-dialog ref="dialogRef" :model-value="modelValue" ...>
 * ```
 */

/** 可聚焦元素选择器（禁用项除外；只读输入允许聚焦以便读屏朗读） */
const FOCUSABLE_SELECTOR = [
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'button:not([disabled])',
  'a[href]',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

export function useDialogFocus(
  dialogRef: Ref<{ $el?: HTMLElement } | null | undefined>,
  visible: Ref<boolean> | ComputedRef<boolean>,
): void {
  watch(visible, (open) => {
    if (!open) return
    nextTick(() => {
      // 等一帧：el-dialog 的 teleport + 过渡动画使 DOM 在下一帧才稳定
      requestAnimationFrame(() => {
        const root = dialogRef?.value?.$el
        if (!root || typeof root.querySelector !== 'function') return
        const first = root.querySelector<HTMLElement>(FOCUSABLE_SELECTOR)
        first?.focus()
      })
    })
  })
}

/** 供单测与脚本化核验使用的可聚焦选择器 */
export const DIALOG_FOCUSABLE_SELECTOR = FOCUSABLE_SELECTOR
