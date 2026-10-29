import { ElMessage } from 'element-plus'
import router from '@/router'
import { setApiHost } from '@/api/apiHost'

/**
 * 旧前端的 HTTP 宿主：提示走 Element Plus，导航走旧端路由。
 * 由 `main.ts` 在入口注册；旧前端下线时，摘除 EP 的动作就收敛到这一处。
 */
setApiHost({
  message: ElMessage,
  push: (target) => {
    router.push(target as never)
  },
  currentPath: () => String(router.currentRoute?.value?.fullPath || ''),
})
