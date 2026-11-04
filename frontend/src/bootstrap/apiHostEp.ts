import { setApiHost } from '@/api/apiHost'
import { setOverlayHost } from '@/bootstrap/overlayHost'
import { appRouter } from '@/ui/router'
import { toast } from '@/ui/composables/ui'
import { confirmWith } from '@/ui/composables/confirm'

/**
 * 的 HTTP 与浮层宿主：提示走 toast，导航走 router，确认走 弹层。
 * 由 `main.ts` 首行注册；旧前端下线后，这里也是唯一需要知道「宿主实现是谁」的地方。
 */
setApiHost({
  message: {
    error: (message) => toast(message, 'danger'),
    warning: (message) => toast(message, 'warn'),
    info: (message) => toast(message, 'info'),
    success: (message) => toast(message, 'ok'),
  },
  push: (target) => {
    appRouter.push(target as never)
  },
  currentPath: () => String(appRouter.currentRoute?.value?.fullPath || ''),
})

setOverlayHost({
  message: {
    info: (message) => toast(message, 'info'),
    success: (message) => toast(message, 'ok'),
    warning: (message) => toast(message, 'warn'),
    error: (message) => toast(message, 'danger'),
  },
  confirm: confirmWith,
})
