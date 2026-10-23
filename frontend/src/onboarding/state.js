/**
 * 本地契约 v2。三态可区分就是"完成率"的定义本身——改前四个出口往同一个键写 '1'，
 * 于是"完成"与"中途 Esc"在数据上是同一件事（F6）。
 */
export const KEY_V1_PREFIX = 'ai-course:onboarding:done:v1'
export const KEY_V2_PREFIX = 'ai-course:onboarding:v2'
const ANON = 'anon'
const STATUSES = ['done', 'skipped', 'paused']

const keyV2 = (userId) => `${KEY_V2_PREFIX}:${userId ?? ANON}`
const keyV1 = (userId) => `${KEY_V1_PREFIX}:${userId ?? ANON}`

export function readState(userId) {
  try {
    const raw = localStorage.getItem(keyV2(userId))
    if (!raw) return { status: 'idle', atStep: 0 }
    const parsed = JSON.parse(raw)
    // 脏值整条作废：status 不在枚举内就不配把它的 atStep 读回来，
    // 否则 resume() 会照着坏记录跳到无关步骤；坏 JSON 同样走这一条降级。
    if (!STATUSES.includes(parsed.status)) return { status: 'idle', atStep: 0 }
    const atStep = Number.isInteger(parsed.atStep) && parsed.atStep >= 0 ? parsed.atStep : 0
    return { status: parsed.status, atStep }
  } catch {
    return { status: 'idle', atStep: 0 }
  }
}

/** resume() 的跨角色判断要取落盘里的 role；readState 的返回形状不含 role（不泄漏进契约），
 *  所以单开这条窄读，只在角色比对时读一次。 */
export function readStoredRole(userId) {
  try {
    const raw = localStorage.getItem(keyV2(userId))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return typeof parsed.role === 'string' ? parsed.role : null
  } catch {
    return null
  }
}

export function writeStatus(userId, status, atStep, role = null) {
  try {
    localStorage.setItem(
      keyV2(userId),
      JSON.stringify({ status, atStep, role, seenAt: new Date().toISOString() }),
    )
  } catch {
    // 读侧可以降级（拿不到就当没看过），写侧不许静默：静默＝"稍后再看"下次没续上而无人知道
    console.warn(`[onboarding] 写入失败（status=${status}@${atStep}），下次将按未看过处理`)
    return
  }
  // 【R-19b】原先这里有一条 `listeners.forEach((cb) => cb(from, {...}))`，给 `onTransition()`
  // 的订阅者回放 (from, to)——但订阅 API 全仓零生产调用（只被它自己的测试用），属"只被
  // 测试保护的孤儿导出"，已连同订阅端、`listeners` 集合与开头的 `const from = readState()`
  // 一起删除（那次读的返回值只喂给了这条广播，没有别的消费者）。
}

export function migrateV1IfPresent(userId, role = null) {
  try {
    if (localStorage.getItem(keyV2(userId))) return false
    if (localStorage.getItem(keyV1(userId)) !== '1') return false
    writeStatus(userId, 'done', 0, role) // 老用户绝不再弹；v1 键保留作回滚证据
    return true
  } catch {
    return false
  }
}
