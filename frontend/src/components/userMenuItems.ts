/**
 * 用户菜单项的单一来源：侧栏底部用户区与顶栏头像都渲染这一份。
 * 工厂只收原始值（不收 store 对象），两处壳层各喂自己的形状会重新分叉出两套 items。
 */
export interface UserMenuItemDeps {
  router: { push: (to: string) => unknown }
  role: string
  logout: () => Promise<unknown>
  onGuide: () => void
  profilePath?: string
}

export interface UserMenuItem {
  label: string
  icon: string
  danger?: boolean
  action: () => void
}

export const USER_MENU_ITEMS = ({
  router,
  role,
  logout,
  onGuide,
  profilePath,
}: UserMenuItemDeps): UserMenuItem[] => [
  {
    label: '个人中心',
    icon: 'user',
    action: () => router.push(profilePath || `/${role.toLowerCase()}/profile`),
  },
  { label: '新手引导', icon: 'grad', action: () => onGuide() },
  {
    label: '退出登录',
    icon: 'logout',
    danger: true,
    action: () => logout().then(() => router.push('/login')),
  },
]

/** 顶栏用户区与引导组件不是父子，重开只能经窗口事件（与聚焦全局搜索同一条口径） */
export const REOPEN_ONBOARDING_EVENT = 'ac:reopen-onboarding'

export function reopenOnboarding(): void {
  window.dispatchEvent(new CustomEvent(REOPEN_ONBOARDING_EVENT))
}
