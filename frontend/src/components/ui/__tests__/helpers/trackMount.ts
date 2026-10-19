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
   * 类型是 Node，不是 Element——后者在 Modal 这里会说谎：组件根是注释节点
   * （Task 8 起为 `<Teleport>` 的 `teleport start` 锚点，开与关都是它，见下），
   * 而 VTU 的 VueWrapper.element 在 hasMultipleRoots 为 false
   * 时直接返回 vm.$el（实测执行的那份构建是 dist/vue-test-utils.cjs.js:7788-7790；
   * 哪个构建在被执行不是想当然——Modal setup 里 throw 探针取栈帧，实测见
   * vue-test-utils.cjs.js:8495 的 mount 帧）。isConnected 定义在 Node 上，够本文件用。
   * nodeType===8 由 Modal.vmodel.test.ts 的「modelValue=false 不渲染遮罩」与「modelValue=true 时渲染」
   * 两条钉住（现在连 `data === 'teleport start'` 一起钉）。
   * ⚠️ Task 8 修复轮的重测口径（评审 I-3）：旧文"关闭态的 v-if 占位符仍是 Comment、Teleport 的锚点
   * 没有顶掉它"**说错了对象**——Teleport 挂载时 `n2.el = createComment('teleport start')`，
   * 而 `initialVNode.el = subTree.el`（本机 vue 3.5.43 的 runtime-core.cjs.js:1051 与 :6222），
   * 根是不是"开着"与之无关 ⇒ **开启态与关闭态的 `$el` 都是那条 `teleport start` 注释**（nodeType 8、
   * isConnected=true）。两态的差别不在"根是不是注释"，在**东西住在哪儿**：开启态遮罩本体在
   * `document.body` 下而 `$el` 仍住在调用方容器里；关闭态 body 里只剩一条 `v-if` 占位注释
   * （它也挂在 Teleport 的 target 里，不在 `$el` 旁边）。
   * 由此：`isConnected` 判据只反映**原位锚点**是否还挂着，不反映 body 上那块遮罩——
   * 遮罩的清理只能靠 unmount 本身，别拿 isConnected 当「遮罩已消失」的证据。
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
