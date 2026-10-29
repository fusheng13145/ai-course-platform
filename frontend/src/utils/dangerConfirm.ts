import { ElMessageBox } from 'element-plus'
import { buildDangerMessage, type DangerConfirmOptions } from './dangerMessage'

export { buildDangerMessage, DEFAULT_RECOVERY } from './dangerMessage'
export type { DangerConfirmOptions } from './dangerMessage'

/**
 * 弹出规范化危险操作确认框（Element Plus 宿主）。
 * v2 侧走自研弹层，但共用 utils/dangerMessage 的同一套文案模板。
 *
 * 用户确认 → resolve(true)；取消 / 关闭 / 弹框本身异常 → resolve(false)（绝不 reject，
 * 调用方无需再区分 'cancel' / 'close'）。
 */
export async function confirmDanger(options: DangerConfirmOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(buildDangerMessage(options), `${options.verb}确认`, {
      type: 'warning',
      confirmButtonText: options.confirmText ?? options.verb,
      cancelButtonText: options.cancelText ?? '取消',
      confirmButtonClass: 'el-button--danger',
    })
    return true
  } catch {
    return false
  }
}
