import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 全局快捷键原语（D2-7 第八批：快捷键体系）
 * ============================
 * 键盘可达性的最后一环：此前全站快捷键为零——顶栏「全局搜索」只能鼠标点击，
 * 键盘用户无从快速到达；也没有任何「有哪些快捷键」的自说明入口。
 *
 * 设计取舍：
 * - **单键只在输入态之外生效**（`/`、`?` 在输入框里是正常字符，劫持会打断输入）；
 *   输入态判据覆盖 input/textarea/select/contenteditable；
 * - **带修饰与不带修饰互斥**：`Ctrl+K` 不会命中「裸 k」绑定，反之亦然——避免
 *   「注册了 k 就在任何输入里按 k 都触发」这类经典事故；
 * - **弹层打开时不穿透**：`.el-overlay`（el-dialog/el-drawer 的遮罩）存在时，
 *   非显式豁免的快捷键不触发——避免用户在弹层里按 `/` 把焦点跳到弹层外的搜索框；
 * - **preventDefault**：`/`（Firefox 快速查找）、`Ctrl+K`（Chrome 地址栏）都是浏览器
 *   保留行为，命中后必须阻止默认，否则快捷键名存实亡；
 * - 生命周期随组件挂载/卸载自动注册/清理。
 *
 * 用法：
 * ```ts
 * useGlobalHotkeys([
 *   { key: 'k', mod: true, handler: focusSearch },
 *   { key: '/', handler: focusSearch },
 * ])
 * ```
 */

/** 聚焦顶栏全局搜索的事件名：AppShell 派发、AtbGlobalSearch 监听（两端解耦，不跨组件传 ref） */
export const FOCUS_GLOBAL_SEARCH_EVENT = 'ac:focus-global-search'

export interface HotkeyBinding {
  /** 参与匹配的 `event.key`（大小写不敏感；问号直接传 '?'） */
  key: string
  /** 是否要求 Ctrl/⌘（与「无修饰」互斥：两者不会互相误命中） */
  mod?: boolean
  /** 命中后的动作 */
  handler: () => void
  /** 输入态（input/textarea/select/contenteditable）是否也触发；默认 false——打字不被劫持 */
  allowInEditable?: boolean
  /** 有弹层遮罩（.el-overlay）打开时是否也触发；默认 false——弹层内不穿透到壳层动作 */
  allowWhenOverlayOpen?: boolean
}

/** 判定事件目标是否处于「可编辑」语境（输入框/文本域/下拉/富文本） */
export function isEditableTarget(target: EventTarget | null | undefined): boolean {
  if (typeof HTMLElement === 'undefined' || !(target instanceof HTMLElement)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  // 富文本：isContentEditable 覆盖「祖先声明 contenteditable」的继承态，
  // 属性兜底覆盖 jsdom 等实现对游离节点不计算 isContentEditable 的差异
  if (target.isContentEditable === true) return true
  const attr = target.getAttribute?.('contenteditable')
  return attr === '' || attr === 'true' || attr === 'plaintext-only'
}

/** 是否存在打开的弹层遮罩（Element Plus 的 el-dialog / el-drawer 均渲染为 .el-overlay） */
export function hasOpenOverlay(doc: Document = document): boolean {
  return typeof doc?.querySelector === 'function'
    ? Boolean(doc.querySelector('.el-overlay'))
    : false
}

/**
 * 注册一组全局快捷键（keydown 监听随组件生命周期挂载/清理）。
 *
 * <p>匹配规则：按数组顺序取首个命中项；命中后 `preventDefault` 并停止后续匹配
 * （同一击键只触发一个动作）。</p>
 */
export function useGlobalHotkeys(bindings: HotkeyBinding[]): void {
  function onKeydown(event: KeyboardEvent): void {
    for (const binding of bindings) {
      const wantsMod = binding.mod === true
      const hasMod = event.ctrlKey || event.metaKey
      if (wantsMod !== hasMod || event.altKey) continue
      if (event.key.toLowerCase() !== binding.key.toLowerCase()) continue
      if (!binding.allowInEditable && isEditableTarget(event.target)) continue
      if (!binding.allowWhenOverlayOpen && hasOpenOverlay()) continue
      event.preventDefault()
      binding.handler()
      return
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
}
