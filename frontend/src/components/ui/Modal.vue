<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'

/**
 * 遮罩为什么必须 Teleport 到 body（Task 8，spec §2.6 的包含块陷阱）：
 * `.overlay` 是 `position: fixed; inset: 0`，而 fixed 会被**任何计算值非 `none`** 的
 * `transform` / `filter` / `backdrop-filter` / `will-change` 祖先重新包含。本仓实测两处今日就生效：
 * `views/student/components/SdCourseGrid.vue` 中嵌在模板根 `<section class="l1-card animate-in d2">` 之内的那个
 * `<Modal narrow>`（`.animate-in` 以 `both` 填充出永久 `translateY(0)`，而 `.l1-card` 还 `overflow: hidden`
 * ⇒ 遮罩被裁进卡片），以及 `views/teacher/tools/KnowledgeManagement.vue` 页面根 `<div class="fade-in">` 之下的
 * `<ExcelImportDialog>`（其模板根即 `<Modal wide>`，`.fade-in` 以 `forwards` 填充同一条 `translateY(0)`）。
 * 这两处只给类名/选择器锚、不写行号：`SdCourseGrid.vue`、`KnowledgeManagement.vue`、`design-system.css` 的
 * `.animate-in` 规则、`index.scss` 的 `.fade-in` 规则全是 Phase 1–3 要动的文件，行号必漂
 * （台账 `P0b-MODAL-01` 里的行号各带"只成立于某 commit"的限定，那是另一套口径，别与本处混用）。
 * 这条陷阱 **Playwright 基线拍不到**：`prefers-reduced-motion: reduce` 把 `.animate-in/.fade-in` 降为
 * `animation: none`，而基线正是在 reduce + `animations: 'disabled'` 下采集的（spec §6.11）。
 * 结构保证由 `__tests__/Modal.teleport.test.ts` 钉，不靠截图。
 *
 * 单个 `<Teleport :disabled>`、不并列两条分支、不复制 DOM，因此不必抽 `ModalPanel.vue`。
 *
 * 可见性双通道（Task 7）。三态语义：`undefined`（不传）与 `true` 都是「开」，只有 `false` 是「关」——
 * 13 个存量调用点（12 个文件）全都不传、可见性由外层 `v-if` 决定，所以「没传」必须继续等于「可见」；
 * `true`/`false` 供 Phase 4 的 `v-model="visible"` 使用。
 *
 * `default: undefined` 是承重的，不能删：Boolean prop 缺省且没有 default 时，Vue 会把「没传」
 * 强制成 `false`（vue 3.5.43 resolvePropValue 的 shouldCast 分支；本机探针实测 vitest 执行的是
 * runtime-core.cjs.js:5118-5123，同一逻辑在 runtime-core.esm-bundler.js:5140-5144）。
 * 否则 open 恒为 false，全部老页面一打开就是空白。同 Drawer.vue 的 modelValue 声明（同一条规则）。
 *
 * 透传属性必须显式接回（Task 8 实测，不是假想）：根变成 `<Teleport>` 之后，Vue 不再把
 * `class` / `data-*` 之类 fall-through attrs 自动贴到某个元素上——探针实测 `attrs:{'data-test':'x', class:'extra-cls'}`
 * 时 `.overlay` 只有 `class="overlay"`、`.modal` 只有 `class="modal"`，两处都**静默丢掉**。
 * Task 8 之前根元素是 `.overlay`，这些属性落在那儿，所以这里的 `v-bind="$attrs"` 是**维持原落点**
 * （brief Step 3 顺口提的「落到 .modal 上」反而会把 class 的落点从 .overlay 挪走，属行为变更，没采纳）。
 * 配套 `defineOptions({ inheritAttrs: false })` 是**保险，不是承重**（Task 8 修复轮把被执行的那份
 * runtime-core.cjs.js 逐行开过之后重写；旧文"手工绑定后不再让运行时二次套用，避免同一个 class 落两遍"不成立）：
 * 根是 Teleport 时**根本没有可抑制的运行时落点**——fall-through 只在 `root.shapeFlag & (1 | 6)` 时才
 * `cloneVNode(root, fallthroughAttrs)`（判定在该文件 :4707-4718，条件行 :4711），而 Teleport 根的 shapeFlag
 * 是 64（:7871）；:4704 的 `getChildRoot` 也只下钻 patchFlag 2048（DEV_ROOT_FRAGMENT）的根，本组件的 Teleport
 * 根带的是 patchFlag 8（PROPS，编译产物实测）。变异实测（修复轮 1，当时 `src/components/ui/` 共 97 条）：
 * 把本行翻成 `inheritAttrs: true` **一条都不红**——旧理由连被证伪的资格都没有，所以它不能当依据。
 * 剩下的两条价值，现在各由一条入库用例兜着（不再只靠一次性探针）：
 * (a) 抑制**关闭态**的 dev 警告：`v-if="open"` 短路后模板不再读 `$attrs`，`markAttrsAccessed()`（:3477-3479）
 *     不被调用，:4719 那条 `Extraneous non-props attributes … teleport root nodes` 就会响。
 *     入库面：`__tests__/Modal.teleport.test.ts` 的「关闭态带透传属性时不打 fall-through 的 dev 警告」，
 *     变异复跑实测 `1 failed | 97 passed (98)`，红的就是它、报的就是那条警告文本。
 *     ⚠️ 开启态在两种设置下都不响（`$attrs` 被 `v-bind` 读到，那条分支自己就是死的），
 *     那半句**故意不钉**——钉上得到的是翻不红的断言，属虚假覆盖；
 * (b) 根哪天**换回元素根**时 :4711 立即生效（判定式 `shapeFlag & (1 | 6)`），届时 `v-bind="$attrs"` 与运行时
 *     自动套用**才会真的落两遍**（实测：卸掉 `<Teleport>` 再翻 `inheritAttrs: true` ⇒ `.overlay` 的
 *     `extra-cls` 计数 1→2，`Modal.teleport.test.ts` 那条计数断言报 `expected 2 to be 1`）。
 *     ⚠️ 这半句只覆盖元素根，别写成"任何非 Teleport 根"：单元素 fragment 要经 `getChildRoot`（:4772）与
 *     `filterSingleRoot`（:4795）钻出那个元素才算元素根，而**真正的多子 fragment 根本钻不出来**——`filterSingleRoot`
 *     见第二个真子节点就 `return`（:4795 起的循环体），`getChildRoot` 于是原样返回这个 fragment（:4777），
 *     它的 shapeFlag 实测是 **16**（`createVNode(Fragment, null, [两个元素]).shapeFlag`），`& (1 | 6)` 为 0
 *     ⇒ 照旧只走 :4719 那条 dev 警告、一次都不落。所以这是把设计留在"显式"一侧的前瞻，不是当下的机制。
 * 以上行号只成立于本机已装的 vue 3.5.43（`node_modules/@vue/runtime-core`）；锚点请以函数名与警告文本为准。
 * 尺寸档已声明为 props，故不在 `$attrs` 里，`wide=""` 这个属性不会跟着落到 `.overlay`（旧行为会）。
 * 现存 13 个调用点一个都不传 class/data-*（grep 全部 `<Modal` 开标签：只有 v-if、尺寸档、@close），
 * 这条纯为 Phase 4 的 65 个迁移点兜底。`@close` 不受影响：`close` 已声明进 emits，不会进 `$attrs`。
 */
defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: Boolean, default: undefined },
  /**
   * 遮罩落点开关（Task 8）。缺省即 `true`：Phase 4 的 65 个迁移点不该被迫多写一个 prop 才拿到正确行为，
   * 只有极少数「要继承调用方层叠上下文」的场景才显式写 `:teleported="false"`。
   * `default: undefined` 与 modelValue 同一条理由（Boolean 缺省无 default 会被 cast 成 false，
   * 于是 `props.teleported !== false` 为 false ⇒ **默认反而不 teleport**，方向正好写反）。
   * 变异实测：删掉这个 `default` 后 `Modal.teleport.test.ts` 第一条转红。
   *
   * ⚠️ 交 Task 9/10（本轮不实现，只把事实交接清楚）：**挂载期内翻转 `teleported` 会搬动 DOM 节点**。
   * Vue 的 `disabled` 翻转走 `moveTeleport`（本机 vue 3.5.43 的 runtime-core.cjs.js:1193-1216，
   * 由 Teleport 的 patch 分支 :1111 调用），它对每个子 vnode 调渲染器的 `move`——**同一批 DOM 节点被
   * insert 进新容器，不是卸载重建** ⇒ 挂在遮罩上的监听器随节点搬走、不丢；而元素被搬动会**失焦**
   * （浏览器把 `document.activeElement` 落回 body；jsdom 不建模，本轮未实测）。焦点方案别假设"翻一下
   * `teleported` 焦点还在原处"。
   *
   * ⚠️ 同一次交接的第二半：根是 Teleport 时组件的 `$el` **不是遮罩**（开/关两态都是 Teleport 的
   * `teleport start` 锚点注释，nodeType 8），实测与含义见 LEDGER 的 `P0b-MODAL-02` 行。
   */
  teleported: { type: Boolean, default: undefined },
  /**
   * 尺寸档由「attribute 契约」改为**声明式 props**（Task 8）。两件事必须同一 commit 落地：
   * ① 模板读取处一起改成读 props（见下面的 `sizeClass`）——只改声明不改读取，`$attrs` 里就没有
   *    `wide` 了，13 个调用点会**静默塌回默认 640px**，不报错不红，只是宽度全错。
   *    变异实测：把模板退回 `$attrs.wide` 而保留声明，`Modal.compat.test.ts` 的三条尺寸档**红**；
   *    同一次实测里 `wrapper.props('wide')` 却是 `true`（渲染出的 class 只有 `modal`）——
   *    所以那三条**只许断言渲染出的 class 列表**，改断 prop 值就等于自己把网拆了。
   * ② `sizeClass` 同时接受 `''` 与 `true`，这一条才是承重的：`<Modal wide>` 编译成 `wide=""`，
   *    而 `[Boolean, String]` 下 Vue 是否把它 cast 成 `true` 取决于类型数组顺序——实测把顺序翻成
   *    `[String, Boolean]` 后三条尺寸档**仍全绿**（说明 `''` 被原样保留、走了 `=== ''` 那一支）。
   *    ⇒ 保持 `[Boolean, String]` 是与 `modelValue` 一致的写法，但**别把它当安全垫**；
   *    真正的垫子是 computed 里那两支并列的判断。
   */
  wide: { type: [Boolean, String], default: undefined },
  xwide: { type: [Boolean, String], default: undefined },
  narrow: { type: [Boolean, String], default: undefined },
})
// 双通道：`close` 是 13 个存量调用点唯一在听的，`update:modelValue` 是 v-model 的另一条腿。
const emit = defineEmits(['close', 'update:modelValue'])

// 缺省视为 true——false 才是「关」，undefined 不是。
const open = computed(() => props.modelValue !== false)
// 同一口径：`teleported` 只有显式 false 才原位渲染。
const toBody = computed(() => props.teleported !== false)

// 原先这是模板里内联的 `$attrs` 三档判断；收成 computed 与 props 声明同批落地（见上 ①）。
const sizeClass = computed(() => ({
  wide: props.wide === '' || props.wide === true,
  xwide: props.xwide === '' || props.xwide === true,
  narrow: props.narrow === '' || props.narrow === true,
}))

/**
 * 焦点接入（Task 9）。走台账 P0b-MODAL-04 行定档的 3-B 分支，实测读数全在那一行里：
 * 模板根是 <Teleport>，组件的 $el 开、关两态都是 teleport start 锚点注释（nodeType 8），
 * 注释上没有 querySelector，而 useDialogFocus.ts:50-51 读的正是 dialogRef.value.$el ——
 * 那条路**静默 return**（不报错、不红，焦点永远进不去），所以本组件自持面板侧的 rootRef。
 *
 * ⚠️ 本任务不改 useDialogFocus.ts（控制器裁定 R-T9-1）：它的 watch(visible) 不带 immediate，
 * 那就是 63 个存量 el-dialog 接线的时序契约。后果是「挂载即打开」这条路永远等不到边沿，
 * 而 13 个存量 <Modal> 调用点**全是**这个形状（外层 v-if、挂载时已开）。所以适配器把可见性
 * 折成 open && mounted —— onMounted 里翻 mounted 这件事本身就构成一次 false→true 边沿，
 * 两条开启路径（v-model 翻转 / v-if 挂载）都接得上。Drawer.vue 的焦点接入（Task 11）同一写法。
 * 不许用 setTimeout 绕：那会把原语的 nextTick 加 rAF 双等待改掉，波及全部 63 个接线点。
 */
const rootRef = ref(null)
const mounted = ref(false)
useDialogFocus(
  computed(() => ({ $el: rootRef.value ?? undefined })),
  computed(() => open.value && mounted.value),
)

/**
 * ⚠️ 3-B 的「对外等价面」这半**没有落地**（控制器裁定 R-T9-2 要求 defineExpose 一个同形的 $el，
 * 本轮实测否证了它可与既有钉共存这一前提，故在此处留注释而非留代码；台账 P0b-MODAL-04 行有全文）：
 *
 * 1) 机制本身成立（探针已入库，见 Modal.focus.test.ts 的 P-c 两条）：expose 的对象被**原样**存进
 *    instance.exposed，dev 只 warn「重复调用」与「不是纯对象」、没有任何 $ 前缀守卫；解包发生在
 *    getComponentPublicInstance 里的 new Proxy(proxyRefs(markRaw(instance.exposed)), ...)，其 get 在
 *    key in target 成立时直接返回 target[key]（**没有 $ 前缀守卫**，grep "Exposing a property" 与
 *    "key[0] === '$'" 在本机 vue 3.5.43 的 runtime-core.cjs.js
 *    expose 路径上 0 命中），只有不成立才回落到 publicPropertiesMap —— 内置 $el 被同名键顶掉。
 * 2) 但顶掉的不只是调用方模板 ref：VTU 的 createVMProxy（vue-test-utils.cjs.js:7617-7646）把
 *    vm.$.exposed 排在 setupState 与 vm 之前，而 VueWrapper.element 就是 this.vm.$el（:7788-7790）
 *    ⇒ wrapper.element 一起跟着变。实跑后果：npx vitest run src/components/ui/ 从 100 绿变
 *    **18 failed**，因为关闭态 rootRef 为 null、内置 $el 拿不到 ⇒ wrapper.element 是 undefined ⇒
 *    helpers/trackMount.ts:48 那句 w.element.isConnected 抛 TypeError，整个 Modal 测试族的
 *    afterEach 崩掉、僵尸实例与 document keydown 监听互相污染（Modal.vmodel 11 条全红、
 *    Modal.compat 5 红、Modal.teleport 1 红）。
 * 3) 让关闭态回落内置锚点（defineExpose 的 $el 写成 rootRef.value ?? inst.vnode.el，inst 取
 *    getCurrentInstance()）能把伤害收到 1 条：Modal.vmodel.test.ts 的「modelValue=true 时渲染」
 *    仍报 expected 1 to be 8 —— 那条用例钉的就是「开启态的 $el 仍是 teleport start 注释」，
 *    与 defineExpose($el) 在定义上互斥，不是可以两边都要的工程问题。
 * 4) 那两个文件（Modal.vmodel.test.ts / helpers/trackMount.ts）都在本任务的改动许可之外，而
 *    本轮硬门禁是「既有测试一条不许红」⇒ 只接内部焦点、不写 defineExpose。Phase 4 的迁移规矩
 *    因此是：**迁到 Modal 时把 ref 与 useDialogFocus 那两行删掉**（Modal 内部已接），不要指望
 *    dialogRef.$el；存量 13 个调用点一个都没这么写（两条 ref= 反查命令退出码均为 1，命令在台账里）。
 *    要恢复这半个方案，需控制器先授权改那两处钉与夹具，再连同 P-b 那条用例一起重签。
 */

// keydown 监听整个挂载期常驻 document：模板根的 `v-if` 只摘子树、不销毁实例，
// 所以「关闭态必须哑火」的门只能放在 close() 里（评审 I-1）。
onMounted(() => {
  document.addEventListener('keydown', onEsc)
})
// 挂载这件事本身要成为一次可见性边沿（见上面焦点接入段的 R-T9-1 说明）。
// 与 keydown 那个 hook 分成两次注册：Vue 允许同名 hook 并存，各自职责单一，
// Task 10 接滚动锁时不必动焦点这段。
onMounted(() => {
  mounted.value = true
})
onBeforeUnmount(() => document.removeEventListener('keydown', onEsc))
function onEsc(e) {
  if (e.key === 'Escape') close()
}
// 门卫是派生值 open，不是 `modelValue === true`（Task 7 修复轮，评审 I-1/M-1 的裁定）：
// - 不 gate open：一次 Esc 会给树上所有「曾开过、已关闭、仍存活」的实例补发幽灵 close，
//   其中 5 个存量站点 `@close="emit('close')"` 会把它原样递给各自的父组件；
// - 只认 `=== true`：v-model 绑 ref<boolean|undefined>()（值为 undefined、open 为 true）时
//   遮罩渲染得出却关不掉。缺省调用点因此多收一个没人监听的 update:modelValue——Vue 里无害，
//   且与 brief「两 emit 同时触发」的 Interfaces 原文一致。
// 两条各由 Modal.vmodel.test.ts 的红灯钉死（关闭态 Esc 哑火 / undefined 可关闭）。
function close() {
  if (!open.value) return
  emit('close')
  emit('update:modelValue', false)
}
function onOverlay(e) {
  if (e.target === e.currentTarget) close()
}
</script>
<template>
  <Teleport to="body" :disabled="!toBody">
    <div v-if="open" class="overlay" v-bind="$attrs" @mousedown.self="onOverlay">
      <div ref="rootRef" class="modal" :class="sizeClass" @click.stop>
        <div class="modal-head">
          <div>
            <div class="modal-title"><slot name="title" /></div>
            <div v-if="$slots.sub" class="modal-sub"><slot name="sub" /></div>
          </div>
          <div style="flex: 1"></div>
          <button class="t-icon-btn" @click="close">
            <svg
              viewBox="0 0 24 24"
              width="17"
              height="17"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="modal-body"><slot /></div>
        <div v-if="$slots.footer" class="modal-foot"><slot name="footer" /></div>
      </div>
    </div>
  </Teleport>
</template>
<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(20, 26, 38, 0.45);
  backdrop-filter: blur(3px);
  display: grid;
  place-items: center;
  z-index: var(--z-modal);
  padding: 24px;
}
.modal {
  background: var(--surface);
  border-radius: var(--r-l1);
  box-shadow: var(--sh-modal);
  width: 640px;
  max-width: 100%;
  max-height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal.wide {
  width: 880px;
}
.modal.xwide {
  width: 1060px;
}
.modal.narrow {
  width: 460px;
}
.modal-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--line);
  flex-shrink: 0;
}
.modal-title {
  font-size: 15.5px;
  font-weight: 600;
}
.modal-sub {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: 1px;
}
.modal-body {
  padding: 20px 22px;
  overflow-y: auto;
  flex: 1;
  background: var(--surface-2);
}
.modal-foot {
  padding: 13px 22px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
  background: var(--surface);
}
.t-icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  display: grid;
  place-items: center;
  cursor: pointer;
  color: var(--ink-3);
  transition: all 0.15s;
  flex-shrink: 0;
}
.t-icon-btn:hover {
  background: var(--surface-3);
  color: var(--ink);
}
</style>
