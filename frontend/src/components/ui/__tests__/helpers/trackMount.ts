/**
 * Modal 测试族共用的「挂载即登记、afterEach 统一卸载」夹具。
 * （Task 7 修复轮从 Modal.compat.test.ts 与 Modal.vmodel.test.ts 的逐字重复中提取，
 * 两个文件共用；新能力文件可另开，但不得再复制一份本夹具。）
 *
 * 为什么必须卸载：族内用例全部 attachTo document.body，而 Modal 的 keydown 监听整个
 * 挂载期常驻 document。不卸载，`.overlay` 节点和监听就会作为僵尸实例活到整份文件结束；
 * Task 8 把查询路径切到 body 作用域后，僵尸节点会直接污染断言。
 *
 * 用法：factory 里 `return track(mount(...))`；describe 内 `afterEach(unmountTracked)`。
 */

export interface TrackedWrapper {
  /**
   * 类型是 Node，不是 Element——后者在「v-if 单根组件处于关闭态」时是谎话：
   * 关闭时子树是 Comment 占位节点，VTU 的 VueWrapper.element 在 hasMultipleRoots 为 false
   * 时直接返回 vm.$el（实测执行的那份构建是 dist/vue-test-utils.cjs.js:7788-7790；
   * 哪个构建在被执行不是想当然——Modal setup 里 throw 探针取栈帧，实测见
   * vue-test-utils.cjs.js:8495 的 mount 帧）。isConnected 定义在 Node 上，够本文件用。
   * nodeType===8 的事实在 Modal.vmodel.test.ts「modelValue=false 不渲染遮罩」里断言钉住。
   */
  readonly element: Node
  unmount(): void
}

const registry: TrackedWrapper[] = []

export function track<W extends TrackedWrapper>(wrapper: W): W {
  registry.push(wrapper)
  return wrapper
}

export function unmountTracked(): void {
  // 监听清理类用例会自己 unmount（那是被测动作）；卸载后元素从 body 摘掉、
  // isConnected 变 false，据此跳过，避免二次 unmount。
  for (const w of registry) {
    if (w.element.isConnected) w.unmount()
  }
  registry.length = 0
}
