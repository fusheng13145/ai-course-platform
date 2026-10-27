<template>
  <div ref="menu" class="pop-menu" :style="pos" role="menu" aria-orientation="vertical">
    <div
      v-for="item in items"
      :key="item.label"
      ref="itemEls"
      class="menu-item"
      :class="{ danger: !!item.danger }"
      role="menuitem"
      tabindex="0"
      @click="pick(item)"
      @keydown.enter.prevent="pick(item)"
      @keydown.space.prevent="pick(item)"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.home.prevent="focusAt(0)"
      @keydown.end.prevent="focusAt(items.length - 1)"
    >
      <!-- 图标取自仓内常量表（layouts/icons.ts），是受控输入；标签一律走文本插值，
           不再动态建 DOM 把整行拼成 HTML 片段 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span class="mi-ico" v-html="iconHtml(item.icon, 14)"></span>
      <span>{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { iconHtml } from '@/layouts/icons'
import type { UserMenuItem } from '@/components/userMenuItems'

/**
 * 壳层用户菜单
 * <p>侧栏底部用户区与顶栏头像共用这一份。触发物按元素传进来：弹层是 position:fixed，
 * 拿一次矩形快照就会在触发物滚走后留在原地，所以矩形现量现算，滚动与改窗都重算。</p>
 * <p>外观类（.pop-menu/.menu-item）取 design-system.css 的全局定义，这里不再抄一份；
 * 沿用只管外观不管层级——层级由下面那一条 scoped 例外给出。</p>
 */
const props = defineProps<{
  items: UserMenuItem[]
  triggerEl: HTMLElement | null
}>()
const emit = defineEmits<{ closed: [] }>()

const ITEM_H = 34
const GAP = 8
const EDGE = 8
const MENU_W = 200

type Rect = { top: number; left: number; bottom: number }

function rectOf(el: HTMLElement | null): Rect | null {
  if (!el) return null
  const r = el.getBoundingClientRect()
  return { top: r.top, left: r.left, bottom: r.bottom }
}

/** 跟随触发物：在下半屏就向上开。改前是恒 top:52px ⇒ 点左下角头像、菜单出现在右上角 */
function posOf(r: Rect | null) {
  if (!r) return { top: `${EDGE}px`, left: `${EDGE}px` }
  const openUp = r.top > window.innerHeight / 2
  const top = openUp ? Math.max(EDGE, r.top - GAP - props.items.length * ITEM_H) : r.bottom + GAP
  const left = Math.min(Math.max(EDGE, r.left), window.innerWidth - MENU_W)
  return { top: `${top}px`, left: `${left}px` }
}

const pos = ref(posOf(rectOf(props.triggerEl)))
const menu = ref<HTMLElement | null>(null)
const itemEls = ref<HTMLElement[]>([])

function focusAt(index: number) {
  const els = itemEls.value
  if (!els.length) return
  const n = els.length
  // preventScroll：默认 focus() 会把最近的滚动容器滚到可视区，壳层里这一下会挪动
  // 侧栏位置，进而把引导聚光的几何带偏
  els[((index % n) + n) % n]?.focus({ preventScroll: true })
}

function move(step: number) {
  const cur = itemEls.value.indexOf(document.activeElement as HTMLElement)
  focusAt((cur < 0 ? 0 : cur) + step)
}

function pick(item: UserMenuItem) {
  emit('closed')
  item.action?.()
}

// 监听只随挂载存在、卸载即摘：否则实例没了之后任意一次点击还会 emit 一个没人接的 closed
function onDocMousedown(e: MouseEvent) {
  if (!menu.value?.contains(e.target as Node)) emit('closed')
}

function onDocKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  // 菜单是键盘打开的，关掉后焦点必须回到触发物，否则掉回 body、用户得从头 Tab 一遍
  props.triggerEl?.focus({ preventScroll: true })
  emit('closed')
}

function recompute() {
  pos.value = posOf(rectOf(props.triggerEl))
}

onMounted(() => {
  document.addEventListener('mousedown', onDocMousedown)
  document.addEventListener('keydown', onDocKeydown)
  // 滚动可能发生在任何一层滚动容器里，捕获阶段才收得到
  window.addEventListener('scroll', recompute, true)
  window.addEventListener('resize', recompute)
  focusAt(0)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMousedown)
  document.removeEventListener('keydown', onDocKeydown)
  window.removeEventListener('scroll', recompute, true)
  window.removeEventListener('resize', recompute)
})
</script>

<style scoped>
/* 层级例外，不是第二份外观抄本：全局 .pop-menu 的 z-index 是 --z-menu(130)，
   会被 toast(160) 与 EP 弹层(2000 起)压在下面——本菜单由壳层常驻渲染，落点正是那一档。
   这里只补这一条属性，外观规则继续沿用全局那份。 */
.pop-menu {
  z-index: var(--z-shell-menu);
}

/* 图标盒：SVG 由 iconHtml 按 14 出图，行内盒负责居中与占位（缺了它图标会撑不满行高、
   与文字基线错位），尺寸与旧实现逐字同值 */
.mi-ico {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
}
</style>
