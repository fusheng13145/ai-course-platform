import { isRegisteredAnchor } from '../anchors'
import type { OnboardingStep } from './types'
import student from './student'
import teacher from './teacher'
import admin from './admin'

const BY_ROLE: Record<string, OnboardingStep[]> = { student, teacher, admin }

/** 排序不变式：page 锚点只能是最后一步；锚点必须已注册 */
export function validateSteps(steps: unknown): { ok: boolean } {
  if (!Array.isArray(steps) || steps.length < 2) throw new Error('steps 至少两步（欢迎＋激活动作）')
  const arr = steps as OnboardingStep[]
  arr.forEach((s, i) => {
    if (s.anchor && !isRegisteredAnchor(s.anchor)) {
      throw new Error(`未注册锚点：${s.anchor}（第 ${i + 1} 步）`)
    }
    if (s.scope === 'page' && i !== arr.length - 1) {
      throw new Error(`page 锚点只能是最后一步（第 ${i + 1} 步用了 ${s.anchor}）`)
    }
  })
  return { ok: true }
}

export function loadSteps(role: string): OnboardingStep[] {
  const steps = BY_ROLE[role]
  if (!steps) throw new Error(`无该角色步骤配置：${role}`)
  return steps
}
