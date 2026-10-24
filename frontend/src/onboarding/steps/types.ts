/**
 * 引导步骤的公共形状。三端 steps/{student,teacher,admin}.ts 与 loader 的校验共用。
 * scope='page' 的步只允许出现在最后一步（loader.validateSteps 的排序不变式）。
 */
export interface OnboardingStep {
  id: string
  title: string
  desc: string
  /** 已注册锚点键（nav: 前缀走导航注册表、裸键走 anchors.ts 的 BARE 表） */
  anchor?: string
  scope: 'shell' | 'page'
  /** 激活动作：点击锚点本身，或导航到指定路由 */
  action?: { type: 'click-anchor' | 'navigate'; to?: string }
  /** 激活动作后的预期文案（演示/取证用） */
  expect?: string
}
