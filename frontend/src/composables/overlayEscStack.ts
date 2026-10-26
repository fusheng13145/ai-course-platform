/**
 * 弹层 Esc 栈（P4-GATE-01，准入门 #6「Esc 只关最上层」的共享实现）。
 *
 * 为什么 Modal 与 Drawer 必须共用一份：两原语可以互相叠（modal 上叠 drawer、
 * drawer 上再开 modal），「最上层」是**跨原语**的层叠顺序，谁自己的实例集合都算不出来。
 * 顺序口径 = 开启顺序（后开者在上）——与 Teleport to body 的 append 顺序一致，
 * 也就是用户看到的层叠顺序；不需要读 z-index（两原语的 z 是固定 token，不构成顺序）。
 *
 * 注册的句柄就是各实例自己的 `close` 函数（每实例唯一、闭包含各自 emit）：
 * push/remove 由实例的 `watch(open, …, { immediate: true })` 边沿驱动，
 * onBeforeUnmount 兜底 remove——「曾开过、已关闭、仍存活」的实例早已出栈，
 * 不会收到幽灵 Esc（Modal.vmodel.test.ts 的关闭态哑火用例继续成立）。
 *
 * 拆锁声明（spec §11.3 准入 #6 明文授权「显式拆锁」）：Drawer.test.ts 原有一条
 * 「两个抽屉叠着开：一次 Esc 两个都关」钉的是旧全局广播行为，本批改为
 * 「只关栈顶」，原断言按新契约重写——这不是测试放宽，是行为语义的迁移。
 */
const stack = []

export function overlayEscPush(close) {
  if (!stack.includes(close)) stack.push(close)
}

export function overlayEscRemove(close) {
  const i = stack.indexOf(close)
  if (i >= 0) stack.splice(i, 1)
}

export function overlayEscIsTop(close) {
  return stack[stack.length - 1] === close
}
