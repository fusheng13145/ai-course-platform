<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, useAttrs, watch } from 'vue'
import { useDialogFocus } from '@/composables/useDialogFocus'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { useOverlayScrollLock } from '@/composables/useOverlayScrollLock'
import { overlayEscIsTop, overlayEscPush, overlayEscRemove } from '@/composables/overlayEscStack'

/**
 * 抽屉原语 —— 与 Modal 同通道（Task 11）
 * ======================================
 * Phase 4 已把 4 处裸 `<el-drawer>`（EgEventsDrawer / DscDetailDrawer / TaskDrawer /
 * CdsDetailDrawer）收进来（2026-10-27，P4-MIG-06，`<el-drawer` 全仓 0 命中），而它们恰是全仓弹层里
 * 唯一没有任何焦点管理的一组。
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
 *    （没有弹层栈，见下面 `bindEsc` 上方那段注释；「只关最上面一层」已记为 Phase 4 准入门待办）。
 * 3. 滚动锁与焦点：进出抽屉锁 body 滚动、把焦点送进面板、关闭时还给打开前的元素并 emit `closed`。
 *
 * 焦点陷阱与 `aria-modal`（Task 10B 补上最后一块）：`useFocusTrap(rootRef, open)` 与 Modal 共用
 * 同一份实现，陷阱在场才挂回 `aria-modal="true"`（Task 11 评审 R-1 撤下它的补回前提由此兑现）。
 * 实测已装的 EP 2.14.7：`el-dialog` 本来就带 ElFocusTrap（台账 `P0b-FOCUS-01` 有路径与读数），
 * 所以两原语与 `el-dialog`/`el-drawer` 是**对齐**，不是更严。
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
  // 任意宽度通道（P4-GATE-01，准入门 #1）与 Modal 同一条：数字按 px、字符串原样，
  // 优先级高于 wide 档。要迁移的 4 处 el-drawer 需要 460 / 560px、TaskDrawer 还是
  // JS 算出的 100%|620px——width prop 是它们的落点。
  width: { type: [String, Number], default: undefined },
  // 字符串标题通道（P4-GATE-01，准入门 #2 的迁移期规则），与 Modal 同语义：
  // 插槽优先（fallback 链），aria-labelledby 联动。
  title: { type: String, default: undefined },
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

/** 任意宽度（P4-GATE-01 #1）：width prop 优先于 wide 档；数字补 px、字符串原样。 */
const widthStyle = computed(() => {
  if (props.width === undefined || props.width === '') return null
  const w = typeof props.width === 'number' ? `${props.width}px` : props.width
  return { width: w }
})

/**
 * 关闭契约 = Modal 的那一条，逐字对齐（Task 10C 写回对称，R-B）。两边同一个形状：
 * 门卫取派生值 `open`（三态里 `undefined` 与 `true` 都是「开」，只有 `false` 是「关」），
 * 门后 `close` 与 `update:modelValue(false)` **无条件成对发出**。
 *
 * 改动前这里是 `emit('close')` + `if (props.modelValue === true) emit('update:modelValue', false)`
 * ——`=== true` 那道闸让「不传 modelValue」的调用点关得掉 Modal、**关不掉 Drawer**
 * （`@close` 发得出，但 `update:modelValue` 不写回）。Phase 4 要批量写 `v-model="visible"`，
 * 两原语对同一个绑定表现不同 ⇒ 排查得按页做而不是按原语做一次。
 *
 * **方向是单向的**：收 Drawer 到 Modal，不反改 Modal——Modal 那条形状由 `6d2379d`
 * （"Modal 加 close() 的 open 门卫并按控制器裁定改双发契约"，Task 7 修复轮）定形并一直保留到今天，
 * `Modal.vmodel.test.ts` 的「不传 modelValue 时关闭同样双发——门卫是 open，不是 modelValue===true」
 * 就是它的红面。
 * ⚠️ 顺带记一处**题面引用被自己的命令否证**（我核对过才这么写）：`37b9414`（Task 7 首棒）的
 * `close()` 写的恰恰是本棒刚从 Drawer 删掉的那一型——
 * `emit('close')` 加 `if (props.modelValue === true) emit(...)`，所以 `git show 37b9414` 不是
 * "Modal 现状"的证据，而是"漂移起点"的证据。根因因此很清楚：Drawer 当年（Task 11）**逐字照搬的是
 * 37b9414 那版 Modal**，其后 `6d2379d` 把 Modal 抬到 open 闸、Drawer 没跟上，
 * 而它的注释一直自称"逐字照搬 Modal"——注释对着上游的新形状说话，代码还停在旧形状。
 * 本棒收的就是这笔差。
 *
 * 代价与 Modal 侧同判：非受控调用点（`RagDocs.vue` 的 `v-if` + `@close` 那一型，只听 `close`）
 * 多收一个没人监听的 `update:modelValue(false)`——Vue 里无害。受控调用点上两条腿成对发出，
 * 所以业务页只该转发一条腿（v-model 语义下的 `update:modelValue`）；两条腿都往父级转发，
 * 一次关闭就会发两次写回。
 *
 * ⚠️ 那道 `open` 闸在 Drawer 这边**今天没有红面**（不是我有遗漏，是三条入口都在源头挡住了）：
 * 关闭按钮与遮罩都在 `v-if="open"` 之内，Esc 由下面的 `bindEsc(on)` 只在打开期间挂。实测删掉这一行
 * 跑 `npx vitest run src/components/ui/` 是 132 条一条不红；保留它只为与 Modal 逐字同形——
 * 同样那行在 Modal 里是**承重的**（Modal 的 keydown 常驻整个挂载期、`v-if` 不销毁实例，
 * 关闭态 Esc 哑火那条就是它的红面）。谁把 Drawer 的 Esc 改成常驻挂载，这条闸立刻变成承重，
 * 届时应同步补一条「关闭态按 Esc 什么都不发」的对称红面（现在那条钉的是"监听没挂"，不是这道闸）。
 */
function close() {
  if (!open.value) return
  // ⚠️ 不在这里出栈（P4-GATE-01 定案，与 Modal.close 同一条）：出栈只由
  // watch(open) 下降沿与卸载驱动——拒绝关闭（父级不落值）后再按 Esc 重试同实例，
  // 对齐 el-dialog；多实例让位依赖父级写回（v-model 迁移形状）。
  emit('close')
  emit('update:modelValue', false)
}

function onEsc(e) {
  // 「Esc 只关最上层」（P4-GATE-01，准入门 #6）：共享栈见 overlayEscStack.ts。
  // 旧全局广播（一次 Esc 全关）已被本条替换，Drawer.test.ts 的对应断言
  // 按 spec §11.3 #6 的拆锁授权同步重写。
  if (e.key !== 'Escape' || !overlayEscIsTop(close)) return
  close()
}

function onOverlay(e) {
  // 开关必须在 .self/target 判定之前生效：否则按下遮罩已经关掉再补一次开关判定
  if (props.closeOnClickModal === false) return
  if (e.target === e.currentTarget) close()
}

/**
 * 引用计数滚动锁：状态与实现在 `@/composables/useOverlayScrollLock`，Modal 与本原语
 * 调同一个函数、读写同一个 `document.body.dataset.overlayCount`，所以两个原语是**一把锁**：
 * 抽屉叠在弹窗上时最后一个关闭才还原滚动。
 *
 * 抽取（Task 10A）时逐条保住的语义，连同"为什么这么选"都在那个文件里，本文件不再复述实现细节；
 * 本文件的可证伪面留在 Drawer.test.ts 的「滚动锁与焦点」一节：
 * - 归零时**还原上锁前记下的 `body.style.overflow`**（`body.dataset.overlayOverflowPrev`），
 *   不是无条件写 ''（评审 Minor-4）；
 * - 计数归 0 而 prev 键不存在时**什么都不动**（这把锁不是我上的，宁可不还原也不猜别人的值）；
 * - 关闭态挂载不扣数、开着被卸载归还（v-if 式调用点 RagDocs.vue 正是该形态，
 *   归还时机由 composable 的 onScopeDispose 提供，见下面 onBeforeUnmount 的注释）；
 * - 脏值消毒口径是「只接受正整数」，与抽取前的 `Number.isFinite` 不同——差别与理由见
 *   composable 的 `count()` 注释与它的「非整数脏值按 0 重开」用例。
 */
useOverlayScrollLock(open)

/**
 * 焦点陷阱接线（Task 10B）。与 Modal.vue 同一行、同一个 composable：Tab 回绕、跳过禁用/不可见、
 * 只在开关边沿装卸、嵌套各管各层——语义与跑红配方都在 `composables/useFocusTrap.ts` 与其单测；
 * Drawer 侧的消费面由 Drawer.test.ts「陷阱接线」一条钉（摘掉这行它就红）。
 */
useFocusTrap(rootRef, open)

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
    bindEsc(on)
    if (on && !off) {
      opener = document.activeElement
      overlayEscPush(close)
    }
    if (!on && off) {
      overlayEscRemove(close)
    }
  },
  { immediate: true },
)

/**
 * `closed` + 焦点返还移到离开过渡结束（P4-GATE-01，准入门 #4）——
 * 语义与迁移规则与 Modal.onAfterLeave 同一条，详见那边的注释。
 * bindEsc 的关闭侧出栈仍在这里（边沿即时），Transition 的 after-leave
 * 只承载 closed 与焦点返还。
 */
function onAfterLeave() {
  emit('closed')
  opener?.focus?.()
  opener = null
}

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
  // 滚动锁不在这里归还：useOverlayScrollLock 的 onScopeDispose 负责，
  // 时机就在本钩子之后、子树卸载之前（本机 vue 3.5.43 的 runtime-core.cjs.js
  // unmountComponent 里 `scope.stop()` 是同步调用），所以既有那条
  // 「v-if 式调用点卸载即归还滚动锁」不 await 也读得到归还后的值。
  bindEsc(false)
  // 兜底出栈：v-if 式调用点直接销毁实例时没有可见性下降沿。
  overlayEscRemove(close)
})
</script>
<template>
  <Teleport to="body" :disabled="teleported === false">
    <Transition name="overlay-anim" @after-leave="onAfterLeave">
      <div v-if="open" v-bind="overlayAttrs" class="drawer-overlay" @mousedown.self="onOverlay">
        <div
          ref="rootRef"
          class="drawer"
          :class="[placementClass, { wide: isWide }]"
          :style="widthStyle"
          role="dialog"
          aria-modal="true"
          :aria-label="ariaLabel || null"
          :aria-labelledby="ariaLabelledby ?? ($slots.title || title ? titleId : null)"
        >
          <div class="drawer-head">
            <div>
              <div :id="titleId" class="modal-title">
                <slot name="title">{{ title }}</slot>
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
    </Transition>
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
  /* 左向抽屉读左向 token。--sh-drawer 的 x 偏移是 -18px，为**右侧**抽屉写的：直接沿用到 ltr，
     投影会跑到面板右边缘之外、贴着屏幕右侧的那条左边缘反而没有阴影。
     Task 10C 起 theme.css 两向各有档位（且明暗两套都给了值），所以这里读 token、不再手算反号
     ——此前是组件内写死一份与 theme.css 同源的字面量，两处各存一份、缺一处就漂移。
     浅色侧读数与手算版逐字相同（18px 0 56px rgba(24, 32, 48, 0.18)）⇒ 浅色像素不变；
     变化只在暗色：ltr 现在拿到暗色档位，不再继承浅色那枚蓝灰投影。 */
  box-shadow: var(--sh-drawer-ltr);
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
/* 进入/离开过渡（P4-GATE-01，准入门 #4）：与 Modal 同名同时长（遮罩淡入淡出，
   面板不动——抽屉的滑入动画属 Phase 6 微交互面，本批不掺）。closed 挂在
   after-leave（见 onAfterLeave）；reduce 降级瞬时。 */
.overlay-anim-enter-active,
.overlay-anim-leave-active {
  transition: opacity 0.18s ease;
}
.overlay-anim-enter-from,
.overlay-anim-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .overlay-anim-enter-active,
  .overlay-anim-leave-active {
    transition: none;
  }
}
</style>
