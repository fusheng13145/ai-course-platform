<script setup>
import { computed, onMounted, onBeforeUnmount } from 'vue'

/**
 * 遮罩为什么必须 Teleport 到 body（Task 8，spec §2.6 的包含块陷阱）：
 * `.overlay` 是 `position: fixed; inset: 0`，而 fixed 会被**任何计算值非 `none`** 的
 * `transform` / `filter` / `backdrop-filter` / `will-change` 祖先重新包含。本仓实测两处今日就生效：
 * `views/student/components/SdCourseGrid.vue:111`（Modal 在模板根 `section.l1-card.animate-in.d2` 之内，
 * `.animate-in` 以 `both` 填充出永久 `translateY(0)`，而 `.l1-card` 还 `overflow: hidden` ⇒ 遮罩被裁进卡片）、
 * `views/teacher/tools/KnowledgeManagement.vue:96`（`<ExcelImportDialog>` 的根即 `<Modal wide>`，挂在
 * 页面根 `div.fade-in` 下，`.fade-in` 以 `forwards` 填充同一条 `translateY(0)`）。
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
 * 配套 `inheritAttrs: false`：手工绑定后不再让运行时二次套用，避免同一个 class 落两遍。
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

// keydown 监听整个挂载期常驻 document：模板根的 `v-if` 只摘子树、不销毁实例，
// 所以「关闭态必须哑火」的门只能放在 close() 里（评审 I-1）。
onMounted(() => {
  document.addEventListener('keydown', onEsc)
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
      <div class="modal" :class="sizeClass" @click.stop>
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
