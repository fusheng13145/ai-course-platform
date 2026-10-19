<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, useAttrs, watch } from 'vue'
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
 *    的一个属性**、抽屉恒显示，现在才真的生效。两个调用点各有一条行为钉（I-4）：
 *    `NmPublishDrawer.drawerGate.test.ts` / `RagDocs.drawerGate.test.ts`。
 * 2. Esc 关抽屉：`el-drawer` 有这个通道，Drawer 此前没有；监听只在打开期间挂在 document 上，
 *    关闭态按 Esc 不发幽灵 `close`。**同页多个抽屉叠着开时一次 Esc 会把它们全部关掉**
 *    （没有弹层栈，见 :145 那条注释；「只关最上面一层」已记为 Phase 4 准入门待办）。
 * 3. 滚动锁与焦点：进出抽屉锁 body 滚动、把焦点送进面板、关闭时还给打开前的元素并 emit `closed`。
 *
 * `aria-modal` **不在能力清单里**（Task 11 评审 I-1 撤回）：没有 focus trap 时声明模态是假承诺，
 * 详见模板里 role 那一行的注释；补回的前提是 Phase 0b 的共享 focus trap 落地。
 *
 * `wide` 保持 **attribute 契约**（不声明成 prop）：调用点写 `wide` 裸属性，模板从 `useAttrs()`
 * 读（就是 `$attrs` 那份），并且**只**给面板用——`overlayAttrs` 已把它从遮罩的透传里剔掉。
 * （Task 6 的 R-T6-3：把它提升成 prop 而不同步改模板读取，类会静默消失、宽度全错但不报错。）
 */
defineOptions({ inheritAttrs: false })

const props = defineProps({
  // 三态：undefined=不传（恒显示，v-if 式调用点）、true=开、false=关。
  // 这里必须显式写 default: undefined —— Boolean prop 缺省且无 default 时 Vue 会把「没传」
  // 强制成 false，那会让 RagDocs.vue:90 这种 v-if + 无 model-value 的调用点永远打不开。
  // 执行的是哪份构建我量过（不是推的）：vitest 3.2.7 + 本仓 vitest.config.ts 下，
  // @vue/runtime-core 落到 **runtime-core.cjs.js**（探针打到的栈帧：callWithErrorHandling →
  // 同文件 :200、emit 本体 → 同文件 :4483/:4545、setupStatefulComponent → 同文件 :8257），
  // 该转换分支在同文件 :5117-5118（`if (opt[0]) { if (isAbsent && !hasDefault) value = false }`）。
  // 同一段逻辑在 runtime-core.esm-bundler.js:5141-5142 也有，但那份构建不在测试里执行，
  // 所以本文件的库断言一律引真正跑起来的那份（评审 Minor-1）。
  modelValue: { type: Boolean, default: undefined },
  // 不给校验的话 `placement="right"` 这种拼错会静默产出一个「右侧 + 零圆角」的抽屉：
  // 类名找不到对应 CSS 规则、不报错也不红灯，和 wide 的静默失效是同一形态。
  // 校验器只在开发态 warn（执行路径 runtime-core.cjs.js:5270-5271，由 initProps :5047 /
  // updateProps :4939 调用），warn 完仍会把非法值传进来，所以下面 placementClass 还兜一层。
  placement: {
    type: String,
    default: undefined,
    validator: (v) => v === 'rtl' || v === 'ltr',
  },
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

/** 方向类名：只可能产出 rtl / ltr 两个值。拼错的 placement 已被校验器 warn，这里再退回 rtl。 */
const placementClass = computed(() => (props.placement === 'ltr' ? 'ltr' : 'rtl'))

/**
 * 遮罩根上真正要透传的属性。`wide` 是 Drawer 自己吃掉的修饰属性，不能跟着 `$attrs`
 * 落到 `.drawer-overlay`——那个类名下没有 `.wide` 规则，落上去只是一枚 `wide=""` 死属性
 * （评审 Minor-5；调用点 `NmPublishDrawer.vue:4` 就是这么写的）。
 */
const attrs = useAttrs()
const overlayAttrs = computed(() => {
  const rest = { ...attrs }
  delete rest.wide
  return rest
})
const isWide = computed(() => attrs.wide === '' || attrs.wide === true)

/**
 * 关闭契约 = Modal 的那一条，逐字照搬（Modal.vue 的 close()：`close` 恒发，
 * `update:modelValue` 只在调用点真的受控时发）。选它而不是自创一条的理由：
 * 本任务的章程就是「两原语一套通道，Phase 4 只写一套迁移规则」，
 * 而 `close`/`update:modelValue` 各自的受众在存量调用点里都真实存在
 * （`RagDocs.vue:90` 只听 `close`；`NmPublishDrawer.vue:5-6` 两条都听；
 * Phase 4 要收的 4 个 el-drawer 里 3 个用 `:model-value` + `@update:model-value`）。
 * 被砍掉的只有一种噪声：非受控调用点收到没人听的 `update:modelValue(false)`。
 * 残留：受控形态下两条腿同时发，经 `NmPublishDrawer.vue:5-6` 的双转发会让页面收到两次
 * 相同的 `update:modelValue(false)`——那是业务页的转发形状（不在本任务所有权内），
 * 状态层面幂等（`drawerOpen` 第二次写入不触发，已由该用例量出来），故保留契约不动。
 */
function close() {
  emit('close')
  if (props.modelValue === true) emit('update:modelValue', false)
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
 * 与 Task 10 草案的三处差别都是故意的，见 Drawer.test.ts 的对应用例：
 * - 用实例内 `locked` 标志保幂等，避免「关闭态挂载」被当成开→关过渡而白扣 1；
 * - 卸载时归还计数，覆盖 v-if 式调用点（RagDocs.vue:90）在打开状态下直接卸载的形态；
 * - 归零时**还原**上锁前记下的 `body.style.overflow`，不是无条件写 ''（评审 Minor-4）：
 *   页面自己写的非计数锁（`overflow: hidden/scroll/auto`）不该被抽屉的开关顺带抹掉。
 *   原值存在与计数同域的 `body.dataset.overlayOverflowPrev`，由真正完成 0→1 的那次上锁写入，
 *   所以多个抽屉/弹窗交错关闭时谁先抢到 0 都能拿到同一个原值。
 */
let locked = false
/** 上锁前的内联 overflow 存在这里（body dataset，与计数同域），归零时按它还原 */
const OVERFLOW_PREV_KEY = 'overlayOverflowPrev'
function setScrollLock(on) {
  if (on === locked) return
  locked = on
  // 计数读回来不是数字时按 0 重开：否则 NaN 会让 overflow 恒被置回 ''，
  // 一个页面里只要有一次脏写就再也锁不住滚动。
  const raw = Number(document.body.dataset.overlayCount)
  const base = Number.isFinite(raw) ? raw : 0
  const next = Math.max(0, base + (on ? 1 : -1))
  if (on && base === 0) document.body.dataset.overlayOverflowPrev = document.body.style.overflow
  document.body.dataset.overlayCount = String(next)
  if (next > 0) {
    document.body.style.overflow = 'hidden'
  } else if (OVERFLOW_PREV_KEY in document.body.dataset) {
    document.body.style.overflow = document.body.dataset[OVERFLOW_PREV_KEY]
    delete document.body.dataset[OVERFLOW_PREV_KEY]
  }
  // next 归 0 但没找到原值：说明这把锁不是我上的（计数被外部写过），
  // 此时**什么都不动**——宁可不还原，也不去猜别人该是什么值。
}

/**
 * Esc 监听只在打开期间挂载：这条只解决「关闭态发幽灵 close」，**不解决层叠**。
 * 每个「打开中的抽屉」各自往 document 挂一个 keydown，所以两个抽屉叠着开时
 * 一次 Esc 会把它们**一起**关掉（评审 I-3 认定的就是这件事，此前注释写反了）。
 * 「只关最上面一层」需要弹层栈：Modal 也还没有，本任务不建（建一次、两原语共用，归 Phase 0b/
 * Task 10），已作为准入门待办记进 task-11-report §Fix round 1。
 * 现有行为由 Drawer.test.ts「两个抽屉叠着开：一次 Esc 两个都关」钉住——谁改成 topmost-only，
 * 这条锁就红，逼他写清是有意的。
 */
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
    <!-- aria-modal 已撤回：没有 focus trap 时声明模态是假承诺（Tab 仍走得到背景）。等 Phase 0b 的共享 focus trap（Task 10 建一次、Modal 同用）到位再挂回。 -->
    <div v-if="open" v-bind="overlayAttrs" class="drawer-overlay" @mousedown.self="onOverlay">
      <div
        ref="rootRef"
        class="drawer"
        :class="[placementClass, { wide: isWide }]"
        role="dialog"
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
  /* 圆角必须配裁剪，否则 .drawer-foot{background:var(--surface)} / .drawer-body{background:var(--surface-2)}
     会用矩形背景盖在下圆角上——面板自身背景是被 border-radius 裁的，子节点不是，于是上圆角成真、
     下圆角被填成直角（评审 I-2）。同仓先例成对写：index.scss:335-338 的 .el-dialog 就是
     border-radius + overflow:hidden。 */
  overflow: hidden;
}
.drawer.wide {
  width: 620px;
}
/* 贴边圆角：方向类名与 index.scss:342-347 给 .el-drawer 的那套同名同语义，
   Phase 4 把 el-drawer 换进来时不会丢掉圆角。半径取 ui 原语自己的 --r-l1（theme.css:46 = 16px；
   Modal 的 .modal 用同一档，见 Modal.vue@999862c:68——不引工作树行号，那个文件正在被人改）。
   圆角真的生效依赖上面那条 overflow:hidden，两者是一对，删一条就等于删两条。 */
.drawer.rtl {
  border-radius: var(--r-l1) 0 0 var(--r-l1);
}
.drawer.ltr {
  left: 0;
  right: auto;
  border-radius: 0 var(--r-l1) var(--r-l1) 0;
  /* --sh-drawer（theme.css:56）的 x 偏移是 -18px，为**右侧**抽屉写的：
     直接沿用到 ltr，投影会跑到面板右边缘之外、贴着屏幕右侧的左边缘反而没有阴影。
     这里按同源值把 x 取反。theme.css 里缺 --sh-drawer-ltr，而该文件不在本任务所有权内，
     所以先就地取反并把「补 token」记入报告待办；数值与 theme.css:56 逐字同源。 */
  box-shadow: 18px 0 56px rgba(24, 32, 48, 0.18);
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
