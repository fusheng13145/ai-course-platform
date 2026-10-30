import { ElMessage, ElMessageBox } from 'element-plus'
import type { OverlayHost } from '@/bootstrap/overlayHost'

/**
 * 旧前端的浮层宿主：提示与确认走 Element Plus。
 * 由 `bootstrap/apiHostEp.ts` 在入口注册；旧前端下线时，摘除 EP 的动作就收敛到这一处。
 *
 * 危险按钮的类名等 EP 细节只在这里出现，共用件只表达 `danger` 语义。
 */
export const epOverlayHost: OverlayHost = {
  message: {
    info: (message) => ElMessage.info(message),
    success: (message) => ElMessage.success(message),
    warning: (message) => ElMessage.warning(message),
    error: (message) => ElMessage.error(message),
  },
  confirm: (message, title, options) => {
    const boxOptions = {
      type: options?.type,
      confirmButtonText: options?.confirmButtonText,
      cancelButtonText: options?.cancelButtonText,
      ...(options?.danger ? { confirmButtonClass: 'el-button--danger' } : {}),
    }
    return ElMessageBox.confirm(message, title, boxOptions)
      .then(() => true)
      .catch(() => false)
  },
}
