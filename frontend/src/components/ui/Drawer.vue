<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'

/**
 * 抽屉原语 —— 与 Modal 同通道（Task 11）
 * ======================================
 * Phase 4 要把 4 处裸 `<el-drawer>`（EgEventsDrawer / DscDetailDrawer / TaskDrawer /
 * CdsDetailDrawer）收进来，而它们恰是全仓弹层里唯一没有任何焦点管理的一组。
 * 所以这里的通道命名与语义**逐字对齐 Modal**（modelValue / teleported / closeOnClickModal /
 * ariaLabel / ariaLabelledby + close / update:modelValue / closed），Phase 4 只需一套迁移规则。
 *
 * 三条对外可见的新行为（相对改动前）：
 * 1. 可见性门：`modelValue === false` 时整个抽屉不渲染。缺省（不传）仍渲染，
 *    所以 `RagDocs.vue:90` 的 `v-if` 式调用点行为不变；而 `NotificationManagement.vue:47`
 *    经 `NmPublishDrawer` 传的 `:model-value="modelValue"`（初值 false）此前**只是落到根元素上
 *    的一个属性**、抽屉恒显示，现在才真的生效。
 * 2. Esc 关抽屉：`el-drawer` 有这个通道，Drawer 此前没有；监听只在打开期间挂在 document 上，
 *    关闭态按 Esc 不发幽灵 `close`。
 * 3. 滚动锁与焦点：进出抽屉锁 body 滚动、把焦点送进面板、关闭时还给打开前的元素并 emit `closed`。
 *
 * `wide` 保持 **attribute 契约**（不声明成 prop）：调用点写 `wide` 裸属性，模板从 `$attrs` 读
 * （Task 6 的 R-T6-3：把它提升成 prop 而不同步改模板读取，类会静默消失、宽度全错但不报错）。
 */
defineOptions({ inheritAttrs: false })

const props = defineProps({
  // 三态：undefined=不传（恒显示，v-if 式调用点）、true=开、false=关。
  // 这里必须显式写 default: undefined —— Boolean prop 缺省且无 default 时 Vue 会把
  // 「没传」强制成 false（runtime-core resolvePropValue 的 isAbsent && !hasDefault 分支），
  // 那会让 RagDocs.vue:90 这种 v-if + 无 model-value 的调用点永远打不开。
  modelValue: { type: Boolean, default: undefined },
  placement: { type: String, default: undefined },
  teleported: { type: Boolean, default: undefined },
  closeOnClickModal: { type: Boolean, default: undefined },
  ariaLabel: { type: String, default: undefined },
  ariaLabelledby: { type: String, default: undefined },
})
const emit = defineEmits(['close', 'update:modelValue', 'closed'])

const open = computed(() => props.modelValue !== false)
const rootRef = ref(null)
/** 同页多个抽屉时 id 不能撞车，用实例 uid 生成（与 Modal 的 titleId 同一口径） */
const titleId = `drawer-title-${getCurrentInstance()?.uid ?? 0}`

function close() {
  emit('close')
  emit('update:modelValue', false)
}

function onEsc(e) {
  if (e.key === 'Escape') close()
}

function onOverlay(e) {
  // 开关必须在 .self/target 判定之前生效：否则按下遮罩已经关掉再补一次开关判定
  if (props.closeOnClickModal === false) return
  if (e.target === e.currentTarget) close()
}

/**
 * 引用计数滚动锁。状态键与 Modal（Task 10 的 useOverlayScrollLock）共用
 * `document.body.dataset.overlayCount`，所以两个原语已经是同一把锁：
 * 抽屉叠在弹窗上时，最后一个关闭才还原滚动。
 *
 * 该 composable 尚未落地（Task 10 未派发），先在本文件内实现同键同语义的版本，
 * 落地后把这段换成 `useOverlayScrollLock(open)` 即可，无需改任何调用点。
 * 与 Task 10 草案的两处差别都是故意的，见 Drawer.test.ts 的对应用例：
 * - 用实例内 `locked` 标志保幂等，避免「关闭态挂载」被当成开→关过渡而白扣 1；
 * - 卸载时归还计数，覆盖 v-if 式调用点（RagDocs.vue:90）在打开状态下直接卸载的形态。
 */
let locked = false
function setScrollLock(on) {
  if (on === locked) return
  locked = on
  // 计数读回来不是数字时按 0 重开：否则 NaN 会让 overflow 恒被置回 ''，
  // 一个页面里只要有一次脏写就再也锁不住滚动。
  const raw = Number(document.body.dataset.overlayCount)
  const base = Number.isFinite(raw) ? raw : 0
  const next = Math.max(0, base + (on ? 1 : -1))
  document.body.dataset.overlayCount = String(next)
  document.body.style.overflow = next > 0 ? 'hidden' : ''
}

/** Esc 监听只在打开期间存在：常挂版会让 4 个抽屉叠加时一次 Esc 全关、且关闭态留幽灵回调 */
let escBound = false
function bindEsc(on) {
  if (on === escBound) return
  escBound = on
  if (on) document.addEventListener('keydown', onEsc)
  else document.removeEventListener('keydown', onEsc)
}

let opener = null
watch(
  open,
  (on, off) => {
    setScrollLock(on)
    bindEsc(on)
    if (on && !off) opener = document.activeElement
    if (!on && off) {
      emit('closed')
      opener?.focus?.()
      opener = null
    }
  },
  { immediate: true },
)

/**
 * 焦点接入。useDialogFocus 的 watch 不带 immediate，只认「false→true 的边沿」；
 * 而 v-if 式调用点（含要迁移的 4 个 el-drawer）是「挂载即打开」，那条路永远等不到边沿。
 * 故把可见性折成 open && 已挂载：挂载本身构成一次 false→true 边沿，两条开启路径都接得上。
 */
const mounted = ref(false)
useDialogFocus(
  computed(() => ({ $el: rootRef.value ?? undefined })),
  computed(() => open.value && mounted.value),
)

onMounted(() => {
  mounted.value = true
})

onBeforeUnmount(() => {
  setScrollLock(false)
  bindEsc(false)
})
</script>
<template>
  <Teleport to="body" :disabled="teleported === false">
    <div v-if="open" v-bind="$attrs" class="drawer-overlay" @mousedown.self="onOverlay">
      <div
        ref="rootRef"
        class="drawer"
        :class="[placement ?? 'rtl', { wide: $attrs.wide === '' || $attrs.wide === true }]"
        role="dialog"
        aria-modal="true"
        :aria-label="ariaLabel || null"
        :aria-labelledby="ariaLabelledby ?? ($slots.title ? titleId : null)"
      >
        <div class="drawer-head">
          <div>
            <div :id="titleId" class="modal-title">
              <slot name="title" />
            </div>
            <div v-if="$slots.sub" class="modal-sub">
              <slot name="sub" />
            </div>
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
        <div class="drawer-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="drawer-foot">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(20, 26, 38, 0.38);
  z-index: var(--z-drawer);
}
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 480px;
  max-width: 94vw;
  background: var(--surface);
  box-shadow: var(--sh-drawer);
  z-index: calc(var(--z-drawer) + 1);
  display: flex;
  flex-direction: column;
}
.drawer.wide {
  width: 620px;
}
/* 贴边圆角：方向类名与 index.scss:342-347 给 .el-drawer 的那套同名同语义，
   Phase 4 把 el-drawer 换进来时不会丢掉圆角。半径取 ui 原语自己的 --r-l1（Modal.vue:68 同档）。 */
.drawer.rtl {
  border-radius: var(--r-l1) 0 0 var(--r-l1);
}
.drawer.ltr {
  left: 0;
  right: auto;
  border-radius: 0 var(--r-l1) var(--r-l1) 0;
}
.drawer-head {
  padding: 16px 22px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px;
  background: var(--surface-2);
}
.drawer-foot {
  padding: 13px 22px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  background: var(--surface);
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
}
.t-icon-btn:hover {
  background: var(--surface-3);
  color: var(--ink);
}
</style>
