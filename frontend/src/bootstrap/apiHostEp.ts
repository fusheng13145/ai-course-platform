import { setApiHost } from '@/api/apiHost'
import { setOverlayHost } from '@/bootstrap/overlayHost'
import { appRouter } from '@/v2/router'
import { v2Toast } from '@/v2/composables/ui'
import { confirmWithV2 } from '@/v2/composables/v2Confirm'

/**
 * v2 的 HTTP 与浮层宿主：提示走 v2 toast，导航走 v2 router，确认走 v2 弹层。
 * 由 `main.ts` 首行注册；旧前端下线后，这里也是唯一需要知道「宿主实现是谁」的地方。
 */
setApiHost({
  message: {
    error: (message) => v2Toast(message, 'danger'),
    warning: (message) => v2Toast(message, 'warn'),
    info: (message) => v2Toast(message, 'info'),
    success: (message) => v2Toast(message, 'ok'),
  },
  push: (target) => {
    appRouter.push(target as never)
  },
  currentPath: () => String(appRouter.currentRoute?.value?.fullPath || ''),
})

setOverlayHost({
  message: {
    info: (message) => v2Toast(message, 'info'),
    success: (message) => v2Toast(message, 'ok'),
    warning: (message) => v2Toast(message, 'warn'),
    error: (message) => v2Toast(message, 'danger'),
  },
  confirm: confirmWithV2,
})
